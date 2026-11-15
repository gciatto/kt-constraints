// Z3's Java API accepts operand lists only as varargs, so building them requires a spread copy.
@file:Suppress("SpreadOperator")

package io.github.gciatto.ktconstraints.solve.z3

import com.microsoft.z3.Context
import com.microsoft.z3.IntNum
import com.microsoft.z3.Solver
import com.microsoft.z3.Status
import io.github.gciatto.ktconstraints.core.BoolExpr
import io.github.gciatto.ktconstraints.core.GroundEvaluator
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.Assignment
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
import org.gciatto.kt.math.BigInteger
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.TimeSource

/** Incremental Z3 session mapping checkpoints to native frames and assumptions to indicator constants. */
internal class Z3Session(
    private val model: ConstraintModel,
    override val descriptor: SolverDescriptor,
    private val onClosed: (Z3Session) -> Unit,
) : IncrementalSession {
    private val worker = Z3Worker()
    private val busy = AtomicBoolean(false)
    private val checkpoints = CheckpointStack()
    private val frames = mutableListOf(model.constraints.toMutableList())
    private lateinit var solver: Solver
    private lateinit var translation: Z3Translation
    private var checks = 0

    init {
        exclusive {
            worker.run { context ->
                translation = Z3Translation(context, model)
                solver = context.mkSolver()
                solver.add(*translation.assertions.toTypedArray())
            }
        }
    }

    override fun add(constraint: BoolExpr) =
        exclusive {
            requireAccepted(listOf(constraint))
            worker.run { solver.add(*(listOf(translation.boolean(constraint)) + translation.drainDefinedness()).toTypedArray()) }
            frames.last() += constraint
        }

    override fun push(): Checkpoint =
        exclusive {
            worker.run { solver.push() }
            frames += mutableListOf<BoolExpr>()
            checkpoints.push()
        }

    override fun popTo(checkpoint: Checkpoint) =
        exclusive {
            val popped = checkpoints.popTo(checkpoint)
            worker.run { solver.pop(popped) }
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
                worker.await { context -> checkNative(context, assumptions, timeoutMillis, started) }
            }
        }
    }

    override fun close() {
        if (worker.isClosed) return
        worker.close()
        onClosed(this)
    }

    private fun checkNative(
        context: Context,
        assumptions: List<BoolExpr>,
        timeoutMillis: Long?,
        started: TimeSource.Monotonic.ValueTimeMark,
    ): CheckResult {
        val round = checks++
        val indicators = assumptions.indices.map { context.mkBoolConst("assumption-$round-$it") }
        solver.push()
        try {
            assumptions.zip(indicators) { assumption, indicator ->
                val guarded = listOf(translation.boolean(assumption)) + translation.drainDefinedness()
                solver.add(context.mkImplies(indicator, context.mkAnd(*guarded.toTypedArray())))
            }
            val timeout = timeoutMillis?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: Int.MAX_VALUE
            solver.setParameters(context.mkParams().apply { add("timeout", timeout) })
            return when (solver.check(*indicators.toTypedArray())) {
                Status.SATISFIABLE -> satisfiable(assumptions)
                Status.UNSATISFIABLE -> {
                    val core = solver.unsatCore.toSet()
                    val relevant = assumptions.filterIndexed { index, _ -> indicators[index] in core }
                    CheckResult(CheckStatus.UNSATISFIABLE, null, descriptor, unsatCore = relevant)
                }
                else -> unknown(solver.reasonUnknown, timeoutMillis, started)
            }
        } finally {
            solver.pop()
        }
    }

    private fun satisfiable(assumptions: List<BoolExpr>): CheckResult {
        val found = solver.model
        val booleans = translation.booleanTerms.mapValues { (_, term) -> found.eval(term, true).isTrue }
        val integers = translation.integerTerms.mapValues { (_, term) -> BigInteger.of((found.eval(term, true) as IntNum).toString()) }
        val full = Assignment.of(booleans, integers)
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
                worker.isClosed -> TerminationReason.CANCELLED
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

    private inline fun <T> exclusive(block: () -> T): T {
        check(!worker.isClosed) { "Session is closed" }
        check(busy.compareAndSet(false, true)) { "Only one caller may use a session at a time" }
        try {
            return block()
        } finally {
            busy.set(false)
        }
    }
}
