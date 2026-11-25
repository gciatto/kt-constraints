package io.github.gciatto.ktconstraints.solve.minizinc.runtime

internal actual fun runMiniZinc(
    executable: String,
    arguments: List<String>,
    model: String?,
): String {
    val directory = createWorkspace("kt-constraints-minizinc-probe-")
    try {
        val modelArguments = model?.let { listOf("$directory/probe.mzn".also { path -> writeText(path, it) }) }.orEmpty()
        val child = NativeChild.start(listOf(executable) + arguments + modelArguments, "$directory/stdout.log", "$directory/stderr.log")
        var code: Int? = null
        try {
            code = child.waitFor(PROBE_TIMEOUT_MILLIS)
        } finally {
            child.terminate(reaped = code != null)
        }
        check(code != null) { "MiniZinc runtime probe timed out" }
        check(code == 0) { "MiniZinc runtime probe failed: ${readText("$directory/stderr.log")}" }
        return readText("$directory/stdout.log")
    } finally {
        deleteWorkspace(directory, "probe.mzn", "stdout.log", "stderr.log")
    }
}
