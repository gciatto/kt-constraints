package io.github.gciatto.ktconstraints.solve.z3

import io.github.gciatto.ktconstraints.core.BoolVar
import io.github.gciatto.ktconstraints.core.GroundEvaluator
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.Objective
import io.github.gciatto.ktconstraints.solve.Assignment
import io.github.gciatto.ktconstraints.solve.CheckStatus
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
import org.gciatto.kt.math.BigInteger
import kotlin.time.TimeSource

/** Z3 search stream owning one port; enumerates distinct projections or strictly improving incumbents. */
internal class Z3Stream<T>(
    private val model: ConstraintModel,
    private val options: SolveOptions,
    descriptor: SolverDescriptor,
    private val owner: Z3Owner<T>,
    onClosed: () -> Unit,
) : ManagedSolutionStream(descriptor, onClosed) {
    private val started = TimeSource.Monotonic.markNow()
    private var search: Search<T>? = null
    private var incumbent: SolutionFound? = null
    private var emitted = 0
    private val messages = mutableListOf<String>()

    override suspend fun readNext(): SolutionEvent = owner.confined { step(it) }

    override fun release() = owner.close()

    private suspend fun step(port: Z3Port<T>): SolutionEvent {
        val current = search ?: open(port)
        val remaining = options.timeoutMillis?.let { it - started.elapsedNow().inWholeMilliseconds }
        return when {
            options.solutionLimit?.let { emitted >= it } == true -> complete(TerminationReason.RESOURCE_LIMIT)
            remaining != null && remaining <= 0 -> complete(TerminationReason.TIMEOUT)
            else -> {
                val outcome = port.check(emptyList(), remaining)
                when (outcome.status) {
                    CheckStatus.UNSATISFIABLE -> complete(TerminationReason.COMPLETED)
                    CheckStatus.SATISFIABLE -> solution(port, current)
                    CheckStatus.UNKNOWN -> unknown(outcome.reason)
                }
            }
        }
    }

    private fun open(port: Z3Port<T>): Search<T> {
        val translation = Z3Translation(port, model)
        val objective =
            when (val objective = model.objective) {
                is Objective.Minimize -> translation.translate(objective.expression)
                is Objective.Maximize -> translation.translate(objective.expression)
                null -> null
            }
        port.assert(translation.assertions + translation.drainDefinedness())
        return Search(translation, objective).also { search = it }
    }

    private fun solution(
        port: Z3Port<T>,
        current: Search<T>,
    ): SolutionEvent {
        val full = current.translation.assignment()
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
        port.assert(listOf(block(port, current, projected, value)))
        emitted++
        return SolutionFound(projected, value).also { incumbent = it }
    }

    private fun block(
        port: Z3Port<T>,
        current: Search<T>,
        projected: Assignment,
        value: BigInteger?,
    ): T {
        val objective = current.objective
        if (objective != null) {
            val bound = port.int(requireNotNull(value))
            return if (model.objective is Objective.Minimize) port.less(objective, bound) else port.less(bound, objective)
        }
        val differences =
            model.outputVariables.map { variable ->
                when (variable) {
                    is BoolVar -> port.not(current.translation.binding(variable, requireNotNull(projected.valueOf(variable))))
                    is IntVar -> port.not(current.translation.binding(variable, requireNotNull(projected.valueOf(variable))))
                    else -> error("Unsupported projection: ${variable.name}")
                }
            }
        return port.or(differences)
    }

    private fun unknown(reason: String): SolveCompleted {
        messages += "Z3 unknown: $reason"
        val expired = options.timeoutMillis?.let { started.elapsedNow().inWholeMilliseconds >= it } == true
        return when {
            owner.isClosed -> complete(TerminationReason.CANCELLED)
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

    private class Search<T>(
        val translation: Z3Translation<T>,
        val objective: T?,
    )
}
