package io.github.gciatto.ktconstraints.interop

import io.github.gciatto.ktconstraints.core.Rational
import org.gciatto.kt.math.BigInteger
import org.gciatto.kt.math.toKotlin
import java.math.BigDecimal

/**
 * Exact conversion of `java.math.BigDecimal` into the [Rational]s used by models.
 * For integers, use kt-math's own converters: `JvmUtils.toKotlin(java.math.BigInteger)` and `JvmUtils.toJava(BigInteger)`.
 */
object JavaNumbers {
    /** Converts a decimal exactly: unscaled value over a power of ten. */
    @JvmStatic
    fun rational(value: BigDecimal): Rational {
        val unscaled = value.unscaledValue().toKotlin()
        val power = BigInteger.TEN.pow(kotlin.math.abs(value.scale()))
        return if (value.scale() >= 0) Rational.of(unscaled, power) else Rational.of(unscaled * power)
    }
}
