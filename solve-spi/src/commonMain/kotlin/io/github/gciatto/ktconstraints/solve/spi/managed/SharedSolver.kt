package io.github.gciatto.ktconstraints.solve.spi.managed
import io.github.gciatto.ktconstraints.solve.incremental.IncrementalConstraintSolver
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch

/** One [solver] shared by a store and its forks; the last store to release it closes it. */
@OptIn(ExperimentalAtomicApi::class)
internal class SharedSolver(
    val solver: IncrementalConstraintSolver,
) {
    private val users = AtomicInt(0)

    fun acquire(): IncrementalConstraintSolver = solver.also { users.incrementAndFetch() }

    fun release() {
        if (users.decrementAndFetch() == 0) solver.close()
    }
}
