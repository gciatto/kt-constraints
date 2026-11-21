package io.github.gciatto.ktconstraints.solve.portfolio

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolutionStream
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolveResult
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** See [ScriptedBackend]; the boolean passed to the script tells whether the timeout cut the wait short. */
internal class ScriptedSolver(
    override val descriptor: SolverDescriptor,
    private val delayMillis: Long,
    private val log: PortfolioLog,
    private val script: (ConstraintModel, SolverDescriptor, Boolean) -> SolveResult,
) : ConstraintSolver {
    override suspend fun solve(
        model: ConstraintModel,
        options: SolveOptions,
    ): SolveResult {
        log.running++
        log.peak = maxOf(log.peak, log.running)
        try {
            val timedOut = options.timeoutMillis != null && options.timeoutMillis!! < delayMillis
            delay(if (timedOut) options.timeoutMillis!! else delayMillis)
            return script(model, descriptor, timedOut)
        } catch (cancelled: CancellationException) {
            log.cancelled += descriptor.engine
            throw cancelled
        } finally {
            log.running--
        }
    }

    override fun solutions(
        model: ConstraintModel,
        options: SolveOptions,
    ): SolutionStream = error("Portfolios use solve")

    override fun close() = Unit
}
