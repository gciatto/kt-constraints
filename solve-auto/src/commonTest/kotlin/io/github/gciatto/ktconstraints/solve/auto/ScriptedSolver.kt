package io.github.gciatto.ktconstraints.solve.auto

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.stream.SolutionStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** See [ScriptedBackend]; the boolean passed to the script tells whether the timeout cut the wait short. */
internal class ScriptedSolver(
    override val descriptor: SolverDescriptor,
    private val delayMillis: Long,
    private val log: AutoLog,
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
            // +1: Node timers may fire ~1 ms early, which would leave the shared deadline unexpired.
            delay(if (timedOut) options.timeoutMillis!! + 1 else delayMillis)
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
    ): SolutionStream = error("Scripted solvers only solve")

    override fun close() = Unit
}
