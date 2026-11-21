package io.github.gciatto.ktconstraints.solve.kotlin.cp

import io.github.gciatto.ktconstraints.core.IntDomain
import io.github.gciatto.ktconstraints.core.IntVar

/** Sound restrictions; a pass-budget stop leaves [fixedPoint] false and proves no conflict. */
internal class PresolveResult(
    domains: Map<IntVar, IntDomain>,
    val conflict: Boolean,
    val fixedPoint: Boolean,
    reductions: List<DomainReduction>,
) {
    val domains: Map<IntVar, IntDomain> = buildMap { putAll(domains) }
    val reductions: List<DomainReduction> = buildList { addAll(reductions) }
}
