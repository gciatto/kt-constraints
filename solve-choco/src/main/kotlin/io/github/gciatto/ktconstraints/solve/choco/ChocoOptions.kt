package io.github.gciatto.ktconstraints.solve.choco

import io.github.gciatto.ktconstraints.solve.BackendOptions

/** Per-solve Choco tuning: `SolveOptions() + ChocoOptions(ChocoSearch.DOM_OVER_WDEG)`. */
data class ChocoOptions(
    /** Branching strategy over the model's variables, or null for Choco's default search. */
    val search: ChocoSearch? = null,
) : BackendOptions
