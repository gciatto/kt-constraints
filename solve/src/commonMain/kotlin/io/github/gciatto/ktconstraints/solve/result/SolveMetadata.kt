package io.github.gciatto.ktconstraints.solve.result

/** Immutable runtime diagnostics observed by a backend. Statistics retain lossless raw JSON values. */
class SolveMetadata(
    messages: Collection<String> = emptyList(),
    statistics: Map<String, String> = emptyMap(),
) {
    /** Runtime warnings, informational messages and stderr diagnostics. */
    val messages: List<String> = buildList { addAll(messages) }

    /** Backend-defined statistic names mapped to their original JSON lexemes. */
    val statistics: Map<String, String> = buildMap { putAll(statistics) }
}
