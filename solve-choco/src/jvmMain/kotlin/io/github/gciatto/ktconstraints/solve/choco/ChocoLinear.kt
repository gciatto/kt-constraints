package io.github.gciatto.ktconstraints.solve.choco

import org.chocosolver.solver.Model
import org.chocosolver.solver.constraints.Constraint
import org.chocosolver.solver.variables.IntVar
import org.gciatto.kt.math.BigInteger

/** Exact affine form `Σ coefficient · variable + constant` over Choco variables, merged by variable. */
internal data class ChocoLinear(
    val terms: Map<IntVar, BigInteger> = emptyMap(),
    val constant: BigInteger = BigInteger.ZERO,
) {
    operator fun plus(other: ChocoLinear): ChocoLinear {
        val merged = terms.toMutableMap()
        other.terms.forEach { (variable, coefficient) -> merged[variable] = (merged[variable] ?: BigInteger.ZERO) + coefficient }
        return ChocoLinear(merged.filterValues { it.signum != 0 }, constant + other.constant)
    }

    operator fun times(factor: BigInteger): ChocoLinear =
        ChocoLinear(terms.mapValues { it.value * factor }.filterValues { it.signum != 0 }, constant * factor)

    /** `this operator 0` in [model]; a form without variables is decided here, since Choco needs at least one term. */
    fun relation(
        model: Model,
        operator: String,
    ): Constraint {
        if (terms.isEmpty()) {
            val holds =
                when (operator) {
                    "=" -> constant.signum == 0
                    "<" -> constant.signum < 0
                    "<=" -> constant.signum <= 0
                    else -> constant.signum >= 0
                }
            return if (holds) model.trueConstraint() else model.falseConstraint()
        }
        val coefficients = terms.values.map { it.toIntExact() }.toIntArray()
        return model.scalar(terms.keys.toTypedArray(), coefficients, operator, (-constant).toIntExact())
    }

    companion object {
        fun of(variable: IntVar): ChocoLinear = ChocoLinear(mapOf(variable to BigInteger.ONE))

        fun of(constant: BigInteger): ChocoLinear = ChocoLinear(constant = constant)
    }
}
