package io.github.gciatto.ktconstraints.solve.kotlin.cp

import io.github.gciatto.ktconstraints.core.IntDomain
import io.github.gciatto.ktconstraints.core.IntVar

/** Exact interval-domain store with change-proportional restoration and detached snapshots. */
internal class ReversibleIntDomains(
    variables: Collection<IntVar>,
) {
    private val owner = Any()
    private val domains = variables.associateWith { it.domain }.toMutableMap()
    private val trail = mutableListOf<DomainReduction>()
    private val checkpoints = mutableListOf<DomainCheckpoint>()

    /** Current restrictions, in source declaration order. */
    fun snapshot(): Map<IntVar, IntDomain> = buildMap { putAll(domains) }

    /** Active change history, detached from later restrictions and restoration. */
    val reductions: List<DomainReduction> get() = buildList { addAll(trail) }

    /** Returns the current domain of a declared variable. */
    operator fun get(variable: IntVar): IntDomain = requireNotNull(domains[variable]) { "Variable belongs to another domain store" }

    /** Restricts the current domain by intersection, returning whether anything changed. */
    fun narrow(
        variable: IntVar,
        allowed: IntDomain,
        sourceAssertion: Int,
    ): Boolean {
        val before = this[variable]
        val after = before.intersect(allowed)
        if (before.intervals == after.intervals) return false
        trail += DomainReduction(variable, before, after, sourceAssertion)
        domains[variable] = after
        return true
    }

    /** Saves a store-owned point, without copying any domains. */
    fun push(): DomainCheckpoint = DomainCheckpoint(owner, trail.size).also { checkpoints += it }

    /** Restores one active point; stale/foreign points fail before any mutation. */
    fun popTo(checkpoint: DomainCheckpoint) {
        require(checkpoint.owner === owner) { "Checkpoint belongs to another domain store" }
        val index = checkpoints.indexOf(checkpoint)
        require(index >= 0) { "Checkpoint is no longer active" }
        while (trail.size > checkpoint.trailSize) {
            val change = trail.removeAt(trail.lastIndex)
            domains[change.variable] = change.before
        }
        while (checkpoints.size > index) checkpoints.removeAt(checkpoints.lastIndex)
    }
}
