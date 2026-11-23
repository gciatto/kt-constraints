package io.github.gciatto.ktconstraints.solve.kotlin.cp.presolve
import io.github.gciatto.ktconstraints.core.evaluation.fold
import io.github.gciatto.ktconstraints.core.integer.IntExpr
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntDifference
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntNegation
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntProduct
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntSum
import io.github.gciatto.ktconstraints.core.integer.arithmetic.LinearSum
import io.github.gciatto.ktconstraints.solve.kotlin.cp.ReversibleIntDomains
import org.gciatto.kt.math.BigInteger

/** Recognises affine expressions under the store's current exact singleton values. */
internal class AffineNormalizer(
    private val domains: ReversibleIntDomains,
) {
    fun normalize(expression: IntExpr): AffineForm? =
        expression.fold { node, values: List<AffineForm?> ->
            when (node) {
                is IntLiteral -> AffineForm(constant = node.value)
                is IntVar -> variable(node)
                is IntSum -> combine(values, BigInteger.ONE)
                is IntDifference -> combine(values, -BigInteger.ONE)
                is IntNegation -> values[0]?.scaled(-BigInteger.ONE)
                is IntProduct -> product(values)
                is LinearSum -> weighted(node, values)
                else -> null
            }
        }

    private fun variable(variable: IntVar): AffineForm {
        val domain = domains[variable]
        val fixed = domain.min?.takeIf { it == domain.max }
        return if (fixed == null) AffineForm(mapOf(variable to BigInteger.ONE)) else AffineForm(constant = fixed)
    }

    private fun combine(
        values: List<AffineForm?>,
        sign: BigInteger,
    ): AffineForm? {
        val left = values[0] ?: return null
        val right = values[1] ?: return null
        return left.plus(right.scaled(sign))
    }

    private fun product(values: List<AffineForm?>): AffineForm? {
        val left = values[0] ?: return null
        val right = values[1] ?: return null
        return when {
            left.coefficients.isEmpty() -> right.scaled(left.constant)
            right.coefficients.isEmpty() -> left.scaled(right.constant)
            else -> null
        }
    }

    private fun weighted(
        expression: LinearSum,
        values: List<AffineForm?>,
    ): AffineForm? {
        var result = AffineForm(constant = expression.constant)
        for (index in values.indices) {
            val value = values[index] ?: return null
            result = result.plus(value.scaled(expression.coefficients[index]))
        }
        return result
    }
}
