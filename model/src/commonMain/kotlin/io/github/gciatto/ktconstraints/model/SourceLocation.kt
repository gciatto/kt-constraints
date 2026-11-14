package io.github.gciatto.ktconstraints.model

/** Optional source provenance with one-based line and column positions. */
data class SourceLocation(
    val file: String,
    val line: Int = 1,
    val column: Int = 1,
) {
    init {
        require(file.isNotBlank()) { "Source file must not be blank" }
        require(line > 0 && column > 0) { "Source positions must be positive" }
    }
}
