package io.github.gciatto.ktconstraints.solve.choco

import io.github.gciatto.ktconstraints.core.Variable
import io.github.gciatto.ktconstraints.core.VariableFactory
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.bool.BoolNot
import io.github.gciatto.ktconstraints.core.bool.BoolVar
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.core.real.RealVar
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.Evidence
import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.choco.runtime.ChocoFrame
import io.github.gciatto.ktconstraints.solve.choco.runtime.ChocoWorlds
import io.github.gciatto.ktconstraints.solve.choco.translation.ChocoSupport
import io.github.gciatto.ktconstraints.solve.choco.translation.ChocoTranslation
import io.github.gciatto.ktconstraints.solve.spi.exception.UnsupportedModelException
import io.github.gciatto.ktconstraints.solve.spi.store.StoreGuard
import io.github.gciatto.ktconstraints.solve.spi.support.SupportReport
import io.github.gciatto.ktconstraints.solve.store.ConstraintStore
import io.github.gciatto.ktconstraints.solve.store.Entailment
import io.github.gciatto.ktconstraints.solve.store.EntailmentResult
import io.github.gciatto.ktconstraints.solve.store.Propagation
import io.github.gciatto.ktconstraints.solve.store.Residual
import io.github.gciatto.ktconstraints.solve.store.StoreMark
import io.github.gciatto.ktconstraints.solve.store.StoreStatus

/**
 * [ConstraintStore] with genuine Choco propagation: each post runs propagation to a fixpoint and reports the
 * variables whose domains shrank. Marks are Choco environment worlds; undoing one also unposts every Choco constraint
 * created since, because reifications are posted permanently. Entailment is decided by refuting the constraint or its
 * negation through propagation, so it may be [Entailment.UNKNOWN] but is never wrong. Propagation always reaches its
 * fixpoint, so timeouts are not applied. Single consumer; JVM only.
 */
@Suppress("TooManyFunctions") // One override per ConstraintStore operation, plus declaration and replay steps.
class ChocoStore : ConstraintStore {
    private val factory = VariableFactory.empty()
    private val translation = ChocoTranslation(ConstraintModel.empty())
    private val worlds = ChocoWorlds(translation.chocoModel)
    private val frames = mutableListOf(ChocoFrame(this, worlds.world, failedBefore = false))
    private val guard = StoreGuard()

    override val descriptor: SolverDescriptor = DESCRIPTOR

    override var isFailed: Boolean = false
        private set

    override fun boolVar(name: String): BoolVar = declare(factory.boolVar(name))

    override fun intVar(
        name: String,
        domain: IntDomain,
    ): IntVar = declare(factory.intVar(name, domain))

    override fun realVar(name: String): RealVar =
        throw UnsupportedModelException(SupportReport(listOf("${descriptor.id} has no exact reals: $name")))

    override suspend fun constrain(
        constraint: BoolExpr,
        timeoutMillis: Long?,
    ): Propagation {
        guard.requireOwned(constraint.variables)
        requireSupported(ChocoSupport.report(constraint))
        if (isFailed) return Propagation(StoreStatus.FAILED)
        val frame = frames.last()
        val before = frames.flatMap { it.variables }.associateWith { worlds.footprint(translation.natives.getValue(it)) }
        frame.natives += worlds.recording { translation.post(constraint) }
        frame.constraints += constraint
        isFailed = !worlds.propagate()
        val changed = before.filter { (variable, footprint) -> worlds.footprint(translation.natives.getValue(variable)) != footprint }
        val status =
            when {
                isFailed -> StoreStatus.FAILED
                frames.all { worlds.entailed(it.natives) } -> StoreStatus.SATISFIABLE
                else -> StoreStatus.CONSISTENT
            }
        return Propagation(status, if (isFailed) emptyList() else changed.keys.toList())
    }

    override fun mark(): StoreMark {
        guard.requireOpen()
        val frame = ChocoFrame(this, worlds.world, isFailed)
        worlds.push()
        frames += frame
        return frame
    }

    override fun undoTo(mark: StoreMark) {
        guard.requireOpen()
        require(mark is ChocoFrame && mark.owner === this) { "Mark belongs to another store" }
        val index = frames.indexOfFirst { it === mark }
        require(index > 0) { "Mark is stale" }
        val popped = frames.subList(index, frames.size).toList()
        worlds.popTo(mark.world, popped.flatMap { it.natives })
        popped.forEach { guard.release(it.variables) }
        repeat(popped.size) { frames.removeAt(frames.lastIndex) }
        isFailed = mark.failedBefore
    }

    override suspend fun entails(
        constraint: BoolExpr,
        timeoutMillis: Long?,
    ): EntailmentResult {
        guard.requireOwned(constraint.variables)
        requireSupported(ChocoSupport.report(constraint))
        val value =
            when {
                isFailed || worlds.refutes { translation.post(BoolNot(constraint)) } -> Entailment.ENTAILED
                worlds.refutes { translation.post(constraint) } -> Entailment.DISENTAILED
                else -> Entailment.UNKNOWN
            }
        return EntailmentResult(value, Evidence.PROPAGATION)
    }

    override fun domain(variable: IntVar): IntDomain {
        guard.requireOwned(setOf(variable))
        return worlds.domain(translation.natives.getValue(variable))
    }

    override fun project(variables: List<Variable>): Residual {
        guard.requireOwned(variables)
        val requested = variables.toSet()
        val constraints = frames.flatMap { it.constraints }.filter { constraint -> constraint.variables.any { it in requested } }
        return Residual(variables.filterIsInstance<IntVar>().associateWith { domain(it) }, constraints, exact = false)
    }

    /** Deletion filter: drops each posted constraint whose removal still lets propagation refute the rest. */
    override suspend fun explain(timeoutMillis: Long?): List<BoolExpr>? {
        guard.requireOpen()
        if (!isFailed) return null
        val variables = frames.flatMap { it.variables }
        var core = frames.flatMap { it.constraints }
        // shortcut: one full replay per posted constraint, O(n^2) posts; switch to QuickXplain if explanations get slow.
        for (candidate in core.toList()) {
            val without = core - candidate
            if (replay(variables, without).isFailed) core = without
        }
        return core
    }

    override fun fork(): ConstraintStore {
        guard.requireOpen()
        return replay(frames.flatMap { it.variables }, frames.flatMap { it.constraints }).also { it.isFailed = it.isFailed || isFailed }
    }

    override fun close() {
        guard.close()
    }

    /** A fresh store declaring [variables] and posting [constraints], propagated once. */
    private fun replay(
        variables: List<Variable>,
        constraints: List<BoolExpr>,
    ): ChocoStore =
        ChocoStore().also { child ->
            variables.forEach { child.declare(it) }
            constraints.forEach {
                child.frames.last().natives += child.worlds.recording { child.translation.post(it) }
                child.frames.last().constraints += it
            }
            child.isFailed = child.isFailed || !child.worlds.propagate()
        }

    private fun <V : Variable> declare(variable: V): V {
        guard.requireOpen()
        requireSupported(ChocoSupport.report(variable))
        frames.last().natives += worlds.recording { translation.declare(variable) }
        frames.last().variables += variable
        guard.adopt(variable)
        if (!isFailed) isFailed = !worlds.propagate()
        return variable
    }

    /** Route identity of Choco stores. */
    companion object {
        /** `choco/direct` as a propagating, backtracking store over Booleans and bounded integers. */
        @JvmField
        val DESCRIPTOR: SolverDescriptor =
            SolverDescriptor(
                "choco",
                "direct",
                setOf(
                    SolverCapability.INCREMENTAL_ASSERTIONS,
                    SolverCapability.BACKTRACKING,
                    SolverCapability.DOMAIN_PROPAGATION,
                    SolverCapability.ENTAILMENT,
                    SolverCapability.INTEGER_ARITHMETIC,
                    SolverCapability.GLOBAL_CONSTRAINTS,
                ),
            )
    }
}

private fun requireSupported(report: SupportReport) {
    if (!report.isSupported) throw UnsupportedModelException(report)
}
