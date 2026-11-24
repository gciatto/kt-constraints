package io.github.gciatto.ktconstraints.solve.auto

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.Objective
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.result.SolveMetadata
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.Platform
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.solve.spi.exception.UnsupportedModelException
import io.github.gciatto.ktconstraints.solve.spi.ranking.RankingPolicy
import io.github.gciatto.ktconstraints.solve.spi.registry.Candidate
import io.github.gciatto.ktconstraints.solve.spi.registry.Selection
import io.github.gciatto.ktconstraints.solve.spi.registry.SolverRegistry
import io.github.gciatto.ktconstraints.solve.spi.support.SupportReport
import io.github.gciatto.ktconstraints.solve.spi.withSolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Runs a model on several backends of [registry], ranked by [policy] on [platform]. A proof (optimal, infeasible,
 * unbounded, or any solution of a satisfaction model) comes from a single backend's own result. Timeouts, crashes,
 * unavailable routes and unsupported models are never evidence, and every skipped or failed backend is reported
 * in the result's metadata messages. The answering backend's descriptor identifies the result. When none answers,
 * the result is `UNKNOWN` from [descriptor]; when no backend is eligible, solving fails with
 * [UnsupportedModelException].
 */
internal class Portfolio(
    private val registry: SolverRegistry,
    private val policy: RankingPolicy,
    private val platform: Platform,
    private val descriptor: SolverDescriptor,
) {
    /** Tries the ranked backends one at a time under one shared deadline, moving on only after UNKNOWN or failure. */
    suspend fun fallback(
        model: ConstraintModel,
        options: SolveOptions = SolveOptions.DEFAULT,
    ): SolveResult {
        val notes = mutableListOf<String>()
        val deadline = Deadline(options.timeoutMillis)
        val candidates = rank(model, options, notes)
        for (candidate in candidates) {
            if (deadline.expired) break
            when (val attempt = attempt(candidate.backend, model, options.copy(timeoutMillis = deadline.remaining()))) {
                is Attempt.Answer ->
                    if (attempt.result.status ==
                        SolveStatus.UNKNOWN
                    ) {
                        notes += unknown(attempt.result)
                    } else {
                        return annotated(attempt.result, notes)
                    }
                is Attempt.Failure -> notes += attempt.message
            }
        }
        return undecided(deadline, notes)
    }

    /**
     * Runs up to [parallelism] ranked backends at once under one shared deadline, starting the next as each
     * finishes without a proof. The first proof wins and cancels the rest; otherwise the best incumbent is returned
     * without claiming optimality.
     */
    suspend fun race(
        model: ConstraintModel,
        options: SolveOptions = SolveOptions.DEFAULT,
        parallelism: Int,
    ): SolveResult {
        val notes = mutableListOf<String>()
        val deadline = Deadline(options.timeoutMillis)
        val queue = ArrayDeque(rank(model, options, notes))
        var best: SolveResult? = null
        val proof =
            coroutineScope {
                val outcomes = Channel<Attempt>(Channel.UNLIMITED)
                val jobs = mutableListOf<Job>()

                fun startNext(): Boolean {
                    if (deadline.expired) return false
                    val candidate = queue.removeFirstOrNull() ?: return false
                    val budget = options.copy(timeoutMillis = deadline.remaining())
                    jobs += launch { outcomes.send(attempt(candidate.backend, model, budget)) }
                    return true
                }

                var running = (1..parallelism).count { startNext() }
                var winner: SolveResult? = null
                while (running > 0 && winner == null) {
                    when (val attempt = outcomes.receive()) {
                        is Attempt.Answer ->
                            when {
                                isProof(attempt.result, model) -> winner = attempt.result
                                attempt.result.status == SolveStatus.FEASIBLE -> best = better(best, attempt.result, model)
                                else -> notes += unknown(attempt.result)
                            }
                        is Attempt.Failure -> notes += attempt.message
                    }
                    running--
                    if (winner == null && startNext()) running++
                }
                jobs.forEach { it.cancel() }
                winner
            }
        return (proof ?: best)?.let { annotated(it, notes) } ?: undecided(deadline, notes)
    }

    /** The best-ranked route able to enumerate solutions of [model]. */
    fun enumerator(
        model: ConstraintModel,
        options: SolveOptions,
    ): SolverBackend = rank(model, options, mutableListOf(), setOf(SolverCapability.ENUMERATION)).first().backend

    /** Whether some route is eligible for [model], with every rejection reason otherwise. */
    fun support(
        model: ConstraintModel,
        options: SolveOptions,
    ): SupportReport {
        val selection = registry.rank(model, options, policy = policy, platform = platform)
        return SupportReport(if (selection.candidates.isEmpty()) rejections(selection) else emptyList())
    }

    private fun rank(
        model: ConstraintModel,
        options: SolveOptions,
        notes: MutableList<String>,
        required: Set<SolverCapability> = emptySet(),
    ): List<Candidate> {
        val selection = registry.rank(model, options, required, policy, platform)
        if (selection.candidates.isEmpty()) throw UnsupportedModelException(SupportReport(rejections(selection)))
        notes += rejections(selection)
        return selection.candidates
    }

    private suspend fun attempt(
        backend: SolverBackend,
        model: ConstraintModel,
        options: SolveOptions,
    ): Attempt =
        try {
            Attempt.Answer(backend.withSolver { it.solve(model, options) })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (
            @Suppress("TooGenericExceptionCaught") failure: Exception, // Any backend failure is reported, never fatal.
        ) {
            Attempt.Failure("${backend.descriptor.id} failed: ${failure.message ?: failure::class.simpleName}")
        }

    private fun annotated(
        result: SolveResult,
        notes: List<String>,
    ): SolveResult =
        result.copy(
            metadata =
                SolveMetadata(
                    result.metadata.messages + notes + "auto: answered by ${result.descriptor.id}",
                    result.metadata.statistics,
                ),
        )

    private fun undecided(
        deadline: Deadline,
        notes: List<String>,
    ): SolveResult =
        SolveResult(
            SolveStatus.UNKNOWN,
            null,
            descriptor,
            if (deadline.expired) TerminationReason.TIMEOUT else TerminationReason.COMPLETED,
            metadata = SolveMetadata(notes + "auto: no backend answered"),
        )

    private companion object {
        private fun rejections(selection: Selection) =
            selection.rejections.map { (id, reasons) -> "$id rejected: ${reasons.joinToString("; ")}" }

        private fun unknown(result: SolveResult) = "${result.descriptor.id}: UNKNOWN (${result.terminationReason})"

        private fun isProof(
            result: SolveResult,
            model: ConstraintModel,
        ): Boolean =
            when (result.status) {
                SolveStatus.OPTIMAL, SolveStatus.INFEASIBLE, SolveStatus.UNBOUNDED, SolveStatus.INFEASIBLE_OR_UNBOUNDED -> true
                SolveStatus.FEASIBLE -> model.objective == null
                SolveStatus.UNKNOWN -> false
            }

        private fun better(
            current: SolveResult?,
            candidate: SolveResult,
            model: ConstraintModel,
        ): SolveResult {
            val incumbent = current?.objectiveValue ?: return candidate
            val value = candidate.objectiveValue ?: return current
            val improves = if (model.objective is Objective.Maximize) value > incumbent else value < incumbent
            return if (improves) candidate else current
        }
    }
}
