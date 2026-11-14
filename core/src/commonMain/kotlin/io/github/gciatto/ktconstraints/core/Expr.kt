package io.github.gciatto.ktconstraints.core

/** Immutable symbolic expression, independent of any backend. */
interface Expr {
    /** Sort of this expression. */
    val sort: Sort

    /** Immediate operands, in semantic order; extension nodes must expose their operands here. */
    val children: List<Expr> get() = emptyList()

    /** Free symbolic variables occurring in this expression. */
    val variables: Set<Variable> get() = children.flatMap { it.variables }.toSet()
}

/** Boolean expression, directly usable as a constraint. */
interface BoolExpr : Expr {
    override val sort: Sort get() = Sort.Bool
}

/** Mathematical integer expression. */
interface IntExpr : Expr {
    override val sort: Sort get() = Sort.Integer
}
