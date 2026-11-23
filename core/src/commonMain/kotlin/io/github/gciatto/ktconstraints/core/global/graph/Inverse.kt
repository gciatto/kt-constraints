package io.github.gciatto.ktconstraints.core.global.graph

import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.bool.BoolAnd
import io.github.gciatto.ktconstraints.core.bool.BoolEquality
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.global.DecomposableConstraint
import io.github.gciatto.ktconstraints.core.global.Decompositions
import io.github.gciatto.ktconstraints.core.integer.IntExpr
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality

/**
 * Global constraint: [direct] and [inverse] are mutually inverse zero-based permutations, i.e. both range over
 * `0 until n` and `direct[i] = j ⇔ inverse[j] = i`.
 */
class Inverse(
    direct: Collection<IntExpr>,
    inverse: Collection<IntExpr>,
) : DecomposableConstraint {
    /** Detached immutable forward permutation. */
    val direct: List<IntExpr> = buildList { addAll(direct) }

    /** Detached immutable backward permutation. */
    val inverse: List<IntExpr> = buildList { addAll(inverse) }

    init {
        require(this.direct.size == this.inverse.size) { "Inverse requires arrays of equal length" }
    }

    override val children: List<Expr> get() = direct + inverse

    override val decomposition: BoolExpr by lazy {
        val n = this.direct.size
        val ranges = (this.direct + this.inverse).map { Decompositions.inRange(it, n) }
        val links =
            this.direct.indices.flatMap { i ->
                this.inverse.indices.map { j ->
                    BoolEquality(IntEquality(this.direct[i], IntLiteral(j)), IntEquality(this.inverse[j], IntLiteral(i)))
                }
            }
        BoolAnd.of(ranges + links)
    }

    override fun equals(other: Any?): Boolean = other is Inverse && direct == other.direct && inverse == other.inverse

    override fun hashCode(): Int = 31 * direct.hashCode() + inverse.hashCode()

    override fun toString(): String = "inverse($direct, $inverse)"
}
