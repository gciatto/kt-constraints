package io.github.gciatto.ktconstraints.solve.kotlin.sat

/** Batches cooperative cancellation and work-limit checks without allocating per search event. */
internal class SatBudget(private val checkpoint: suspend () -> Unit) {
    private var work = 0

    suspend fun tick() {
        if ((++work and 255) == 0) checkpoint()
    }
}
