package io.github.gciatto.ktconstraints.core

import org.gciatto.kt.math.BigInteger

/** Exact mathematical integer literal. Construction never narrows its value. */
data class IntLiteral(
    /** Exact constant value. */
    val value: BigInteger,
) : IntExpr {
    /** Creates an exact literal from a machine integer. */
    constructor(value: Long) : this(BigInteger.of(value))

    /** Creates an exact literal from a machine integer. */
    constructor(value: Int) : this(BigInteger.of(value))

    /** Parses a signed decimal integer without floating-point conversion. */
    constructor(value: String) : this(BigInteger.of(value))

    override val variables: Set<Variable> get() = emptySet()

    override fun toString(): String = value.toString()
}
