package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.solve.SolutionEvent
import io.github.gciatto.ktconstraints.solve.SolutionFound
import io.github.gciatto.ktconstraints.solve.SolveCompleted
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.BackendExecutionException
import io.github.gciatto.ktconstraints.solve.spi.ManagedSolutionStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class MiniZincJsStream(
    private val compiled: MiniZincModel,
    descriptor: SolverDescriptor,
    private val configurationId: String,
    private val options: SolveOptions,
    onClosed: () -> Unit,
) : ManagedSolutionStream(descriptor, onClosed) {
    private val decoder = MiniZincDecoder(compiled, descriptor)

    // shortcut: upstream callbacks cannot pause; add backpressure when its event API supports it.
    private val events = Channel<SolutionEvent>(Channel.UNLIMITED)
    private var progress: dynamic = null
    private var emitted = 0
    private var deadline: Job? = null
    private val listeners = mutableMapOf<String, (dynamic) -> Unit>()

    override suspend fun readNext(): SolutionEvent {
        val reason =
            when {
                options.timeoutMillis == 0L -> TerminationReason.TIMEOUT
                options.solutionLimit?.let { emitted >= it } == true -> TerminationReason.RESOURCE_LIMIT
                else -> null
            }
        if (reason != null) {
            stop(reason)
            return SolveCompleted(requireNotNull(terminalResult))
        }
        if (progress == null) start()
        val event = events.receive()
        if (event is SolutionFound) emitted++
        return event
    }

    private fun start() {
        val model = newMiniZincModel()
        model.addFile("model.mzn", compiled.source)
        val config = js("({jsonOutput:false, options:{solver:'org.gecode.gecode', 'all-solutions':true, statistics:true}})")
        config.options.solver = configurationId
        progress = model.solve(config)
        for (type in listOf("solution", "status", "statistics", "warning", "error", "trace", "time", "exit")) {
            val listener: (dynamic) -> Unit = { message ->
                try {
                    val event =
                        if (type == "exit") {
                            val code = message.code as Int? ?: throw BackendExecutionException("MiniZinc JS exited without a code")
                            SolveCompleted(decoder.finish(code, message.message as String? ?: ""))
                        } else {
                            decoder.accept(JSON.stringify(message))
                        }
                    if (event != null) events.trySend(event).getOrThrow()
                } catch (failure: BackendExecutionException) {
                    fail(failure)
                } catch (failure: IllegalStateException) {
                    fail(failure)
                }
            }
            listeners[type] = listener
            progress.on(type, listener)
        }
        progress.then({ _: dynamic -> Unit }, { failure: dynamic ->
            events.close(BackendExecutionException("MiniZinc JS request failed: $failure"))
            Unit
        })
        options.timeoutMillis?.let { millis ->
            deadline =
                CoroutineScope(Dispatchers.Default).launch {
                    delay(millis)
                    stop(TerminationReason.TIMEOUT)
                }
        }
    }

    private fun fail(failure: Throwable) {
        events.close(failure)
        progress.cancel()
    }

    override fun release() {
        deadline?.cancel()
        if (progress != null) {
            listeners.forEach { (type, listener) -> progress.off(type, listener) }
            listeners.clear()
            progress.cancel()
        }
        events.close(BackendExecutionException("MiniZinc JS stream is closed"))
        while (true) {
            if (events.tryReceive().isFailure) break
        }
        compiled.close()
    }
}
