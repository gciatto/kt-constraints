package io.github.gciatto.ktconstraints.interop

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolveResult
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
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
 * Java owner of one [ConstraintSolver] with blocking, [CompletableFuture] and iterator entry points, so Java callers
 * never handle Kotlin suspension. Closing it cancels pending futures and closes the solver and its streams.
 */
class BlockingSolver private constructor(
    private val solver: ConstraintSolver,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    /** Engine and execution route of the wrapped solver. */
    val descriptor: SolverDescriptor get() = solver.descriptor

    /** Solves on the calling thread; interrupting that thread cancels the search. */
    @JvmOverloads
    fun solve(
        model: ConstraintModel,
        options: SolveOptions = SolveOptions.DEFAULT,
    ): SolveResult = runBlocking { solver.solve(model, options) }

    /** Solves in the background; cancelling the returned future cancels the search. */
    @JvmOverloads
    fun solveAsync(
        model: ConstraintModel,
        options: SolveOptions = SolveOptions.DEFAULT,
    ): CompletableFuture<SolveResult> = scope.future { solver.solve(model, options) }

    /** Opens a single-pass search to iterate; close it when stopping early. */
    @JvmOverloads
    fun solutions(
        model: ConstraintModel,
        options: SolveOptions = SolveOptions.DEFAULT,
    ): BlockingSolutionStream = BlockingSolutionStream(solver.solutions(model, options))

    override fun close() {
        scope.cancel()
        solver.close()
    }

    /** Static factories. */
    companion object {
        /** Creates a fresh solver from [backend], failing explicitly if it is unavailable. */
        @JvmStatic
        fun of(backend: SolverBackend): BlockingSolver = of(backend.create())

        /** Takes ownership of [solver]. */
        @JvmStatic
        fun of(solver: ConstraintSolver): BlockingSolver = BlockingSolver(solver)
    }
}
