package io.github.gciatto.ktconstraints.solve.minizinc.runtime

import io.github.gciatto.ktconstraints.solve.minizinc.MiniZincEngine

/**
 * One MiniZinc driver run on [source], started lazily by the first [readLine]. It owns a temporary workspace, calls
 * [onTimeout] once [timeoutMillis] elapse, and calls [onCancel] when a blocked read is cancelled. Closing it kills the
 * driver with its solver descendants and deletes the workspace.
 */
internal expect class MiniZincProcess(
    executable: String,
    source: String,
    timeoutMillis: Long?,
    onTimeout: () -> Unit,
    onCancel: () -> Unit,
    solverArguments: List<String> = MiniZincEngine.GECODE.solverArguments(),
) : AutoCloseable {
    /** The temporary workspace, once started and until closed. */
    val directory: String?

    /** The driver's process ID, once started. */
    val processId: Long?

    /** The next line of the driver's standard output, or null at its end. */
    suspend fun readLine(): String?

    /** Waits for the driver to exit and returns its exit code. */
    suspend fun exitCode(): Int

    /** What the driver wrote to standard error so far. */
    fun stderr(): String

    override fun close()
}
