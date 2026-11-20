package io.github.gciatto.ktconstraints.interop

import io.github.gciatto.ktconstraints.core.Rational
import org.gciatto.kt.math.BigInteger
import java.math.BigDecimal

/** Exact conversions between `java.math` numbers and the kt-math integers and [Rational]s used by models. */
object JavaNumbers {
    /** Converts a `java.math.BigInteger` exactly. */
    @JvmStatic
    fun integer(value: java.math.BigInteger): BigInteger = BigInteger.of(value.toString())

    /** Converts a model integer to `java.math.BigInteger` exactly. */
    @JvmStatic
    fun toJava(value: BigInteger): java.math.BigInteger = java.math.BigInteger(value.toString())

    /** Converts a decimal exactly: unscaled value over a power of ten. */
    @JvmStatic
    fun rational(value: BigDecimal): Rational {
        val unscaled = integer(value.unscaledValue())
        val power = BigInteger.TEN.pow(kotlin.math.abs(value.scale()))
        return if (value.scale() >= 0) Rational.of(unscaled, power) else Rational.of(unscaled * power)
    }
}
