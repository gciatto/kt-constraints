package io.github.gciatto.ktconstraints.solve.kotlin.sat

/**
 * Counts search work without suspending; [poll] runs the cooperative checkpoint once at least
 * [QUANTUM] units accumulated, so hot loops stay plain while interruption latency stays bounded.
 */
internal class SatBudget(
    private val checkpoint: suspend () -> Unit,
) {
    private var work = 0L
    private var nextPoll = QUANTUM

    /** True once a checkpoint is owed; propagation pauses at the next safe point. */
    val due: Boolean get() = work >= nextPoll

    fun tick() {
        work++
    }

    fun tick(units: Int) {
        work += units
    }

    suspend fun poll() {
        if (work >= nextPoll) {
            nextPoll = work + QUANTUM
            checkpoint()
        }
    }

    private companion object {
        const val QUANTUM = 256L
    }
}
