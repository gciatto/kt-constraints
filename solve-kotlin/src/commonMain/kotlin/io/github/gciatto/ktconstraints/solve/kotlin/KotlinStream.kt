package io.github.gciatto.ktconstraints.solve.kotlin

import io.github.gciatto.ktconstraints.core.BoolExpr
import io.github.gciatto.ktconstraints.core.GroundEvaluator
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.Objective
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
import io.github.gciatto.ktconstraints.solve.spi.ManagedSolutionStream
import org.gciatto.kt.math.BigInteger
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/** Cold, pull-driven stream with exact projection blocking and integer objective-bound search. */
@OptIn(ExperimentalAtomicApi::class)
internal class KotlinStream(
    private val model: ConstraintModel,
    private val options: SolveOptions,
    descriptor: SolverDescriptor,
    private val configuration: KotlinConfiguration,
    onClosed: () -> Unit,
) : ManagedSolutionStream(descriptor, onClosed) {
    private val closed = AtomicBoolean(false)
    private val budget = SearchBudget(options.timeoutMillis, configuration.workLimit) { closed.load() }
    private var engine: KotlinEngine? = null
    private var optimization: OptimizationState? = null
    private var incumbent: SolutionFound? = null
    private var pendingBlock: BoolExpr? = null
    private var emitted = 0

    override suspend fun readNext(): SolutionEvent = try {
        // Each pull may run in a different coroutine: refresh the polled context before polling it.
        budget.checkpoint()
        budget.poll()
        step()
    } catch (stopped: SearchStopped) {
        complete(stopped.reason, proven = false)
    }

    override fun release() {
        closed.store(true)
    }

    private suspend fun step(): SolutionEvent {
        if (options.solutionLimit?.let { emitted >= it } == true) return complete(TerminationReason.RESOURCE_LIMIT, false)
        val current = engine ?: initialize()
        val blocking = pendingBlock
        if (blocking != null) {
            current.circuit.clause(current.literal(blocking, budget))
            pendingBlock = null
        }
        while (true) {
            budget.checkpoint()
            if (optimization?.proven == true) return complete(TerminationReason.COMPLETED)
            val target = optimization?.target()
            val assumptions = if (target == null) intArrayOf() else intArrayOf(objectiveAtLeast(current, target))
            val full = current.check(assumptions, budget)
            if (full == null) {
                if (target == null) return complete(TerminationReason.COMPLETED)
                requireNotNull(optimization).refuted(target)
            } else {
                val objectiveValue = when (val objective = model.objective) {
                    is Objective.Minimize -> GroundEvaluator.evaluate(objective.expression, full)
                    is Objective.Maximize -> GroundEvaluator.evaluate(objective.expression, full)
                    null -> null
                }
                if (objectiveValue != null) requireNotNull(optimization).improved(objectiveValue)
                val found = SolutionFound(full.project(model.outputVariables), objectiveValue)
                incumbent = found
                emitted++
                if (optimization == null) pendingBlock = ProjectionBlocker.block(model.outputVariables, found.assignment)
                return found
            }
        }
    }

    private suspend fun initialize(): KotlinEngine {
        val current = KotlinEngine(model, configuration, budget)
        current.initialize(budget, includeObjective = true)
        val objective = current.objective
        if (objective != null) {
            val maximize = model.objective is Objective.Maximize
            optimization = OptimizationState(maximize, if (maximize) objective.max else -objective.min)
        }
        engine = current
        return current
    }

    private suspend fun objectiveAtLeast(current: KotlinEngine, target: BigInteger): Int {
        val binary = current.translation.binary
        val objective = requireNotNull(current.objective)
        return if (model.objective is Objective.Maximize) {
            -binary.less(objective, binary.integer(target))
        } else {
            -binary.less(binary.integer(-target), objective)
        }
    }

    private fun complete(reason: TerminationReason, proven: Boolean = true): SolveCompleted {
        val best = incumbent
        val status = when {
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
                if (status == SolveStatus.OPTIMAL) best?.objectiveValue else optimization?.bestBound,
                engine?.metadata(budget) ?: SolveMetadata(statistics = mapOf("work" to budget.work.toString())),
            ),
        )
    }
}
