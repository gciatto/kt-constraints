package io.github.gciatto.ktconstraints.core

/** Sum of two integer expressions. */
data class IntSum(
    val left: IntExpr,
    val right: IntExpr,
) : IntExpr {
    override val variables: Set<Variable> get() = left.variables + right.variables
}

/** Symbolic integer equality, *not* Kotlin object identity. */
data class IntEquality(
    val left: IntExpr,
    val right: IntExpr,
) : BoolExpr {
    override val variables: Set<Variable> get() = left.variables + right.variables
}

/** Logical conjunction. */
data class BoolAnd(
    val left: BoolExpr,
    val right: BoolExpr,
) : BoolExpr {
    override val variables: Set<Variable> get() = left.variables + right.variables
}
