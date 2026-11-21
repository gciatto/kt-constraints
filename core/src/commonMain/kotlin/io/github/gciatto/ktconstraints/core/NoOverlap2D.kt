package io.github.gciatto.ktconstraints.core

import org.gciatto.kt.math.BigInteger

/**
 * Global constraint: rectangles with corners at `(xs[i], ys[i])` and the positive constant [widths] and [heights]
 * pairwise do not overlap; they may touch.
 */
class NoOverlap2D(
    xs: Collection<IntExpr>,
    ys: Collection<IntExpr>,
    widths: Collection<BigInteger>,
    heights: Collection<BigInteger>,
) : DecomposableConstraint {
    /** Detached immutable horizontal origins. */
    val xs: List<IntExpr> = buildList { addAll(xs) }

    /** Detached immutable vertical origins. */
    val ys: List<IntExpr> = buildList { addAll(ys) }

    /** Detached immutable positive widths. */
    val widths: List<BigInteger> = buildList { addAll(widths) }

    /** Detached immutable positive heights. */
    val heights: List<BigInteger> = buildList { addAll(heights) }

    init {
        require(setOf(this.xs.size, this.ys.size, this.widths.size, this.heights.size).size == 1) {
            "NoOverlap2D requires one origin, width and height per rectangle"
        }
        require((this.widths + this.heights).all { it.signum > 0 }) { "NoOverlap2D sizes must be positive" }
    }

    override val children: List<Expr> get() = xs + ys

    /** Each pair is separated along at least one side. */
    override val decomposition: BoolExpr by lazy {
        fun before(
            origin: IntExpr,
            size: BigInteger,
            other: IntExpr,
        ): BoolExpr = IntLessOrEqual(IntSum(origin, IntLiteral(size)), other)
        val x = this.xs
        val y = this.ys
        val pairs = x.indices.flatMap { i -> (i + 1 until x.size).map { j -> i to j } }
        BoolAnd.of(
            pairs.map { (i, j) ->
                BoolOr(
                    BoolOr(before(x[i], this.widths[i], x[j]), before(x[j], this.widths[j], x[i])),
                    BoolOr(before(y[i], this.heights[i], y[j]), before(y[j], this.heights[j], y[i])),
                )
            },
        )
    }

    override fun equals(other: Any?): Boolean =
        other is NoOverlap2D && xs == other.xs && ys == other.ys && widths == other.widths && heights == other.heights

    override fun hashCode(): Int = ((31 * xs.hashCode() + ys.hashCode()) * 31 + widths.hashCode()) * 31 + heights.hashCode()

    override fun toString(): String = "noOverlap2D($xs, $ys, $widths, $heights)"
}
