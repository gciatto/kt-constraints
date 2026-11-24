package io.github.gciatto.ktconstraints.test.solve

import io.github.gciatto.ktconstraints.core.bool.BoolAnd
import io.github.gciatto.ktconstraints.core.bool.BoolEquality
import io.github.gciatto.ktconstraints.core.bool.BoolImplies
import io.github.gciatto.ktconstraints.core.bool.BoolLiteral
import io.github.gciatto.ktconstraints.core.bool.BoolNot
import io.github.gciatto.ktconstraints.core.bool.BoolOr
import io.github.gciatto.ktconstraints.core.bool.BoolToInt
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.core.domain.IntInterval
import io.github.gciatto.ktconstraints.core.global.counting.AllDifferent
import io.github.gciatto.ktconstraints.core.global.counting.BoolCardinality
import io.github.gciatto.ktconstraints.core.global.counting.CardinalityRelation
import io.github.gciatto.ktconstraints.core.global.extensional.Automaton
import io.github.gciatto.ktconstraints.core.global.extensional.Element
import io.github.gciatto.ktconstraints.core.global.extensional.Regular
import io.github.gciatto.ktconstraints.core.global.extensional.Table
import io.github.gciatto.ktconstraints.core.global.extensional.Transition
import io.github.gciatto.ktconstraints.core.global.graph.Circuit
import io.github.gciatto.ktconstraints.core.global.graph.Inverse
import io.github.gciatto.ktconstraints.core.global.graph.SubCircuit
import io.github.gciatto.ktconstraints.core.global.scheduling.BinPacking
import io.github.gciatto.ktconstraints.core.global.scheduling.Cumulative
import io.github.gciatto.ktconstraints.core.global.scheduling.NoOverlap2D
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntDifference
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntMax
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntMin
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntNegation
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntProduct
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntSum
import io.github.gciatto.ktconstraints.core.integer.arithmetic.LinearSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessThan
import io.github.gciatto.ktconstraints.model.ConstraintScope
import org.gciatto.kt.math.BigInteger

/** A small Boolean/integer model the [FiniteOracle] can enumerate, compared against a backend by [differentialContract]. */
class DifferentialCase(
    /** Test-name suffix. */
    val name: String,
    /** Declares the variables and constraints into a fresh scope. */
    val build: ConstraintScope.() -> Unit,
) {
    override fun toString(): String = name

    /** The shared catalogue. */
    companion object {
        /** Name of the only case with non-affine products, which MIP routes exclude. */
        const val NONLINEAR_PRODUCTS = "products-and-negative-values"

        /** Reification, globals, holes, products, projection and optimization cases over tiny domains. */
        @Suppress("LongMethod")
        fun catalogue(): List<DifferentialCase> =
            listOf(
                DifferentialCase("reified-relations") {
                    val x = intVar("x", -2..2)
                    val y = intVar("y", -2..2)
                    val b = boolVar("b")
                    add(BoolEquality(b, IntLessThan(x, y)))
                    add(BoolOr(b, IntEquality(x, IntLiteral(2))))
                    add(BoolNot(IntLessOrEqual(IntNegation(y), IntLiteral(-2))))
                },
                DifferentialCase("nested-all-different") {
                    val xs = (0 until 3).map { intVar("x$it", 0..2) }
                    add(BoolNot(AllDifferent(xs)))
                    add(IntLessOrEqual(xs[0], xs[1]))
                },
                DifferentialCase("nested-table-and-empty-table") {
                    val x = intVar("x", 0..3)
                    val y = intVar("y", 0..3)
                    val rows = listOf(listOf(BigInteger.ONE, BigInteger.TWO), listOf(BigInteger.of(3), BigInteger.ZERO))
                    add(BoolOr(Table(listOf(x, y), rows), IntEquality(x, y)))
                    add(BoolNot(Table(listOf(x), emptyList())))
                },
                DifferentialCase("cardinality-implies-and-conjunction") {
                    val flags = (0 until 3).map { boolVar("f$it") }
                    val z = intVar("z", 0..3)
                    add(BoolImplies(BoolCardinality(flags, 2, CardinalityRelation.AT_LEAST), IntLessThan(IntLiteral(1), z)))
                    add(BoolCardinality(listOf(BoolAnd(flags[0], BoolNot(flags[1])), flags[2]), 1, CardinalityRelation.AT_MOST))
                    add(BoolOr(BoolCardinality(flags, 0, CardinalityRelation.EXACTLY), IntLessOrEqual(z, IntLiteral(2))))
                },
                DifferentialCase("min-max-reified-and-nested") {
                    val x = intVar("x", -2..3)
                    val y = intVar("y", 0..2)
                    val b = boolVar("b")
                    add(BoolEquality(b, IntEquality(IntMax(listOf(x, y, IntLiteral(1))), IntLiteral(2))))
                    add(IntLessOrEqual(IntSum(IntMin(listOf(x, IntNegation(y))), IntMax(listOf(y))), IntLiteral(1)))
                },
                DifferentialCase("bool-to-int-counting") {
                    val xs = (0 until 3).map { intVar("x$it", 0..2) }
                    val z = intVar("z", 0..3)
                    val b = boolVar("b")
                    val ones = xs.map { BoolToInt(IntEquality(it, IntLiteral(1))) }
                    add(IntEquality(LinearSum(ones, List(3) { BigInteger.ONE }), z))
                    add(IntLessOrEqual(IntSum(BoolToInt(b), BoolToInt(BoolOr(b, IntLessThan(xs[0], xs[1])))), IntLiteral(1)))
                },
                DifferentialCase("inverse-top-level") {
                    val f = (0 until 3).map { intVar("f$it", -1..3) }
                    val g = (0 until 3).map { intVar("g$it", 0..2) }
                    add(Inverse(f, g))
                    add(IntLessThan(f[0], f[1]))
                },
                DifferentialCase("inverse-reified") {
                    val f = (0 until 2).map { intVar("f$it", -1..2) }
                    val g = (0 until 2).map { intVar("g$it", 0..1) }
                    val b = boolVar("b")
                    add(BoolEquality(b, Inverse(f, g)))
                },
                DifferentialCase("circuit-top-level") {
                    val next = (0 until 4).map { intVar("n$it", 0..4) }
                    add(Circuit(next))
                    add(BoolNot(IntEquality(next[0], IntLiteral(1))))
                },
                DifferentialCase("circuit-reified") {
                    val next = (0 until 3).map { intVar("n$it", -1..3) }
                    val b = boolVar("b")
                    add(BoolEquality(b, Circuit(next)))
                },
                DifferentialCase("cumulative-top-level-and-reified") {
                    val starts = (0 until 3).map { intVar("s$it", 0..3) }
                    val b = boolVar("b")
                    add(Cumulative(starts, big(2, 1, 2), big(1, 1, 2), BigInteger.TWO))
                    add(BoolEquality(b, Cumulative(starts.take(2), big(2, 2), big(1, 1), BigInteger.ONE)))
                },
                DifferentialCase("cumulative-makespan-optimization") {
                    val starts = (0 until 3).map { intVar("s$it", 0..6) }
                    val durations = big(2, 3, 1)
                    add(Cumulative(starts, durations, big(2, 1, 1), BigInteger.TWO))
                    minimize(IntMax(starts.zip(durations) { s, d -> IntSum(s, IntLiteral(d)) }))
                },
                DifferentialCase("regular-top-level-and-reified") {
                    val word = (0 until 4).map { intVar("w$it", -1..2) }
                    val b = boolVar("b")
                    add(Regular(word, NO_DOUBLE_ONES))
                    add(BoolEquality(b, Regular(word.drop(1).reversed(), NO_DOUBLE_ONES)))
                    add(IntLessOrEqual(IntLiteral(1), IntSum(word[0], word[3])))
                },
                DifferentialCase("subcircuit-top-level") {
                    val next = (0 until 4).map { intVar("n$it", 0..4) }
                    add(SubCircuit(next))
                },
                DifferentialCase("subcircuit-reified") {
                    val next = (0 until 3).map { intVar("n$it", -1..3) }
                    val b = boolVar("b")
                    add(BoolEquality(b, SubCircuit(next)))
                },
                DifferentialCase("bin-packing-top-level-and-reified") {
                    val bins = (0 until 3).map { intVar("bin$it", -1..2) }
                    val loads = (0 until 2).map { intVar("load$it", 0..4) }
                    val b = boolVar("b")
                    add(BinPacking(bins, big(2, 1, 2), loads))
                    add(BoolEquality(b, BinPacking(bins.take(2), big(1, 1), listOf(IntLiteral(1), IntLiteral(1)))))
                },
                DifferentialCase("no-overlap-2d-top-level-and-reified") {
                    val xs = (0 until 3).map { intVar("x$it", 0..2) }
                    val ys = (0 until 3).map { intVar("y$it", 0..1) }
                    val b = boolVar("b")
                    add(NoOverlap2D(xs, ys, big(2, 1, 1), big(1, 2, 1)))
                    add(BoolEquality(b, NoOverlap2D(xs.take(2), ys.take(2), big(1, 1), big(1, 1))))
                },
                DifferentialCase("products-and-negative-values") {
                    val x = intVar("x", -3..3)
                    val y = intVar("y", -3..3)
                    val b = boolVar("b")
                    add(BoolEquality(b, IntEquality(IntProduct(x, y), IntLiteral(-2))))
                    add(IntLessOrEqual(IntDifference(IntProduct(x, IntProduct(x, y)), y), IntLiteral(1)))
                },
                DifferentialCase("affine-products-and-negative-values") {
                    val x = intVar("x", -3..3)
                    val y = intVar("y", -3..3)
                    val b = boolVar("b")
                    add(BoolEquality(b, IntEquality(IntProduct(IntLiteral(-2), x), y)))
                    add(IntLessOrEqual(IntDifference(IntProduct(x, IntNegation(IntLiteral(3))), y), IntLiteral(1)))
                },
                DifferentialCase("element-definedness-is-top-level") {
                    val i = intVar("i", -1..3)
                    val b = boolVar("b")
                    add(BoolOr(b, IntEquality(Element(i, listOf(IntLiteral(3), IntLiteral(1), IntLiteral(3))), IntLiteral(3))))
                },
                DifferentialCase("domains-with-holes-and-empty-domains") {
                    val x = intVar("x", IntDomain.ofIntervals(listOf(interval(-4, -2), interval(1, 1), interval(5, 6))))
                    val y = intVar("y", IntDomain.values(0, 3))
                    add(IntLessOrEqual(IntSum(x, y), IntLiteral(5)))
                },
                DifferentialCase("empty-domain-is-infeasible") {
                    intVar("x", IntDomain.empty())
                    boolVar("b")
                },
                DifferentialCase("constants-and-hidden-variables") {
                    val x = intVar("x", 0..2)
                    val hidden = intVar("hidden", 0..2)
                    add(BoolOr(BoolLiteral(false), IntLessOrEqual(x, hidden)))
                    add(BoolImplies(BoolLiteral(true), BoolLiteral(true)))
                    project(x)
                },
                DifferentialCase("optimization-over-reified-globals") {
                    val xs = (0 until 3).map { intVar("x$it", 0..3) }
                    val b = boolVar("b")
                    add(BoolEquality(b, AllDifferent(xs)))
                    add(IntEquality(Element(xs[0], listOf(IntLiteral(0), IntLiteral(2), IntLiteral(1), IntLiteral(0))), xs[1]))
                    maximize(LinearSum(xs, listOf(BigInteger.ONE, BigInteger.of(-2), BigInteger.of(3)), BigInteger.of(-1)))
                },
                DifferentialCase("infeasible-optimization") {
                    val x = intVar("x", 0..3)
                    add(IntLessThan(x, IntLiteral(0)))
                    minimize(x)
                },
            )

        /** Words over {0, 1} without two consecutive ones. */
        private val NO_DOUBLE_ONES =
            Automaton(
                0,
                setOf(0, 1),
                listOf(Transition(0, BigInteger.ZERO, 0), Transition(0, BigInteger.ONE, 1), Transition(1, BigInteger.ZERO, 0)),
            )

        private fun big(vararg values: Long): List<BigInteger> = values.map { BigInteger.of(it) }

        private fun interval(
            min: Long,
            max: Long,
        ): IntInterval = IntInterval(BigInteger.of(min), BigInteger.of(max))
    }
}
