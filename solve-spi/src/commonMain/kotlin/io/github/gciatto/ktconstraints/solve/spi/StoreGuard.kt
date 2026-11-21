package io.github.gciatto.ktconstraints.solve.spi

import io.github.gciatto.ktconstraints.core.Variable

/** Variable ownership and closing rules shared by constraint stores; confine each guard to its store. */
class StoreGuard {
    private val live = hashSetOf<Variable>()

    /** Whether the store was closed. */
    var isClosed: Boolean = false
        private set

    /** Fails with [IllegalStateException] once the store is closed. */
    fun requireOpen() = check(!isClosed) { "Store is closed" }

    /** Fails unless the store is open and every one of [variables] is currently declared in it. */
    fun requireOwned(variables: Collection<Variable>) {
        requireOpen()
        val foreign = variables.filterNot { it in live }
        require(foreign.isEmpty()) { "Variables not declared in this store: ${foreign.joinToString { it.name }}" }
    }

    /** Records [variable] as declared. */
    fun adopt(variable: Variable) {
        live += variable
    }

    /** Forgets [variables], as when undoing the frame that declared them. */
    fun release(variables: Collection<Variable>) {
        live -= variables.toSet()
    }

    /** Marks the store closed, returning whether this call did so. */
    fun close(): Boolean = !isClosed.also { isClosed = true }
}
