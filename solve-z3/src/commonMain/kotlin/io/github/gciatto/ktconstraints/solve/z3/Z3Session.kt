package io.github.gciatto.ktconstraints.solve.z3

import io.github.gciatto.ktconstraints.core.BoolExpr
import io.github.gciatto.ktconstraints.core.GroundEvaluator
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.CheckResult
import io.github.gciatto.ktconstraints.solve.CheckStatus
import io.github.gciatto.ktconstraints.solve.Checkpoint
import io.github.gciatto.ktconstraints.solve.IncrementalSession
import io.github.gciatto.ktconstraints.solve.SolveMetadata
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.TerminationReason
import io.github.gciatto.ktconstraints.solve.project
import io.github.gciatto.ktconstraints.solve.spi.BackendExecutionException
import io.github.gciatto.ktconstraints.solve.spi.CheckpointStack
import io.github.gciatto.ktconstraints.solve.spi.UnsupportedModelException
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
    private val translation: Z3Translation<T>
    private var checks = 0

    init {
        translation =
            owner.confinedNow { port ->
                Z3Translation(port, model).also { port.assert(it.assertions) }
            }
    }

    override fun add(constraint: BoolExpr) =
        exclusive {
            requireAccepted(listOf(constraint))
            owner.confinedNow { port -> port.assert(listOf(translation.translate(constraint)) + translation.drainDefinedness()) }
            frames.last() += constraint
        }

    override fun push(): Checkpoint =
        exclusive {
            owner.confinedNow { it.push() }
            frames += mutableListOf<BoolExpr>()
            checkpoints.push()
        }

    override fun popTo(checkpoint: Checkpoint) =
        exclusive {
            val popped = checkpoints.popTo(checkpoint)
            owner.confinedNow { it.pop(popped) }
            repeat(popped) { frames.removeAt(frames.lastIndex) }
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
        return CheckResult(CheckStatus.SATISFIABLE, full.project(model.outputVariables), descriptor)
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
        val declared = model.variables.toSet()
        val foreign = expressions.flatMap { it.variables }.filterNot { it in declared }
        require(foreign.isEmpty()) { "Variables not declared by the session model: ${foreign.joinToString { it.name }}" }
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
