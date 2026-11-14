package io.github.gciatto.ktconstraints.solve.spi

/** Whole-model support assessment; lossless lowering is distinct from rejection. */
class SupportReport(
    unsupported: Collection<String> = emptyList(),
    losslessDecompositions: Collection<String> = emptyList(),
) {
    /** Unsupported constructs, numeric ranges or requested options; any entry rejects the model. */
    val unsupported: List<String> = buildList { addAll(unsupported) }

    /** Documented semantics-preserving lowerings required by this backend. */
    val losslessDecompositions: List<String> = buildList { addAll(losslessDecompositions) }

    /** Whether the entire model and requested options can be represented exactly. */
    val isSupported: Boolean get() = unsupported.isEmpty()
}
