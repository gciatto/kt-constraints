package io.github.gciatto.ktconstraints.solve.spi

/** Engine, transport or protocol failure; never evidence of model infeasibility. */
class BackendExecutionException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
