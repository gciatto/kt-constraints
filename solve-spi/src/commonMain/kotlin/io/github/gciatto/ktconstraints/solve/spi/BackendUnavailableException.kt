package io.github.gciatto.ktconstraints.solve.spi

/** A requested execution route cannot start its runtime; [cause] keeps the runtime's own failure. */
class BackendUnavailableException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
