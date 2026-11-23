package io.github.gciatto.ktconstraints.core.global.graph

import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.VariableFactory
import io.github.gciatto.ktconstraints.core.bool.BoolAnd
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.bool.BoolImplies
import io.github.gciatto.ktconstraints.core.bool.BoolNot
import io.github.gciatto.ktconstraints.core.bool.BoolOr
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.core.global.DecomposableConstraint
import io.github.gciatto.ktconstraints.core.global.Decompositions
import io.github.gciatto.ktconstraints.core.global.counting.AllDifferent
import io.github.gciatto.ktconstraints.core.global.counting.BoolCardinality
import io.github.gciatto.ktconstraints.core.global.counting.CardinalityRelation
import io.github.gciatto.ktconstraints.core.global.extensional.Element
import io.github.gciatto.ktconstraints.core.integer.IntExpr
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality

/**
 * Global constraint on zero-based [successors]: a node pointing to itself is excluded, and the remaining nodes, if
 * any, form a single cycle.
 */
class SubCircuit(
    successors: Collection<IntExpr>,
) : DecomposableConstraint {
    /** Detached immutable successor of each node. */
    val successors: List<IntExpr> = buildList { addAll(successors) }

    override val children: List<Expr> get() = successors

    /**
     * Range and distinctness make the successors a permutation; every included node must reach every other included
     * node within `n - 1` steps of clamped element lookups.
     */
    override val decomposition: BoolExpr by lazy {
        val next = this.successors
        val n = next.size
        val included = next.indices.map { BoolNot(IntEquality(next[it], IntLiteral(it))) }
        val reach =
            next.indices.flatMap { i ->
                val walk = generateSequence(next[i]) { Element(Decompositions.clamp(it, n), next) }.take(n - 1).toList()
                next.indices.filter { it != i }.map { j ->
                    BoolImplies(
                        BoolAnd(included[i], included[j]),
                        walk.map<IntExpr, BoolExpr> { IntEquality(it, IntLiteral(j)) }.reduce(::BoolOr),
                    )
                }
            }
        // shortcut: the reachability walks make this quintic in n; top-level routes use decompose() or a native form.
        BoolAnd.of(next.map { Decompositions.inRange(it, n) } + listOfNotNull(AllDifferent(next).takeIf { n > 1 }) + reach)
    }

    /** One hidden root among the included nodes; every arc into a non-root node increments a hidden position. */
    override fun decompose(auxiliaries: VariableFactory): BoolExpr {
        val n = successors.size
        if (n <= 1) return decomposition
        val included = successors.indices.map { BoolNot(IntEquality(successors[it], IntLiteral(it))) }
        val roots = successors.indices.map { auxiliaries.boolVar("subcircuit.root$it") }
        val position = successors.indices.map { auxiliaries.intVar("subcircuit.position$it", IntDomain.of(0L, n - 1L)) }
        val rooted =
            successors.indices.flatMap {
                listOf(
                    BoolImplies(roots[it], included[it]),
                    BoolImplies(included[it], BoolCardinality(roots, 1, CardinalityRelation.AT_LEAST)),
                )
            }
        val arcs =
            successors.indices.flatMap { i ->
                successors.indices.filter { it != i }.map { j ->
                    BoolImplies(
                        BoolAnd(IntEquality(successors[i], IntLiteral(j)), BoolNot(roots[j])),
                        IntEquality(position[j], IntSum(position[i], IntLiteral(1))),
                    )
                }
            }
        return BoolAnd.of(
            successors.map { Decompositions.inRange(it, n) } + AllDifferent(successors) +
                BoolCardinality(roots, 1, CardinalityRelation.AT_MOST) +
                rooted + arcs,
        )
    }

    override fun equals(other: Any?): Boolean = other is SubCircuit && successors == other.successors

    override fun hashCode(): Int = 19 * successors.hashCode() + 2

    override fun toString(): String = "subCircuit($successors)"
}
