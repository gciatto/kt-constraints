package io.github.gciatto.ktconstraints.solve.spi

import io.github.gciatto.ktconstraints.solve.Checkpoint
import io.github.gciatto.ktconstraints.solve.StoreMark

/** A [StoreMark] backed by one session [checkpoint]; [owner] identifies the store that issued it. */
internal class SessionMark(
    val owner: Any,
    val checkpoint: Checkpoint,
) : StoreMark
