package io.github.gciatto.ktconstraints.core.global

import io.github.gciatto.ktconstraints.core.VariableFactory
import io.github.gciatto.ktconstraints.core.bool.BoolExpr

/**
 * A global constraint with an exact decomposition over primitive nodes and the same operands. Routes lower it
 * natively where they can and fall back to [decomposition] elsewhere, for instance below a disjunction.
 */
interface DecomposableConstraint : BoolExpr {
    /** An equivalent formula without this node; it introduces no variables, so it is exact in every context. */
    val decomposition: BoolExpr

    /**
     * A formula equivalent to this node at top level only: it may use hidden variables created from [auxiliaries],
     * read existentially, and is often much smaller than [decomposition]. Defaults to [decomposition].
     */
    fun decompose(auxiliaries: VariableFactory): BoolExpr = decomposition
}
