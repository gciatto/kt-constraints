package io.github.gciatto.ktconstraints.solve.z3.port

/** Owns one lazily created [Z3Port], serializes access to it and interrupts it on close or cancellation. */
internal interface Z3Owner<T> : AutoCloseable {
    /** Whether [close] has been called. */
    val isClosed: Boolean

    /** Runs [block] with exclusive port access; coroutine cancellation interrupts an active native check. */
    suspend fun <R> confined(block: suspend (Z3Port<T>) -> R): R

    /** Runs a short non-checking [block] with exclusive port access, without suspending. */
    fun <R> confinedNow(block: (Z3Port<T>) -> R): R

    /** Interrupts any active check and releases the port; idempotent. */
    override fun close()
}
