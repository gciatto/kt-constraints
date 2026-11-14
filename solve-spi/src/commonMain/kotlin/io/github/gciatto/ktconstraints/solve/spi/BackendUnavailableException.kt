package io.github.gciatto.ktconstraints.solve.spi

/** A requested execution route cannot start its runtime. */
class BackendUnavailableException(
    message: String,
) : IllegalStateException(message)
