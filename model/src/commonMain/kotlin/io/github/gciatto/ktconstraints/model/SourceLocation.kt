package io.github.gciatto.ktconstraints.model

/** Optional source provenance with one-based line and column positions. */
data class SourceLocation(
    /** Non-blank source identifier; no filesystem access is implied. */
    val file: String,
    /** One-based source line. */
    val line: Int = 1,
    /** One-based source column. */
    val column: Int = 1,
) {
    init {
        require(file.isNotBlank()) { "Source file must not be blank" }
        require(line > 0 && column > 0) { "Source positions must be positive" }
    }
}
