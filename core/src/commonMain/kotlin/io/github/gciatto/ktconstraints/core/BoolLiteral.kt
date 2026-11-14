package io.github.gciatto.ktconstraints.core

/** Classical Boolean constant. */
data class BoolLiteral(
    val value: Boolean,
) : BoolExpr {
    override fun toString(): String = value.toString()
}
