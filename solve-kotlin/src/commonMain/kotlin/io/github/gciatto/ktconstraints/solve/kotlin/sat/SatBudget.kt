package io.github.gciatto.ktconstraints.solve.kotlin.sat

/**
 * Counts search work without suspending; [poll] runs the cooperative checkpoint once at least
 * [QUANTUM] units accumulated, so hot loops stay plain while interruption latency stays bounded.
 * An Int countdown keeps the hot path free of Long arithmetic, which Kotlin/JS emulates in software.
 */
internal class SatBudget(
    private val checkpoint: suspend () -> Unit,
) {
    private var remaining = QUANTUM

    /** True once a checkpoint is owed; propagation pauses at the next safe point. */
    val due: Boolean get() = remaining <= 0

    fun tick() {
        remaining--
    }

    fun tick(units: Int) {
        remaining -= units
    }

    suspend fun poll() {
        if (remaining <= 0) {
            remaining = QUANTUM
            checkpoint()
        }
    }

    private companion object {
        const val QUANTUM = 256
    }
}
