package io.github.gciatto.ktconstraints.solve.kotlin.cp.presolve
import io.github.gciatto.ktconstraints.core.integer.IntVar
import org.gciatto.kt.math.BigInteger

/** Exact affine expression with zero coefficients removed. */
internal class AffineForm(
    val coefficients: Map<IntVar, BigInteger> = emptyMap(),
    val constant: BigInteger = BigInteger.ZERO,
) {
    fun plus(other: AffineForm): AffineForm {
        val combined = coefficients.toMutableMap()
        for ((variable, value) in other.coefficients) {
            val sum = (combined[variable] ?: BigInteger.ZERO) + value
            if (sum.signum == 0) combined.remove(variable) else combined[variable] = sum
        }
        return AffineForm(combined, constant + other.constant)
    }

    fun scaled(factor: BigInteger): AffineForm =
        if (factor.signum == 0) {
            AffineForm()
        } else {
            AffineForm(coefficients.mapValues { (_, value) -> value * factor }, constant * factor)
        }
}
