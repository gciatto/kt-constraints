package io.github.gciatto.ktconstraints.solve.z3.session
import io.github.gciatto.ktconstraints.core.Variable
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.evaluation.GroundEvaluator
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.Objective
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.assignment.project
import io.github.gciatto.ktconstraints.solve.incremental.CheckResult
import io.github.gciatto.ktconstraints.solve.incremental.CheckStatus
import io.github.gciatto.ktconstraints.solve.incremental.Checkpoint
import io.github.gciatto.ktconstraints.solve.incremental.IncrementalSession
import io.github.gciatto.ktconstraints.solve.result.SolveMetadata
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.exception.BackendExecutionException
import io.github.gciatto.ktconstraints.solve.spi.exception.UnsupportedModelException
import io.github.gciatto.ktconstraints.solve.spi.managed.CheckpointStack
import io.github.gciatto.ktconstraints.solve.z3.port.Z3Owner
import io.github.gciatto.ktconstraints.solve.z3.port.Z3Port
import io.github.gciatto.ktconstraints.solve.z3.translation.Z3Support
import io.github.gciatto.ktconstraints.solve.z3.translation.Z3Translation
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.TimeSource

/** Incremental Z3 session mapping checkpoints to native frames and assumptions to indicator constants. */
@OptIn(ExperimentalAtomicApi::class)
internal class Z3Session<T>(
    private val model: ConstraintModel,
    override val descriptor: SolverDescriptor,
    private val owner: Z3Owner<T>,
    private val onClosed: (Z3Session<T>) -> Unit,
) : IncrementalSession {
    private val busy = AtomicBoolean(false)
    private val checkpoints = CheckpointStack()
    private val frames = mutableListOf(model.constraints.toMutableList())
    private val declarations = mutableListOf(mutableListOf<Variable>())
    private val known = model.variables.toMutableSet()

    /** The Z3 terms asserted in each frame beyond the translation's own, which optimizations hand to Z3's optimizer. */
    private val terms = mutableListOf(mutableListOf<T>())
    private val translation: Z3Translation<T>
    private var checks = 0

    init {
        translation =
            owner.confinedNow { port ->
                Z3Translation(port, model).also { port.assert(it.assertions) }
            }
    }

    override fun declare(variable: Variable) =
        exclusive {
            require(known.add(variable)) { "Variable already in the session: ${variable.name}" }
            try {
                owner.confinedNow { port ->
                    val domain = listOfNotNull(translation.declare(variable))
                    port.assert(domain)
                    terms.last() += domain
                }
            } catch (rejection: UnsupportedModelException) {
                known -= variable
                throw rejection
            }
            declarations.last() += variable
        }

    override fun add(constraint: BoolExpr) =
        exclusive {
            requireAccepted(listOf(constraint))
            owner.confinedNow { port ->
                val asserted = listOf(translation.translate(constraint)) + translation.drainDefinedness()
                port.assert(asserted)
                terms.last() += asserted
            }
            frames.last() += constraint
        }

    override fun push(): Checkpoint =
        exclusive {
            owner.confinedNow { it.push() }
            frames += mutableListOf<BoolExpr>()
            declarations += mutableListOf<Variable>()
            terms += mutableListOf<T>()
            checkpoints.push()
        }

    override fun popTo(checkpoint: Checkpoint) =
        exclusive {
            val popped = checkpoints.popTo(checkpoint)
            owner.confinedNow { it.pop(popped) }
            repeat(popped) {
                frames.removeAt(frames.lastIndex)
                terms.removeAt(terms.lastIndex)
            }
            val forgotten = List(popped) { declarations.removeAt(declarations.lastIndex) }.flatten()
            known -= forgotten.toSet()
            translation.forget(forgotten)
        }

    override suspend fun check(
        assumptions: List<BoolExpr>,
        timeoutMillis: Long?,
    ): CheckResult {
        require(timeoutMillis == null || timeoutMillis >= 0) { "Timeout must be non-negative" }
        return exclusive {
            requireAccepted(assumptions)
            if (timeoutMillis == 0L) {
                CheckResult(CheckStatus.UNKNOWN, null, descriptor, TerminationReason.TIMEOUT)
            } else {
                val started = TimeSource.Monotonic.markNow()
                owner.confined { port -> checkNative(port, assumptions, timeoutMillis, started) }
            }
        }
    }

    /**
     * Optimizes over the session's own context: Z3's optimizer sees the active assertions plus [assumptions], and its
     * claims are confirmed by plain checks of the session's solver in temporary frames. Like batch solves, a call that
     * overruns its timeout by a second is abandoned with the incumbent found so far.
     */
    override suspend fun optimize(
        objective: Objective,
        assumptions: List<BoolExpr>,
        timeoutMillis: Long?,
    ): SolveResult {
        require(timeoutMillis == null || timeoutMillis >= 0) { "Timeout must be non-negative" }
        return exclusive {
            requireAccepted(assumptions)
            val optimizer =
                Z3SessionOptimizer(
                    translation,
                    objective,
                    translation.assertions + terms.flatten(),
                    frames.flatten(),
                    assumptions,
                    model.outputVariables + declarations.flatten(),
                    descriptor,
                    timeoutMillis,
                )
            val call: suspend () -> SolveResult = {
                owner.confined { port ->
                    try {
                        optimizer.run(port)
                    } catch (inconclusive: Z3SessionOptimizer.Unconfirmed) {
                        optimizer.unconfirmed(inconclusive.reason)
                    }
                }
            }
            if (timeoutMillis == null) {
                call()
            } else {
                withTimeoutOrNull(timeoutMillis + WATCHDOG_GRACE_MILLIS) { call() }
                    ?: optimizer.result(null, TerminationReason.TIMEOUT)
            }
        }
    }

    override fun close() {
        if (owner.isClosed) return
        owner.close()
        onClosed(this)
    }

    private suspend fun checkNative(
        port: Z3Port<T>,
        assumptions: List<BoolExpr>,
        timeoutMillis: Long?,
        started: TimeSource.Monotonic.ValueTimeMark,
    ): CheckResult {
        val round = checks++
        val indicators = assumptions.indices.map { port.boolConst("assumption-$round-$it") }
        port.push()
        try {
            assumptions.zip(indicators) { assumption, indicator ->
                val guarded = listOf(translation.translate(assumption)) + translation.drainDefinedness()
                port.assert(listOf(port.implies(indicator, port.and(guarded))))
            }
            val outcome = port.check(indicators, timeoutMillis)
            return when (outcome.status) {
                CheckStatus.SATISFIABLE -> satisfiable(assumptions)
                CheckStatus.UNSATISFIABLE -> {
                    val core = port.unsatCore(indicators)
                    CheckResult(
                        CheckStatus.UNSATISFIABLE,
                        null,
                        descriptor,
                        unsatCore =
                            assumptions.filterIndexed { index, _ ->
                                index in
                                    core
                            },
                    )
                }
                CheckStatus.UNKNOWN -> unknown(outcome.reason, timeoutMillis, started)
            }
        } finally {
            if (!owner.isClosed) port.pop(1)
        }
    }

    private fun satisfiable(assumptions: List<BoolExpr>): CheckResult {
        val full = translation.assignment()
        if (!(frames.flatten() + assumptions).all { GroundEvaluator.evaluate(it, full) }) {
            throw BackendExecutionException("${descriptor.id} returned an assignment violating the session")
        }
        return CheckResult(CheckStatus.SATISFIABLE, full.project(model.outputVariables + declarations.flatten()), descriptor)
    }

    private fun unknown(
        reason: String,
        timeoutMillis: Long?,
        started: TimeSource.Monotonic.ValueTimeMark,
    ): CheckResult {
        val termination =
            when {
                owner.isClosed -> TerminationReason.CANCELLED
                timeoutMillis != null && started.elapsedNow().inWholeMilliseconds >= timeoutMillis -> TerminationReason.TIMEOUT
                reason == "timeout" -> TerminationReason.TIMEOUT
                reason == "canceled" -> TerminationReason.CANCELLED
                else -> TerminationReason.COMPLETED
            }
        return CheckResult(CheckStatus.UNKNOWN, null, descriptor, termination, metadata = SolveMetadata(listOf("Z3 unknown: $reason")))
    }

    private fun requireAccepted(expressions: List<BoolExpr>) {
        val foreign = expressions.flatMap { it.variables }.filterNot { it in known }
        require(foreign.isEmpty()) { "Variables not declared in the session: ${foreign.joinToString { it.name }}" }
        val report = Z3Support.report(expressions)
        if (!report.isSupported) throw UnsupportedModelException(report)
    }

    private inline fun <R> exclusive(block: () -> R): R {
        check(!owner.isClosed) { "Session is closed" }
        check(busy.compareAndSet(false, true)) { "Only one caller may use a session at a time" }
        try {
            return block()
        } finally {
            busy.store(false)
        }
    }
}

/** How long past its timeout an optimization may run before it is abandoned with its incumbent. */
private const val WATCHDOG_GRACE_MILLIS = 1000L
