package io.github.gciatto.ktconstraints.solve.spi

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor

internal class RecordingSolver(
    private val onOpen: () -> Unit = {},
    private val releaseHook: () -> Unit = {},
) : ManagedConstraintSolver() {
    override val descriptor = SolverDescriptor("fake", "stream", emptySet())
    val created = mutableListOf<RecordingStream>()

    override fun openStream(model: ConstraintModel, options: SolveOptions, onClosed: () -> Unit): ManagedSolutionStream {
        val stream = RecordingStream(releaseHook = releaseHook, onClosed = onClosed)
        created += stream
        onOpen()
        return stream
    }
}
