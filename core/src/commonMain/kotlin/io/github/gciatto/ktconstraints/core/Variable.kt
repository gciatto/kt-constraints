package io.github.gciatto.ktconstraints.core

/** Symbolic variable belonging to one modeling scope. */
interface Variable : Expr {
    /** Semantic variable identity; independent of [name]. */
    val id: VariableId

    /** Human-friendly variable name. */
    val name: String

    override val variables: Set<Variable> get() = setOf(this)
}
