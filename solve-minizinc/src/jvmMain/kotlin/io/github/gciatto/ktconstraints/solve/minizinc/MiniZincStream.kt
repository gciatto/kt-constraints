package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.solve.SolutionEvent
import io.github.gciatto.ktconstraints.solve.SolveCompleted
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.ManagedSolutionStream
import java.nio.file.Path

internal class MiniZincStream(
    executable: String,
    private val compiled: MiniZincModel,
    private val options: SolveOptions,
    onClosed: () -> Unit,
) : ManagedSolutionStream(gecodeDescriptor, onClosed) {
    private val decoder = MiniZincDecoder(compiled)
    private val process =
        MiniZincProcess(
            executable,
            compiled.source,
            options.timeoutMillis,
            onTimeout = { stop(TerminationReason.TIMEOUT) },
            onCancel = { cancel() },
        )
    private var emitted = 0
    internal val workDirectory: Path? get() = process.directory
    internal val processId: Long? get() = process.processId

    override suspend fun readNext(): SolutionEvent {
        if (options.timeoutMillis == 0L) {
            stop(TerminationReason.TIMEOUT)
            return SolveCompleted(requireNotNull(terminalResult))
        }
        if (options.solutionLimit?.let { emitted >= it } == true) {
            stop(TerminationReason.RESOURCE_LIMIT)
            return SolveCompleted(requireNotNull(terminalResult))
        }
        while (true) {
            val line = process.readLine()
            if (line == null) return SolveCompleted(decoder.finish(process.exitCode(), process.stderr()))
            val event = decoder.accept(line) ?: continue
            emitted++
            return event
        }
    }

    override fun release() {
        try {
            process.close()
        } finally {
            compiled.close()
        }
    }
}
