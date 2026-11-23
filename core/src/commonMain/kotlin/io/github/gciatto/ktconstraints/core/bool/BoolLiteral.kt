package io.github.gciatto.ktconstraints.core.bool

/** Classical Boolean constant. */
data class BoolLiteral(
    /** Exact constant value. */
    val value: Boolean,
) : BoolExpr {
    override fun toString(): String = value.toString()
}
