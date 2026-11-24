package io.github.gciatto.ktconstraints.solve.auto

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.stream.SolutionStream

/** Solver of an [AutoBackend]: batch solves run the portfolio, enumeration goes to the best enumerating route. */
internal class AutoSolver(
    override val descriptor: SolverDescriptor,
    private val portfolio: Portfolio,
    private val strategy: AutoStrategy,
) : ConstraintSolver {
    private val streams = mutableListOf<SolutionStream>()
    private var closed = false

    override suspend fun solve(
        model: ConstraintModel,
        options: SolveOptions,
    ): SolveResult {
        check(!closed) { "Solver is closed" }
        return when (strategy) {
            AutoStrategy.Fallback -> portfolio.fallback(model, options)
            is AutoStrategy.Race -> portfolio.race(model, options, strategy.parallelism)
        }
    }

    override fun solutions(
        model: ConstraintModel,
        options: SolveOptions,
    ): SolutionStream {
        check(!closed) { "Solver is closed" }
        val solver = portfolio.enumerator(model, options).createSolver()
        val stream = runCatching { OwningStream(solver.solutions(model, options), solver) }.onFailure { solver.close() }.getOrThrow()
        streams += stream
        return stream
    }

    override fun close() {
        if (closed) return
        closed = true
        streams.forEach { it.close() }
    }

    /** A route's stream that also closes the route's solver. */
    private class OwningStream(
        private val stream: SolutionStream,
        private val solver: ConstraintSolver,
    ) : SolutionStream by stream {
        override fun close() {
            try {
                stream.close()
            } finally {
                solver.close()
            }
        }
    }
}
