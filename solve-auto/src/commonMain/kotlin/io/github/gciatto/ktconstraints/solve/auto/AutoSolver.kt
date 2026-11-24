package io.github.gciatto.ktconstraints.solve.auto

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.incremental.IncrementalConstraintSolver
import io.github.gciatto.ktconstraints.solve.incremental.IncrementalSession
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.spi.createIncrementalSolver
import io.github.gciatto.ktconstraints.solve.stream.SolutionStream

/**
 * Solver of an [AutoBackend]: batch solves run the portfolio, enumeration goes to the best enumerating route, and
 * each session goes to the best route with incremental assertions.
 */
internal class AutoSolver(
    override val descriptor: SolverDescriptor,
    private val portfolio: Portfolio,
    private val strategy: AutoStrategy,
) : IncrementalConstraintSolver {
    private val owned = mutableListOf<AutoCloseable>()
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
        owned += stream
        return stream
    }

    override fun openSession(model: ConstraintModel): IncrementalSession {
        check(!closed) { "Solver is closed" }
        val solver = portfolio.sessionRoute(model).createIncrementalSolver()
        val session = runCatching { OwningSession(solver.openSession(model), solver) }.onFailure { solver.close() }.getOrThrow()
        owned += session
        return session
    }

    override fun close() {
        if (closed) return
        closed = true
        owned.forEach { it.close() }
    }

    /** A route's session that also closes the route's solver. */
    private class OwningSession(
        private val session: IncrementalSession,
        private val solver: ConstraintSolver,
    ) : IncrementalSession by session {
        override fun close() {
            try {
                session.close()
            } finally {
                solver.close()
            }
        }
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
