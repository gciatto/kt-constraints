package io.github.gciatto.ktconstraints.solve.kotlin.compiler.linear
import io.github.gciatto.ktconstraints.core.Rational

/** Exact real affine form with Boolean-valued coefficients supplied by the discrete search. */
internal class LinearForm(
    realCoefficients: Map<Int, Rational> = emptyMap(),
    bitCoefficients: Map<Int, Rational> = emptyMap(),
    val constant: Rational = Rational.ZERO,
) {
    val realCoefficients: Map<Int, Rational> = realCoefficients.filterValues { it != Rational.ZERO }
    val bitCoefficients: Map<Int, Rational> = bitCoefficients.filterValues { it != Rational.ZERO }

    operator fun plus(other: LinearForm): LinearForm =
        LinearForm(
            combine(realCoefficients, other.realCoefficients),
            combine(bitCoefficients, other.bitCoefficients),
            constant + other.constant,
        )

    fun scale(coefficient: Rational): LinearForm =
        LinearForm(
            realCoefficients.mapValues { it.value * coefficient },
            bitCoefficients.mapValues { it.value * coefficient },
            constant * coefficient,
        )

    fun constantUnder(values: BooleanArray): Rational =
        bitCoefficients.entries.fold(constant) { sum, (variable, coefficient) -> if (values[variable]) sum + coefficient else sum }

    private fun combine(
        left: Map<Int, Rational>,
        right: Map<Int, Rational>,
    ): Map<Int, Rational> =
        left.toMutableMap().apply {
            right.forEach { (variable, coefficient) -> this[variable] = (this[variable] ?: Rational.ZERO) + coefficient }
        }
}
