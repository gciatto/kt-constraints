package io.github.gciatto.ktconstraints.solve

/** Runtime capabilities must be checked independently from platform availability. */
enum class SolverCapability {
    SATISFACTION,
    OPTIMIZATION,
    ENUMERATION,
    INCREMENTAL_ASSERTIONS,
    BACKTRACKING,
    DOMAIN_PROPAGATION,
    ENTAILMENT,
    ASSUMPTIONS,
    INTEGER_ARITHMETIC,
    REAL_ARITHMETIC,
    BITVECTORS,
    GLOBAL_CONSTRAINTS,
}

/** Identifies an engine and its route (direct API versus MiniZinc compilation). */
data class SolverDescriptor(
    val engine: String,
    val route: String,
    val capabilities: Set<SolverCapability>,
)
