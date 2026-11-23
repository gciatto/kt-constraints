package io.github.gciatto.ktconstraints.solve.kotlin.cp

import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.core.integer.IntVar

/** Presolve provenance, not a standalone clause-learning explanation. */
internal data class DomainReduction(
    val variable: IntVar,
    val before: IntDomain,
    val after: IntDomain,
    val sourceAssertion: Int,
)
