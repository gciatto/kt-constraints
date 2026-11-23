package io.github.gciatto.ktconstraints.solve.kotlin.compiler.linear
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.solve.kotlin.arithmetic.LinearInequality

/** A reified `form <= 0` or `form < 0`; its complement is exact, including at the boundary. */
internal class RealAtom(
    val literal: Int,
    val form: LinearForm,
    val strict: Boolean,
) {
    fun inequality(values: BooleanArray): LinearInequality {
        val positive = values[literal]
        val direction = if (positive) Rational.ONE else Rational.of(-1)
        return LinearInequality(
            form.realCoefficients.mapValues { it.value * direction },
            -(form.constantUnder(values) * direction),
            if (positive) strict else !strict,
        )
    }
}
