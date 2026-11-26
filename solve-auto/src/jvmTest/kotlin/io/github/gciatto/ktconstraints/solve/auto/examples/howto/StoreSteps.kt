package io.github.gciatto.ktconstraints.solve.auto.examples.howto

import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.solve.store.Entailment

/** The domain of y observed at each step of [restoreStoreDomains]. */
internal data class StoreSteps(
    val afterFixingX: IntDomain,
    val entailedThen: Entailment,
    val afterUndo: IntDomain,
    val inFork: IntDomain,
    val afterFork: IntDomain,
)
