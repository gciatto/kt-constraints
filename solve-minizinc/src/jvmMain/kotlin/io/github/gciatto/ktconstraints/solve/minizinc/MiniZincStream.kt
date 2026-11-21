package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.solve.SolutionEvent
import io.github.gciatto.ktconstraints.solve.SolveCompleted
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.TerminationReason
import io.github.gciatto.ktconstraints.solve.backend
import io.github.gciatto.ktconstraints.solve.spi.ManagedSolutionStream
import java.nio.file.Path

internal class MiniZincStream(
    executable: String,
    private val compiled: MiniZincModel,
    private val options: SolveOptions,
    onClosed: () -> Unit,
) : ManagedSolutionStream(compiled.engine.descriptor, onClosed) {
    private val decoder = MiniZincDecoder(compiled)
    private val process =
        MiniZincProcess(
            executable,
            compiled.source,
            options.timeoutMillis,
            onTimeout = { stop(TerminationReason.TIMEOUT) },
            onCancel = { cancel() },
            compiled.engine.solverArguments(options.backend<MiniZincOptions>()?.arguments(compiled.engine).orEmpty()),
        )
    private var emitted = 0
    internal val workDirectory: Path? get() = process.directory
    internal val processId: Long? get() = process.processId

    override suspend fun readNext(): SolutionEvent {
        val interruption =
            when {
                options.timeoutMillis == 0L -> TerminationReason.TIMEOUT
                options.solutionLimit?.let { emitted >= it } == true -> TerminationReason.RESOURCE_LIMIT
                else -> null
            }
        if (interruption != null) {
            stop(interruption)
            return SolveCompleted(requireNotNull(terminalResult))
        }
        var event: SolutionEvent? = null
        while (event == null) {
            val line = process.readLine()
            event = if (line == null) SolveCompleted(decoder.finish(process.exitCode(), process.stderr())) else decoder.accept(line)
        }
        if (event is io.github.gciatto.ktconstraints.solve.SolutionFound) emitted++
        return event
    }

    override fun release() {
        try {
            process.close()
        } finally {
            compiled.close()
        }
    }
}
