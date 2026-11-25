package io.github.gciatto.ktconstraints.solve.z3.session
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.bitvector.BitVecVar
import io.github.gciatto.ktconstraints.core.bool.BoolVar
import io.github.gciatto.ktconstraints.core.evaluation.GroundEvaluator
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.core.real.RealExpr
import io.github.gciatto.ktconstraints.core.real.RealVar
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.Objective
import io.github.gciatto.ktconstraints.model.isReal
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

    /** The best confirmed-feasible solution of a real objective's optimization so far. */
    private var candidate: SolutionFound? = null

    /** The completion that follows a real objective's single optimizer incumbent. */
    private var settled: SolveCompleted? = null
    private var emitted = 0
    private val messages = mutableListOf<String>()

    override suspend fun readNext(): SolutionEvent = owner.confined { step(it) }

    override fun release() = owner.close()

    private suspend fun step(port: Z3Port<T>): SolutionEvent {
        settled?.let { return it }
        if (model.objective?.isReal == true) {
            return try {
                optimize(port)
            } catch (inconclusive: Unconfirmed) {
                settle(candidate, null, unknownReason(inconclusive.reason))
            }
        }
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

    /**
     * Optimizes a real objective with Z3's optimizer, treating each of its answers as a claim to confirm: Z3's MILP
     * optimization can report wrong bounds, so a claimed attained optimum `v` stands only once the plain solver finds
     * nothing better than `v`, and a claimed unattained bound `u` only once nothing reaches `u`. A refuting model
     * becomes the incumbent and the optimizer runs again above it. Unconfirmed claims leave a `FEASIBLE` incumbent.
     */
    private suspend fun optimize(port: Z3Port<T>): SolutionEvent {
        val goal = requireNotNull(model.objective)
        val expression = goal.expression as RealExpr
        val translation = Z3Translation(port, model)
        val objective = translation.translate(expression)
        val constraints = translation.assertions + translation.drainDefinedness()
        val improving = { value: Rational -> listOf(improves(port, objective, value, goal.maximize, strict = true)) }
        var best: SolutionFound? by ::candidate
        repeat(MAX_OPTIMIZER_ROUNDS) {
            val remaining = options.timeoutMillis?.let { it - started.elapsedNow().inWholeMilliseconds }
            if (remaining != null && remaining <= 0) return settle(best, null, TerminationReason.TIMEOUT)
            val above = best?.realObjectiveValue?.let(improving).orEmpty()
            val optimum = port.optimize(constraints + above, objective, goal.maximize, remaining)
            when (optimum.outcome.status) {
                CheckStatus.UNKNOWN -> return best?.let { settle(it, null, unknownReason(optimum.outcome.reason)) }
                    ?: unknown(optimum.outcome.reason)
                CheckStatus.UNSATISFIABLE -> {
                    val incumbent = best ?: return complete(TerminationReason.COMPLETED)
                    val value = requireNotNull(incumbent.realObjectiveValue)
                    // Nothing improves on the incumbent, if the plain solver agrees with the optimizer.
                    when (val counter = refute(port, translation, expression, constraints + improving(value))) {
                        null -> return settle(incumbent, value, TerminationReason.COMPLETED)
                        else -> best = better(incumbent, counter, goal.maximize)
                    }
                }
                CheckStatus.SATISFIABLE -> {
                    // shortcut: unboundedness is the optimizer's claim; a ray certificate would need the linear structure.
                    if (optimum.infinity != Rational.ZERO) {
                        return SolveCompleted(SolveResult(SolveStatus.UNBOUNDED, null, descriptor, metadata = SolveMetadata(messages)))
                    }
                    val found = read(translation, expression)
                    val incumbent = best?.let { better(it, found, goal.maximize) } ?: found
                    best = incumbent
                    val attained = optimum.epsilon == Rational.ZERO
                    val refutation = improves(port, objective, optimum.value, goal.maximize, strict = attained)
                    when (val counter = refute(port, translation, expression, constraints + refutation)) {
                        null -> return settle(incumbent, optimum.value, TerminationReason.COMPLETED)
                        else -> best = better(incumbent, counter, goal.maximize)
                    }
                }
            }
        }
        messages += "Z3's optimizer claims could not be confirmed in $MAX_OPTIMIZER_ROUNDS rounds"
        return settle(best, null, TerminationReason.COMPLETED)
    }

    /** `objective > value` (or `≥` when not [strict]) when maximizing, its mirror when minimizing. */
    private fun improves(
        port: Z3Port<T>,
        objective: T,
        value: Rational,
        maximize: Boolean,
        strict: Boolean,
    ): T {
        val bound = port.real(value)
        return when {
            maximize && strict -> port.less(bound, objective)
            maximize -> port.lessOrEqual(bound, objective)
            strict -> port.less(objective, bound)
            else -> port.lessOrEqual(objective, bound)
        }
    }

    /** A validated model satisfying [constraints], from the plain solver, or null when it proves there is none. */
    private suspend fun refute(
        port: Z3Port<T>,
        translation: Z3Translation<T>,
        objective: RealExpr,
        constraints: List<T>,
    ): SolutionFound? {
        // The optimizer call may have used most of the budget: confirm only within what is left now.
        val remaining = options.timeoutMillis?.let { (it - started.elapsedNow().inWholeMilliseconds).coerceAtLeast(1) }
        port.push()
        try {
            port.assert(constraints)
            val outcome = port.check(emptyList(), remaining)
            return when (outcome.status) {
                CheckStatus.UNSATISFIABLE -> null
                CheckStatus.SATISFIABLE -> read(translation, objective)
                CheckStatus.UNKNOWN -> throw Unconfirmed(outcome.reason)
            }
        } finally {
            port.pop(1)
        }
    }

    private fun better(
        current: SolutionFound,
        candidate: SolutionFound,
        maximize: Boolean,
    ): SolutionFound {
        val old = requireNotNull(current.realObjectiveValue)
        val new = requireNotNull(candidate.realObjectiveValue)
        return if (if (maximize) new > old else new < old) candidate else current
    }

    /** The validated model of the last satisfiable call, with its exact objective value. */
    private fun read(
        translation: Z3Translation<T>,
        objective: RealExpr,
    ): SolutionFound {
        val full = translation.assignment()
        if (!model.constraints.all { GroundEvaluator.evaluate(it, full) }) {
            throw BackendExecutionException("${descriptor.id} returned an assignment violating the model")
        }
        return SolutionFound(full.project(model.outputVariables), realObjectiveValue = GroundEvaluator.evaluate(objective, full))
    }

    /**
     * Emits [found] and settles on its completion: `OPTIMAL` when the confirmed [bound] equals its value, otherwise
     * `FEASIBLE` with the bound, if any. Without an incumbent, completes as the [reason] dictates.
     */
    private fun settle(
        found: SolutionFound?,
        bound: Rational?,
        reason: TerminationReason,
    ): SolutionEvent {
        if (found == null) return complete(reason)
        val value = requireNotNull(found.realObjectiveValue)
        val optimal = reason == TerminationReason.COMPLETED && bound == value
        emitted++
        incumbent = found
        settled =
            SolveCompleted(
                SolveResult(
                    if (optimal) SolveStatus.OPTIMAL else SolveStatus.FEASIBLE,
                    found.assignment,
                    descriptor,
                    reason,
                    realObjectiveValue = value,
                    realBestBound = bound,
                    metadata = SolveMetadata(messages),
                ),
            )
        return found
    }

    private fun unknownReason(reason: String): TerminationReason {
        messages += "Z3 unknown: $reason"
        val expired = options.timeoutMillis?.let { started.elapsedNow().inWholeMilliseconds >= it } == true
        return if (owner.isClosed) {
            TerminationReason.CANCELLED
        } else if (expired ||
            reason == "timeout"
        ) {
            TerminationReason.TIMEOUT
        } else {
            TerminationReason.COMPLETED
        }
    }

    private fun open(port: Z3Port<T>): Search<T> {
        val translation = Z3Translation(port, model)
        val objective =
            when (val objective = model.objective) {
                is Objective.Minimize -> translation.translate(objective.expression)
                is Objective.Maximize -> translation.translate(objective.expression)
                is Objective.MinimizeReal, is Objective.MaximizeReal -> error("Real objectives use the optimizer")
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
                is Objective.MinimizeReal, is Objective.MaximizeReal -> error("Real objectives use the optimizer")
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

    /** A plain-solver check that could not confirm or refute an optimizer claim. */
    private class Unconfirmed(
        val reason: String,
    ) : RuntimeException("Z3 could not confirm an optimizer claim: $reason")

    private companion object {
        /** Optimizer runs whose claims a plain check refutes, before settling for the incumbent. */
        const val MAX_OPTIMIZER_ROUNDS = 8
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
