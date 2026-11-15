package io.github.gciatto.ktconstraints.solve.z3

import io.github.gciatto.ktconstraints.solve.CheckStatus

/** Raw outcome of one native check; [reason] explains an unknown result. */
internal data class Z3Outcome(
    val status: CheckStatus,
    val reason: String = "",
)
