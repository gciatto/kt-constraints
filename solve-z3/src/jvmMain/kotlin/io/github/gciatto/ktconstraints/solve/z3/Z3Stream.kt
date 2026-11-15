// Z3's Java API accepts operand lists only as varargs, so building them requires a spread copy.
@file:Suppress("SpreadOperator")

package io.github.gciatto.ktconstraints.solve.z3

import com.microsoft.z3.ArithExpr
import com.microsoft.z3.BoolExpr
import com.microsoft.z3.Context
import com.microsoft.z3.IntNum
import com.microsoft.z3.IntSort
import com.microsoft.z3.Model
import com.microsoft.z3.Solver
import com.microsoft.z3.Status
import io.github.gciatto.ktconstraints.core.BoolVar
import io.github.gciatto.ktconstraints.core.GroundEvaluator
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.Objective
import io.github.gciatto.ktconstraints.solve.Assignment
import io.github.gciatto.ktconstraints.solve.SolutionEvent
import io.github.gciatto.ktconstraints.solve.SolutionFound
import io.github.gciatto.ktconstraints.solve.SolveCompleted
import io.github.gciatto.ktconstraints.solve.SolveMetadata
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolveResult
import io.github.gciatto.ktconstraints.solve.SolveStatus
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.TerminationReason
import io.github.gciatto.ktconstraints.solve.project
import io.github.gciatto.ktconstraints.solve.spi.BackendExecutionException
import io.github.gciatto.ktconstraints.solve.spi.ManagedSolutionStream
import kotlinx.coroutines.suspendCancellableCoroutine
import org.gciatto.kt.math.BigInteger
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.time.TimeSource

/** One Z3 context confined to a dedicated thread; interrupted from any thread and closed on its own. */
internal class Z3Stream(
    private val model: ConstraintModel,
    private val options: SolveOptions,
    descriptor: SolverDescriptor,
    onClosed: () -> Unit,
) : ManagedSolutionStream(descriptor, onClosed) {
    private val started = TimeSource.Monotonic.markNow()
    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor { task -> Thread(task, "kt-constraints-z3").apply { isDaemon = true } }

    @Volatile private var context: Context? = null

    @Volatile private var released = false
    private var search: Search? = null
    private var incumbent: SolutionFound? = null
    private var emitted = 0
    private val messages = mutableListOf<String>()

    override suspend fun readNext(): SolutionEvent =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { context?.interrupt() }
            executor.execute { continuation.resumeWith(runCatching { step() }) }
        }

    override fun release() {
        released = true
        context?.interrupt()
        executor.execute { context?.close() }
        executor.shutdown()
    }

    private fun step(): SolutionEvent {
        val current = search ?: open()
        val remaining = options.timeoutMillis?.let { it - started.elapsedNow().inWholeMilliseconds }
        return when {
            options.solutionLimit?.let { emitted >= it } == true -> complete(TerminationReason.RESOURCE_LIMIT)
            remaining != null && remaining <= 0 -> complete(TerminationReason.TIMEOUT)
            else -> check(current, remaining)
        }
    }

    private fun check(
        current: Search,
        remaining: Long?,
    ): SolutionEvent {
        if (remaining != null) {
            current.solver.setParameters(
                current.context.mkParams().apply { add("timeout", remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) },
            )
        }
        return when (current.solver.check()) {
            Status.UNSATISFIABLE -> complete(TerminationReason.COMPLETED)
            Status.SATISFIABLE -> solution(current, current.solver.model)
            else -> unknown(current.solver.reasonUnknown)
        }
    }

    private fun open(): Search {
        check(!released) { "Stream is released" }
        val owned = Context()
        context = owned
        check(!released) { "Stream is released" }
        val translation = Z3Translation(owned, model)
        val objective =
            when (val objective = model.objective) {
                is Objective.Minimize -> translation.integer(objective.expression)
                is Objective.Maximize -> translation.integer(objective.expression)
                null -> null
            }
        val solver = owned.mkSolver()
        solver.add(*(translation.assertions + translation.definedness).toTypedArray())
        return Search(owned, solver, translation, objective).also { search = it }
    }

    private fun solution(
        current: Search,
        found: Model,
    ): SolutionEvent {
        val booleans = current.translation.booleanTerms.mapValues { (_, term) -> found.eval(term, true).isTrue }
        val integers =
            current.translation.integerTerms.mapValues { (_, term) -> BigInteger.of((found.eval(term, true) as IntNum).toString()) }
        val full = Assignment.of(booleans, integers)
        if (!model.constraints.all { GroundEvaluator.evaluate(it, full) }) {
            throw BackendExecutionException("${descriptor.id} returned an assignment violating the model")
        }
        val value =
            when (val objective = model.objective) {
                is Objective.Minimize -> GroundEvaluator.evaluate(objective.expression, full)
                is Objective.Maximize -> GroundEvaluator.evaluate(objective.expression, full)
                null -> null
            }
        val projected = full.project(model.outputVariables)
        current.solver.add(block(current, projected, value))
        emitted++
        return SolutionFound(projected, value).also { incumbent = it }
    }

    private fun block(
        current: Search,
        projected: Assignment,
        value: BigInteger?,
    ): BoolExpr {
        val context = current.context
        val objective = current.objective
        if (objective != null) {
            val bound = current.translation.literal(requireNotNull(value))
            return if (model.objective is Objective.Minimize) context.mkLt(objective, bound) else context.mkGt(objective, bound)
        }
        val differences =
            model.outputVariables.map { variable ->
                when (variable) {
                    is BoolVar -> {
                        val expected = context.mkBool(requireNotNull(projected.valueOf(variable)))
                        context.mkNot(context.mkEq(current.translation.booleanTerms.getValue(variable), expected))
                    }
                    is IntVar -> {
                        val expected = current.translation.literal(requireNotNull(projected.valueOf(variable)))
                        context.mkNot(context.mkEq(current.translation.integerTerms.getValue(variable), expected))
                    }
                    else -> error("Unsupported projection: ${variable.name}")
                }
            }
        return context.mkOr(*differences.toTypedArray())
    }

    private fun unknown(reason: String): SolveCompleted {
        messages += "Z3 unknown: $reason"
        val expired = options.timeoutMillis?.let { started.elapsedNow().inWholeMilliseconds >= it } == true
        return when {
            released -> complete(TerminationReason.CANCELLED)
            expired || reason == "timeout" -> complete(TerminationReason.TIMEOUT)
            else -> complete(TerminationReason.COMPLETED, proven = false)
        }
    }

    private fun complete(
        reason: TerminationReason,
        proven: Boolean = true,
    ): SolveCompleted {
        val best = incumbent
        val status =
            when {
                best == null -> if (reason == TerminationReason.COMPLETED && proven) SolveStatus.INFEASIBLE else SolveStatus.UNKNOWN
                model.objective != null && reason == TerminationReason.COMPLETED && proven -> SolveStatus.OPTIMAL
                else -> SolveStatus.FEASIBLE
            }
        return SolveCompleted(
            SolveResult(
                status,
                best?.assignment,
                descriptor,
                reason,
                best?.objectiveValue,
                if (status == SolveStatus.OPTIMAL) best?.objectiveValue else null,
                SolveMetadata(messages),
            ),
        )
    }

    private class Search(
        val context: Context,
        val solver: Solver,
        val translation: Z3Translation,
        val objective: ArithExpr<IntSort>?,
    )
}
