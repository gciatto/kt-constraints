package io.github.gciatto.ktconstraints.solve.auto.examples.howto

import io.github.gciatto.ktconstraints.solve.incremental.CheckStatus

/** The outcome of each check in [checkTemporaryAssertions]. */
internal data class SessionChecks(
    val withTrialAssertion: CheckStatus,
    val withAssumption: CheckStatus,
    val afterRollback: CheckStatus,
)
