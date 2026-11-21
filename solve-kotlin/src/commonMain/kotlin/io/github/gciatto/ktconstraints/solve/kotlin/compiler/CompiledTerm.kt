package io.github.gciatto.ktconstraints.solve.kotlin.compiler

/** One typed source node's compiled value and the literal ensuring all partial subexpressions are defined. */
internal class CompiledTerm(
    val value: Any,
    val defined: Int,
)
