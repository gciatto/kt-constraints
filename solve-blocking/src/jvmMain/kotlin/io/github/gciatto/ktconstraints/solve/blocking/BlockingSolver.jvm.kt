package io.github.gciatto.ktconstraints.solve.blocking

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.future
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture

/**
 * Owner of one [ConstraintSolver] with blocking, [CompletableFuture] and iterator entry points, so callers never
 * handle Kotlin suspension. Closing it cancels pending futures and closes the solver and its streams, but never a
 * [SolverBackend] it was created from.
 */
actual class BlockingSolver private constructor(
    private val solver: ConstraintSolver,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    actual val descriptor: SolverDescriptor get() = solver.descriptor

    @JvmOverloads
    actual fun solve(
        model: ConstraintModel,
        options: SolveOptions,
    ): SolveResult = runBlocking { solver.solve(model, options) }

    /** Solves in the background; cancelling the returned future cancels the search. */
    @JvmOverloads
    fun solveAsync(
        model: ConstraintModel,
        options: SolveOptions = SolveOptions.DEFAULT,
    ): CompletableFuture<SolveResult> = scope.future { solver.solve(model, options) }

    @JvmOverloads
    actual fun solutions(
        model: ConstraintModel,
        options: SolveOptions,
    ): BlockingSolutionStream = BlockingSolutionStream(solver.solutions(model, options))

    actual fun openSession(model: ConstraintModel): BlockingSession = BlockingSession(solver.openIncrementalSession(model), scope)

    actual override fun close() {
        scope.cancel()
        solver.close()
    }

    /** Static factories. */
    actual companion object {
        @JvmStatic
        actual fun of(backend: SolverBackend): BlockingSolver = of(backend.createSolver())

        @JvmStatic
        actual fun of(solver: ConstraintSolver): BlockingSolver = BlockingSolver(solver)
    }
}
