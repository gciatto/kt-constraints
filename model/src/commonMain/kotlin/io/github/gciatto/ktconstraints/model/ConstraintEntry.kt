package io.github.gciatto.ktconstraints.model

import io.github.gciatto.ktconstraints.core.BoolExpr

/** Immutable assertion and its optional diagnostic metadata. */
class ConstraintEntry(
    val expression: BoolExpr,
    val name: String? = null,
    annotations: Map<String, String> = emptyMap(),
    val source: SourceLocation? = null,
) {
    /** Detached immutable semantic tags, ordered by key. */
    val annotations: Map<String, String> =
        buildMap {
            annotations.entries.sortedBy { it.key }.forEach { put(it.key, it.value) }
        }

    init {
        require(name == null || name.isNotBlank()) { "Constraint name must not be blank" }
    }

    override fun equals(other: Any?): Boolean =
        other is ConstraintEntry &&
            expression == other.expression &&
            name == other.name &&
            annotations == other.annotations &&
            source == other.source

    override fun hashCode(): Int =
        31 * (31 * (31 * expression.hashCode() + (name?.hashCode() ?: 0)) + annotations.hashCode()) + (source?.hashCode() ?: 0)

    override fun toString(): String = "constraint($name, $expression, $annotations, $source)"
}
