package io.github.gciatto.ktconstraints.solve.store
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.core.integer.IntVar

/**
 * What a [ConstraintStore] still holds on some variables: their current domains and the posted constraints mentioning
 * them that are not known to be entailed. Sound, so every store solution satisfies it, but unless [exact] it may
 * mention other variables and be weaker than the true projection.
 */
class Residual(
    domains: Map<IntVar, IntDomain>,
    constraints: List<BoolExpr>,
    /** Whether this is the exact projection onto the requested variables. */
    val exact: Boolean,
) {
    /** Current domain of each requested integer variable. */
    val domains: Map<IntVar, IntDomain> = buildMap { putAll(domains) }

    /** Posted constraints mentioning a requested variable and not known to be entailed. */
    val constraints: List<BoolExpr> = buildList { addAll(constraints) }
}
