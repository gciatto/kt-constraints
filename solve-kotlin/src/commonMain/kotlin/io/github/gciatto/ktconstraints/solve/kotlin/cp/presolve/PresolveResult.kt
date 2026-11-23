package io.github.gciatto.ktconstraints.solve.kotlin.cp.presolve
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.solve.kotlin.cp.DomainReduction

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
