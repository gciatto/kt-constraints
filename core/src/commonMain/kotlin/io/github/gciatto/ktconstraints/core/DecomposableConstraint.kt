package io.github.gciatto.ktconstraints.core

/**
 * A global constraint with an exact decomposition over primitive nodes and the same operands. Routes lower it
 * natively where they can and fall back to [decomposition] elsewhere, for instance below a disjunction.
 */
interface DecomposableConstraint : BoolExpr {
    /** An equivalent formula without this node; it may introduce no variables. */
    val decomposition: BoolExpr
}
