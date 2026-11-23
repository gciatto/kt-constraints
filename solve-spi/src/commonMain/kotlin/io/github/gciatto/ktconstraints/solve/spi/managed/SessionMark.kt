package io.github.gciatto.ktconstraints.solve.spi.managed
import io.github.gciatto.ktconstraints.solve.incremental.Checkpoint
import io.github.gciatto.ktconstraints.solve.store.StoreMark

/** A [StoreMark] backed by one session [checkpoint]; [owner] identifies the store that issued it. */
internal class SessionMark(
    val owner: Any,
    val checkpoint: Checkpoint,
) : StoreMark
