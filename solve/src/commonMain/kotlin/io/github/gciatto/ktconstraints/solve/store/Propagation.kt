package io.github.gciatto.ktconstraints.solve.store
import io.github.gciatto.ktconstraints.core.Variable

/** Outcome of [ConstraintStore.constrain]: the store status and the variables whose domains shrank, in engine order. */
class Propagation(
    /** What the store established. */
    val status: StoreStatus,
    changed: List<Variable> = emptyList(),
) {
    /** Variables whose domains shrank; empty for stores that do not propagate, and on failure. */
    val changed: List<Variable> = buildList { addAll(changed) }

    init {
        require(status != StoreStatus.FAILED || changed.isEmpty()) { "A failed store reports no domain changes" }
    }

    override fun toString(): String = "Propagation($status, ${changed.map { it.name }})"
}
