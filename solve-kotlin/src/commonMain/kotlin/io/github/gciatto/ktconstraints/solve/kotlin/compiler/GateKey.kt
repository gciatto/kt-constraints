package io.github.gciatto.ktconstraints.solve.kotlin.compiler

/** Shallow canonical gate key; it never recursively hashes the source expression tree. */
internal data class GateKey(
    val operation: Int,
    val left: Int,
    val right: Int,
)
