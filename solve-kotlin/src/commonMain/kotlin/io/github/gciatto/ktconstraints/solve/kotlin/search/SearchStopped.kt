package io.github.gciatto.ktconstraints.solve.kotlin.search
import io.github.gciatto.ktconstraints.solve.result.TerminationReason

/** Internal control flow for a checked search limit; it must not be interpreted as a conflict. */
internal class SearchStopped(
    val reason: TerminationReason,
) : RuntimeException(reason.name)
