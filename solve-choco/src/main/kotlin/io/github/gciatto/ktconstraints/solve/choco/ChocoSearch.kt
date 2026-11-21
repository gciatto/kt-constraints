package io.github.gciatto.ktconstraints.solve.choco

import org.chocosolver.solver.search.strategy.Search
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy
import org.chocosolver.solver.variables.IntVar

/** Choco's built-in branching strategies over the model's variables; Choco's default search completes the rest. */
enum class ChocoSearch(
    internal val strategy: (Array<out IntVar>) -> AbstractStrategy<IntVar>,
) {
    /** Domain over weighted degree. */
    DOM_OVER_WDEG(Search::domOverWDegSearch),

    /** Refined domain over weighted degree. */
    DOM_OVER_WDEG_REF(Search::domOverWDegRefSearch),

    /** Activity-based search (with restarts). */
    ACTIVITY_BASED(Search::activityBasedSearch),

    /** Conflict-history search. */
    CONFLICT_HISTORY(Search::conflictHistorySearch),

    /** Failure-rate-based search. */
    FAILURE_RATE(Search::failureRateBasedSearch),

    /** Failure-length-based search. */
    FAILURE_LENGTH(Search::failureLengthBasedSearch),

    /** Smallest domain first, lower bound first. */
    MIN_DOMAIN_LOWER_BOUND(Search::minDomLBSearch),

    /** Smallest domain first, upper bound first. */
    MIN_DOMAIN_UPPER_BOUND(Search::minDomUBSearch),

    /** Declaration order, lower bound first. */
    INPUT_ORDER_LOWER_BOUND(Search::inputOrderLBSearch),

    /** Declaration order, upper bound first. */
    INPUT_ORDER_UPPER_BOUND(Search::inputOrderUBSearch),
}
