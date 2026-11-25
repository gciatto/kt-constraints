package io.github.gciatto.ktconstraints.solve.minizinc.runtime

import io.github.gciatto.ktconstraints.solve.minizinc.process.kmz_close_reader
import io.github.gciatto.ktconstraints.solve.minizinc.process.kmz_group_exists
import io.github.gciatto.ktconstraints.solve.minizinc.process.kmz_make_directory
import io.github.gciatto.ktconstraints.solve.minizinc.process.kmz_read
import io.github.gciatto.ktconstraints.solve.minizinc.process.kmz_remove_directory
import io.github.gciatto.ktconstraints.solve.minizinc.process.kmz_signal_group
import io.github.gciatto.ktconstraints.solve.minizinc.process.kmz_sleep
import io.github.gciatto.ktconstraints.solve.minizinc.process.kmz_spawn
import io.github.gciatto.ktconstraints.solve.minizinc.process.kmz_wait
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.ECHILD
import platform.posix.errno
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fwrite
import platform.posix.getenv
import platform.posix.remove
import platform.posix.strerror

private const val LINE_CHUNK = 4096
private const val POLL_MILLIS = 10
private const val TERMINATION_GRACE_MILLIS = 2_000L

/** A started child process: its ID, which also names its process group, and its piped standard output, if any. */
@OptIn(ExperimentalForeignApi::class)
internal class NativeChild(
    val pid: Int,
    val output: COpaquePointer?,
) {
    /** The next line of standard output without its newline, or null at its end. */
    fun readLine(): String? {
        val reader = requireNotNull(output) { "Standard output is not piped" }
        val bytes = ArrayList<ByteArray>()
        memScoped {
            val buffer = allocArray<ByteVar>(LINE_CHUNK)
            while (true) {
                val count = kmz_read(reader, buffer, LINE_CHUNK)
                if (count < 0) break
                val chunk = buffer.readBytes(count)
                if (chunk.isNotEmpty() && chunk.last() == '\n'.code.toByte()) {
                    bytes += chunk.copyOf(chunk.size - 1)
                    return join(bytes).decodeToString()
                }
                bytes += chunk
            }
        }
        return if (bytes.isEmpty()) null else join(bytes).decodeToString()
    }

    /** Closes the standard output pipe; no read may be in progress. */
    fun closeOutput() {
        output?.let { kmz_close_reader(it) }
    }

    /** Waits for the child to exit and returns its exit code. */
    fun waitFor(): Int = reap(block = true)?.takeIf { it != REAPED_ELSEWHERE } ?: error("MiniZinc process $pid was already reaped")

    /** The exit code, or [REAPED_ELSEWHERE], once the child exits within [millis]; otherwise null. */
    fun waitFor(millis: Long): Int? {
        var waited = 0L
        while (true) {
            reap(block = false)?.let { return it }
            if (waited >= millis) return null
            kmz_sleep(POLL_MILLIS)
            waited += POLL_MILLIS
        }
    }

    /** Whether any process of the child's group, including the solvers MiniZinc started, is still alive. */
    fun groupExists(): Boolean = kmz_group_exists(pid) != 0

    /**
     * Stops the whole process group: SIGTERM, a grace period, then SIGKILL, so that no solver descendant survives.
     * Reaps the child unless [exitCode] already did.
     */
    fun terminate(reaped: Boolean) {
        if (!reaped) {
            kmz_signal_group(pid, 0)
            if (waitFor(TERMINATION_GRACE_MILLIS) == null) {
                kmz_signal_group(pid, 1)
                reap(block = true)
            }
        }
        // shortcut: the group ID could be reused once every member is gone; a pidfd would rule that out on Linux.
        kmz_signal_group(pid, 1)
    }

    private fun reap(block: Boolean): Int? =
        memScoped {
            val code = alloc<IntVar>()
            when (kmz_wait(pid, if (block) 1 else 0, code.ptr)) {
                1 -> code.value
                0 -> null
                else -> if (errno == ECHILD) REAPED_ELSEWHERE else error("Cannot wait for MiniZinc process $pid: ${describe(errno)}")
            }
        }

    companion object {
        /** Reported when another thread reaped the child first. */
        const val REAPED_ELSEWHERE = -1

        /** Starts [arguments], sending standard error to [errorPath] and standard output to [outputPath] or a pipe. */
        fun start(
            arguments: List<String>,
            outputPath: String?,
            errorPath: String,
        ): NativeChild =
            memScoped {
                val argv = allocArrayOf(arguments.map { it.cstr.getPointer(this) } + null)
                val pid = alloc<IntVar>()
                val output = alloc<COpaquePointerVar>()
                val error = kmz_spawn(argv, outputPath, errorPath, pid.ptr, output.ptr)
                check(error == 0) { "Cannot start ${arguments.first()}: ${describe(error)}" }
                NativeChild(pid.value, output.value)
            }

        private fun join(chunks: List<ByteArray>): ByteArray {
            val result = ByteArray(chunks.sumOf { it.size })
            var offset = 0
            for (chunk in chunks) {
                chunk.copyInto(result, offset)
                offset += chunk.size
            }
            return result
        }
    }
}

/** Creates a private temporary directory whose name starts with [prefix]. */
@OptIn(ExperimentalForeignApi::class)
internal fun createWorkspace(prefix: String): String {
    val root = getenv("TMPDIR")?.toKString()?.trimEnd('/')?.ifEmpty { null } ?: "/tmp"
    val template = "$root/${prefix}XXXXXX".encodeToByteArray() + 0
    template.usePinned { pinned ->
        val error = kmz_make_directory(pinned.addressOf(0))
        check(error == 0) { "Cannot create a MiniZinc workspace in $root: ${describe(error)}" }
    }
    return template.copyOf(template.size - 1).decodeToString()
}

/** Deletes [files] inside [directory], then the directory itself. */
@OptIn(ExperimentalForeignApi::class)
internal fun deleteWorkspace(
    directory: String,
    vararg files: String,
) {
    files.forEach { remove("$directory/$it") }
    val error = kmz_remove_directory(directory)
    check(error == 0) { "Cannot delete MiniZinc workspace $directory: ${describe(error)}" }
}

/** Writes [text] to [path] as UTF-8. */
@OptIn(ExperimentalForeignApi::class)
internal fun writeText(
    path: String,
    text: String,
) {
    val file = fopen(path, "wb") ?: error("Cannot write $path: ${describe(errno)}")
    try {
        val bytes = text.encodeToByteArray()
        if (bytes.isNotEmpty()) {
            val written = bytes.usePinned { fwrite(it.addressOf(0), 1.convert(), bytes.size.convert(), file) }
            check(written.toLong() == bytes.size.toLong()) { "Cannot write $path" }
        }
    } finally {
        fclose(file)
    }
}

/** The UTF-8 content of [path], or an empty string when it cannot be read. */
@OptIn(ExperimentalForeignApi::class)
internal fun readText(path: String): String {
    val file = fopen(path, "rb") ?: return ""
    try {
        val chunks = ArrayList<ByteArray>()
        val buffer = ByteArray(LINE_CHUNK)
        while (true) {
            val count = buffer.usePinned { fread(it.addressOf(0), 1.convert(), LINE_CHUNK.convert(), file) }.toInt()
            if (count <= 0) break
            chunks += buffer.copyOf(count)
        }
        return chunks.fold(ByteArray(0)) { all, chunk -> all + chunk }.decodeToString()
    } finally {
        fclose(file)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun describe(error: Int): String = strerror(error)?.toKString() ?: "error $error"
