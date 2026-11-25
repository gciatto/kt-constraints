package io.github.gciatto.ktconstraints.solve.z3.session
import io.github.gciatto.ktconstraints.core.bitvector.BitVecVar
import io.github.gciatto.ktconstraints.core.bool.BoolVar
import io.github.gciatto.ktconstraints.core.evaluation.GroundEvaluator
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.core.real.RealVar
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.Objective
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.assignment.Assignment
import io.github.gciatto.ktconstraints.solve.assignment.project
import io.github.gciatto.ktconstraints.solve.incremental.CheckStatus
import io.github.gciatto.ktconstraints.solve.result.SolveMetadata
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.exception.BackendExecutionException
import io.github.gciatto.ktconstraints.solve.spi.managed.ManagedSolutionStream
import io.github.gciatto.ktconstraints.solve.spi.support.rejectRealObjective
import io.github.gciatto.ktconstraints.solve.stream.SolutionEvent
import io.github.gciatto.ktconstraints.solve.stream.SolutionFound
import io.github.gciatto.ktconstraints.solve.stream.SolveCompleted
import io.github.gciatto.ktconstraints.solve.z3.port.Z3Owner
import io.github.gciatto.ktconstraints.solve.z3.port.Z3Port
import io.github.gciatto.ktconstraints.solve.z3.translation.Z3Translation
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
        while (true) {
            val remaining = options.timeoutMillis?.let { it - started.elapsedNow().inWholeMilliseconds }
            if (options.solutionLimit?.let { emitted >= it } == true) return complete(TerminationReason.RESOURCE_LIMIT)
            if (remaining != null && remaining <= 0) return complete(TerminationReason.TIMEOUT)
            if (current.isProven(incumbent?.objectiveValue)) return complete(TerminationReason.COMPLETED)
            val target = current.target(incumbent?.objectiveValue)
            // An optimizing check probes a tentative bound in its own frame; only refutations and incumbents persist.
            if (target != null) port.push()
            val (outcome, found) =
                try {
                    target?.let { port.assert(listOf(current.atLeast(port, it))) }
                    val outcome = port.check(emptyList(), remaining)
                    outcome to if (outcome.status == CheckStatus.SATISFIABLE) read(current) else null
                } finally {
                    if (target != null) port.pop(1)
                }
            when (outcome.status) {
                CheckStatus.SATISFIABLE -> return emit(port, current, requireNotNull(found))
                CheckStatus.UNKNOWN -> return unknown(outcome.reason)
                CheckStatus.UNSATISFIABLE -> if (target == null) return complete(TerminationReason.COMPLETED) else current.refuted = target
            }
        }
    }

    private fun open(port: Z3Port<T>): Search<T> {
        val translation = Z3Translation(port, model)
        val objective =
            when (val objective = model.objective) {
                is Objective.Minimize -> translation.translate(objective.expression)
                is Objective.Maximize -> translation.translate(objective.expression)
                is Objective.MinimizeReal, is Objective.MaximizeReal ->
                    rejectRealObjective()
                null -> null
            }
        port.assert(translation.assertions + translation.drainDefinedness())
        return Search(translation, objective, model.objective?.maximize == true).also { search = it }
    }

    private fun read(current: Search<T>): SolutionFound {
        val full = current.translation.assignment()
        if (!model.constraints.all { GroundEvaluator.evaluate(it, full) }) {
            throw BackendExecutionException("${descriptor.id} returned an assignment violating the model")
        }
        val value =
            when (val objective = model.objective) {
                is Objective.Minimize -> GroundEvaluator.evaluate(objective.expression, full)
                is Objective.Maximize -> GroundEvaluator.evaluate(objective.expression, full)
                is Objective.MinimizeReal, is Objective.MaximizeReal ->
                    rejectRealObjective()
                null -> null
            }
        return SolutionFound(full.project(model.outputVariables), value)
    }

    private fun emit(
        port: Z3Port<T>,
        current: Search<T>,
        found: SolutionFound,
    ): SolutionFound {
        port.assert(listOf(block(port, current, found.assignment, found.objectiveValue)))
        current.improved()
        emitted++
        return found.also { incumbent = it }
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
            return if (model.objective?.maximize == true) port.less(bound, objective) else port.less(objective, bound)
        }
        val differences =
            model.outputVariables.map { variable ->
                when (variable) {
                    is BoolVar -> port.not(current.translation.binding(variable, requireNotNull(projected.valueOf(variable))))
                    is IntVar -> port.not(current.translation.binding(variable, requireNotNull(projected.valueOf(variable))))
                    is RealVar -> port.not(current.translation.binding(variable, requireNotNull(projected.valueOf(variable))))
                    is BitVecVar -> port.not(current.translation.binding(variable, requireNotNull(projected.valueOf(variable))))
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

    /** Search state; optimization gallops beyond the incumbent, then bisects below the least refuted score. */
    private class Search<T>(
        val translation: Z3Translation<T>,
        val objective: T?,
        private val maximize: Boolean,
    ) {
        /** Least score proven unreachable (score is the objective, negated when minimizing). */
        var refuted: BigInteger? = null
        private var gap = BigInteger.ONE

        /** Whether the score right after [incumbent] is refuted, so no better objective value exists. */
        fun isProven(incumbent: BigInteger?): Boolean {
            val limit = refuted ?: return false
            return limit - score(requireNotNull(incumbent)) <= BigInteger.ONE
        }

        /** Next score to probe beyond [incumbent], or null for a check without a tentative bound. */
        fun target(incumbent: BigInteger?): BigInteger? {
            if (objective == null || incumbent == null) return null
            val best = score(incumbent)
            val limit = refuted ?: return best + gap
            return best + (limit - best) / BigInteger.TWO
        }

        /** Objective at least as good as the score [target]. */
        fun atLeast(
            port: Z3Port<T>,
            target: BigInteger,
        ): T {
            val term = requireNotNull(objective)
            return if (maximize) port.lessOrEqual(port.int(target), term) else port.lessOrEqual(term, port.int(-target))
        }

        fun improved() {
            if (refuted == null) gap += gap
        }

        private fun score(value: BigInteger): BigInteger = if (maximize) value else -value
    }
}
