package io.github.gciatto.ktconstraints.core

/** Classical Boolean constant. */
data class BoolLiteral(
    /** Exact constant value. */
    val value: Boolean,
) : BoolExpr {
    override fun toString(): String = value.toString()
}
