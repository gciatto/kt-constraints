package io.github.gciatto.ktconstraints.solve.spi.store
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
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.incremental.CheckStatus
import io.github.gciatto.ktconstraints.solve.incremental.IncrementalConstraintSolver
import io.github.gciatto.ktconstraints.solve.incremental.IncrementalSession
import io.github.gciatto.ktconstraints.solve.spi.managed.SessionMark
import io.github.gciatto.ktconstraints.solve.spi.managed.SharedSolver
import io.github.gciatto.ktconstraints.solve.store.ConstraintStore
import io.github.gciatto.ktconstraints.solve.store.Entailment
import io.github.gciatto.ktconstraints.solve.store.EntailmentResult
import io.github.gciatto.ktconstraints.solve.store.Propagation
import io.github.gciatto.ktconstraints.solve.store.Residual
import io.github.gciatto.ktconstraints.solve.store.StoreMark
import io.github.gciatto.ktconstraints.solve.store.StoreStatus
import kotlin.jvm.JvmStatic
import kotlin.time.TimeSource

/**
 * [ConstraintStore] over any incremental session. Every post runs a complete satisfiability check, so statuses and
 * entailments are proofs, but nothing propagates: domains stay as declared. Marks map to session frames.
 */
@Suppress("TooManyFunctions") // One override per ConstraintStore operation, plus the shared declaration step.
class SatisfiabilityStore private constructor(
    private val shared: SharedSolver,
) : ConstraintStore {
    private val factory = VariableFactory.empty()
    private val session: IncrementalSession = shared.acquire().openSession(ConstraintModel.empty())
    private val frames = mutableListOf(StoreFrame(null, failedBefore = false))
    private val guard = StoreGuard()

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
        guard.requireOwned(constraint.variables)
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
        guard.requireOpen()
        val mark = SessionMark(this, session.push())
        frames += StoreFrame(mark, isFailed)
        return mark
    }

    override fun undoTo(mark: StoreMark) {
        guard.requireOpen()
        require(mark is SessionMark && mark.owner === this) { "Mark belongs to another store" }
        val index = frames.indexOfFirst { it.mark === mark }
        require(index > 0) { "Mark is stale" }
        session.popTo(mark.checkpoint)
        isFailed = frames[index].failedBefore
        while (frames.size > index) guard.release(frames.removeAt(frames.lastIndex).variables)
    }

    /** A failed store vacuously entails everything. Otherwise two checks, sharing [timeoutMillis], decide. */
    override suspend fun entails(
        constraint: BoolExpr,
        timeoutMillis: Long?,
    ): EntailmentResult {
        guard.requireOwned(constraint.variables)
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
        guard.requireOwned(setOf(variable))
        return variable.domain
    }

    override fun project(variables: List<Variable>): Residual {
        guard.requireOwned(variables)
        val requested = variables.toSet()
        val constraints = frames.flatMap { it.constraints }.filter { constraint -> constraint.variables.any { it in requested } }
        return Residual(variables.filterIsInstance<IntVar>().associateWith { it.domain }, constraints, exact = false)
    }

    /** Core of the posted constraints, as assumptions of a throwaway session; all of them if no core is available. */
    override suspend fun explain(timeoutMillis: Long?): List<BoolExpr>? {
        guard.requireOpen()
        if (!isFailed) return null
        val posted = frames.flatMap { it.constraints }
        return shared.solver.openSession(ConstraintModel.empty()).use { probe ->
            frames.flatMap { it.variables }.forEach { probe.declare(it) }
            val result = probe.check(posted, timeoutMillis)
            if (result.status == CheckStatus.UNSATISFIABLE) result.unsatCore ?: posted else posted
        }
    }

    override fun fork(): ConstraintStore {
        guard.requireOpen()
        val child = SatisfiabilityStore(shared)
        frames.forEach { frame ->
            frame.variables.forEach { child.declare(it) }
            frame.constraints.forEach {
                child.session.add(it)
                child.frames.last().constraints += it
            }
        }
        child.isFailed = isFailed
        return child
    }

    override fun close() {
        if (!guard.close()) return
        session.close()
        shared.release()
    }

    private fun <V : Variable> declare(variable: V): V {
        guard.requireOpen()
        session.declare(variable)
        frames.last().variables += variable
        guard.adopt(variable)
        return variable
    }

    /** Factories. */
    companion object {
        /** Takes ownership of [solver]; closing the store closes it. */
        @JvmStatic
        fun of(solver: IncrementalConstraintSolver): SatisfiabilityStore = SatisfiabilityStore(SharedSolver(solver))
    }
}
