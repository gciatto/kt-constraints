package io.github.gciatto.ktconstraints.core

/** An expression is undefined for the supplied values, such as an out-of-range element index. */
class UndefinedExpressionException(
    message: String,
) : IllegalArgumentException(message)
