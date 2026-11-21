package io.github.gciatto.ktconstraints.solve.spi

import io.github.gciatto.ktconstraints.core.BoolExpr
import io.github.gciatto.ktconstraints.core.BoolNot
import io.github.gciatto.ktconstraints.core.BoolVar
import io.github.gciatto.ktconstraints.core.IntDomain
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.core.RealVar
import io.github.gciatto.ktconstraints.core.Variable
import io.github.gciatto.ktconstraints.core.VariableFactory
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.CheckStatus
import io.github.gciatto.ktconstraints.solve.ConstraintStore
import io.github.gciatto.ktconstraints.solve.Entailment
import io.github.gciatto.ktconstraints.solve.EntailmentResult
import io.github.gciatto.ktconstraints.solve.Evidence
import io.github.gciatto.ktconstraints.solve.IncrementalConstraintSolver
import io.github.gciatto.ktconstraints.solve.IncrementalSession
import io.github.gciatto.ktconstraints.solve.Propagation
import io.github.gciatto.ktconstraints.solve.Residual
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.StoreMark
import io.github.gciatto.ktconstraints.solve.StoreStatus
import kotlin.jvm.JvmStatic
import kotlin.time.TimeSource

/**
 * [ConstraintStore] over any incremental session. Every post runs a complete satisfiability check, so statuses and
 * entailments are proofs, but nothing propagates: domains stay as declared. Marks map to session frames.
 */
class SatisfiabilityStore private constructor(
    private val solver: IncrementalConstraintSolver,
) : ConstraintStore {
    private val factory = VariableFactory.empty()
    private val session: IncrementalSession = solver.openSession(ConstraintScope.empty().build())
    private val frames = mutableListOf(StoreFrame(null, failedBefore = false))
    private val live = hashSetOf<Variable>()
    private var closed = false

    override val descriptor: SolverDescriptor get() = session.descriptor

    override var isFailed: Boolean = false
        private set

    override fun boolVar(name: String): BoolVar = declare(factory.boolVar(name))

    override fun intVar(
        name: String,
        domain: IntDomain,
    ): IntVar = declare(factory.intVar(name, domain))

    override fun realVar(name: String): RealVar = declare(factory.realVar(name))

    override suspend fun constrain(
        constraint: BoolExpr,
        timeoutMillis: Long?,
    ): Propagation {
        requireOwned(constraint.variables)
        if (isFailed) return Propagation(StoreStatus.FAILED)
        session.add(constraint)
        frames.last().constraints += constraint
        val status =
            when (session.check(timeoutMillis = timeoutMillis).status) {
                CheckStatus.SATISFIABLE -> StoreStatus.SATISFIABLE
                CheckStatus.UNSATISFIABLE -> StoreStatus.FAILED.also { isFailed = true }
                CheckStatus.UNKNOWN -> StoreStatus.UNKNOWN
            }
        return Propagation(status)
    }

    override fun mark(): StoreMark {
        requireOpen()
        val mark = SessionMark(this, session.push())
        frames += StoreFrame(mark, isFailed)
        return mark
    }

    override fun undoTo(mark: StoreMark) {
        requireOpen()
        require(mark is SessionMark && mark.owner === this) { "Mark belongs to another store" }
        val index = frames.indexOfFirst { it.mark === mark }
        require(index > 0) { "Mark is stale" }
        session.popTo(mark.checkpoint)
        isFailed = frames[index].failedBefore
        while (frames.size > index) live -= frames.removeAt(frames.lastIndex).variables.toSet()
    }

    /** A failed store vacuously entails everything. Otherwise two checks, sharing [timeoutMillis], decide. */
    override suspend fun entails(
        constraint: BoolExpr,
        timeoutMillis: Long?,
    ): EntailmentResult {
        requireOwned(constraint.variables)
        if (isFailed) return EntailmentResult(Entailment.ENTAILED, Evidence.SATISFIABILITY)
        val started = TimeSource.Monotonic.markNow()
        val negated = session.check(listOf(BoolNot(constraint)), timeoutMillis).status
        val value =
            when (negated) {
                CheckStatus.UNSATISFIABLE -> Entailment.ENTAILED
                CheckStatus.UNKNOWN -> Entailment.UNKNOWN
                CheckStatus.SATISFIABLE -> {
                    val remaining = timeoutMillis?.let { (it - started.elapsedNow().inWholeMilliseconds).coerceAtLeast(0) }
                    when (session.check(listOf(constraint), remaining).status) {
                        CheckStatus.UNSATISFIABLE -> Entailment.DISENTAILED
                        CheckStatus.SATISFIABLE -> Entailment.UNDETERMINED
                        CheckStatus.UNKNOWN -> Entailment.UNKNOWN
                    }
                }
            }
        return EntailmentResult(value, Evidence.SATISFIABILITY)
    }

    override fun domain(variable: IntVar): IntDomain {
        requireOwned(setOf(variable))
        return variable.domain
    }

    override fun project(variables: List<Variable>): Residual {
        requireOwned(variables.toSet())
        val requested = variables.toSet()
        val constraints = frames.flatMap { it.constraints }.filter { constraint -> constraint.variables.any { it in requested } }
        return Residual(variables.filterIsInstance<IntVar>().associateWith { it.domain }, constraints, exact = false)
    }

    override fun close() {
        if (closed) return
        closed = true
        session.close()
        solver.close()
    }

    private fun <V : Variable> declare(variable: V): V {
        requireOpen()
        session.declare(variable)
        frames.last().variables += variable
        live += variable
        return variable
    }

    private fun requireOwned(variables: Set<Variable>) {
        requireOpen()
        val foreign = variables.filterNot { it in live }
        require(foreign.isEmpty()) { "Variables not declared in this store: ${foreign.joinToString { it.name }}" }
    }

    private fun requireOpen() = check(!closed) { "Store is closed" }

    /** Factories. */
    companion object {
        /** Takes ownership of [solver]; closing the store closes it. */
        @JvmStatic
        fun of(solver: IncrementalConstraintSolver): SatisfiabilityStore = SatisfiabilityStore(solver)
    }
}
