package io.github.gciatto.ktconstraints.solve.spi

/** What [ModelFeatures] learns about one subexpression: whether it mentions a variable or contains a relation. */
internal data class NodeFacts(
    val variable: Boolean,
    val relation: Boolean,
)
