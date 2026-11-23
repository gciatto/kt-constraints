package io.github.gciatto.ktconstraints.core.global.scheduling
import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.bool.BoolAnd
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.bool.BoolToInt
import io.github.gciatto.ktconstraints.core.global.DecomposableConstraint
import io.github.gciatto.ktconstraints.core.global.Decompositions
import io.github.gciatto.ktconstraints.core.integer.IntExpr
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.arithmetic.LinearSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import org.gciatto.kt.math.BigInteger

/**
 * Global constraint: item `i` goes into the zero-based bin `bins[i]`, which must exist, and each `loads[b]` equals the
 * total size of the items in bin `b`.
 */
class BinPacking(
    bins: Collection<IntExpr>,
    sizes: Collection<BigInteger>,
    loads: Collection<IntExpr>,
) : DecomposableConstraint {
    /** Detached immutable bin of each item. */
    val bins: List<IntExpr> = buildList { addAll(bins) }

    /** Detached immutable non-negative size of each item. */
    val sizes: List<BigInteger> = buildList { addAll(sizes) }

    /** Detached immutable load of each bin. */
    val loads: List<IntExpr> = buildList { addAll(loads) }

    init {
        require(this.bins.size == this.sizes.size) { "BinPacking requires one size per item" }
        require(this.sizes.all { it.signum >= 0 }) { "BinPacking sizes must be non-negative" }
    }

    override val children: List<Expr> get() = bins + loads

    override val decomposition: BoolExpr by lazy {
        val items = this.bins
        BoolAnd.of(
            items.map { Decompositions.inRange(it, this.loads.size) } +
                this.loads.mapIndexed {
                    bin,
                    load,
                    ->
                    IntEquality(load, LinearSum(items.map { BoolToInt(IntEquality(it, IntLiteral(bin))) }, this.sizes))
                },
        )
    }

    override fun equals(other: Any?): Boolean = other is BinPacking && bins == other.bins && sizes == other.sizes && loads == other.loads

    override fun hashCode(): Int = (31 * bins.hashCode() + sizes.hashCode()) * 31 + loads.hashCode()

    override fun toString(): String = "binPacking($bins, $sizes, $loads)"
}
