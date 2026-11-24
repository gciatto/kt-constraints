package io.github.gciatto.ktconstraints.solve

/**
 * Tuning for one backend, carried by [SolveOptions] (e.g. `SolveOptions() + CpSatOptions(workers = 8)`). Routes read
 * only their own type and ignore the rest, so one options value can tune every route of an automatic backend. Tuning never
 * changes a model's meaning; an option that would weaken a proof makes the route report weaker evidence instead.
 */
interface BackendOptions
