package io.github.gciatto.ktconstraints.core.integer.arithmetic
import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.integer.IntExpr
import org.gciatto.kt.math.BigInteger

/** Exact weighted sum plus a constant; preserves its terms for backend lowering. */
class LinearSum(
    terms: Collection<IntExpr>,
    coefficients: Collection<BigInteger>,
    /** Exact additive constant. */
    val constant: BigInteger = BigInteger.ZERO,
) : IntExpr {
    /** Detached immutable terms; terms may themselves need nonlinear backend support. */
    val terms: List<IntExpr> = buildList { addAll(terms) }

    /** Exact coefficient for each corresponding term. */
    val coefficients: List<BigInteger> = buildList { addAll(coefficients) }

    init {
        require(this.terms.size == this.coefficients.size) { "Expected one coefficient per term" }
    }

    override val children: List<Expr> get() = terms

    override fun equals(other: Any?): Boolean =
        other is LinearSum && terms == other.terms && coefficients == other.coefficients && constant == other.constant

    override fun hashCode(): Int = 31 * (31 * terms.hashCode() + coefficients.hashCode()) + constant.hashCode()

    override fun toString(): String = "linearSum($terms, $coefficients, $constant)"
}
