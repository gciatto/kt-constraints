package io.github.gciatto.ktconstraints.solve.spi.managed
import io.github.gciatto.ktconstraints.solve.incremental.Checkpoint

/** Ownership and staleness rules for one session's checkpoints; confine each stack to its session. */
class CheckpointStack {
    private val live = mutableListOf<Checkpoint>()

    /** Number of frames currently open. */
    val depth: Int get() = live.size

    /** Opens a frame and returns its checkpoint. */
    fun push(): Checkpoint = OwnedCheckpoint(this).also { live += it }

    /** Removes [checkpoint] and newer frames, returning how many native frames to pop. */
    fun popTo(checkpoint: Checkpoint): Int {
        require(checkpoint is OwnedCheckpoint && checkpoint.owner === this) { "Checkpoint belongs to another session" }
        val index = live.indexOfFirst { it === checkpoint }
        require(index >= 0) { "Checkpoint is stale" }
        val popped = live.size - index
        repeat(popped) { live.removeAt(live.lastIndex) }
        return popped
    }

    private class OwnedCheckpoint(
        val owner: CheckpointStack,
    ) : Checkpoint
}
