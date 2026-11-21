package io.github.gciatto.ktconstraints.test.solve

import io.github.gciatto.ktconstraints.core.AllDifferent
import io.github.gciatto.ktconstraints.core.BoolAnd
import io.github.gciatto.ktconstraints.core.BoolCardinality
import io.github.gciatto.ktconstraints.core.BoolEquality
import io.github.gciatto.ktconstraints.core.BoolImplies
import io.github.gciatto.ktconstraints.core.BoolLiteral
import io.github.gciatto.ktconstraints.core.BoolNot
import io.github.gciatto.ktconstraints.core.BoolOr
import io.github.gciatto.ktconstraints.core.BoolToInt
import io.github.gciatto.ktconstraints.core.CardinalityRelation
import io.github.gciatto.ktconstraints.core.Circuit
import io.github.gciatto.ktconstraints.core.Cumulative
import io.github.gciatto.ktconstraints.core.Element
import io.github.gciatto.ktconstraints.core.IntDifference
import io.github.gciatto.ktconstraints.core.IntDomain
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntInterval
import io.github.gciatto.ktconstraints.core.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.IntLessThan
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntMax
import io.github.gciatto.ktconstraints.core.IntMin
import io.github.gciatto.ktconstraints.core.IntNegation
import io.github.gciatto.ktconstraints.core.IntProduct
import io.github.gciatto.ktconstraints.core.IntSum
import io.github.gciatto.ktconstraints.core.Inverse
import io.github.gciatto.ktconstraints.core.LinearSum
import io.github.gciatto.ktconstraints.core.Table
import io.github.gciatto.ktconstraints.model.ConstraintScope
import org.gciatto.kt.math.BigInteger

/** A small Boolean/integer model the [FiniteOracle] can enumerate, compared against a backend by [differentialContract]. */
class DifferentialCase(
    /** Test-name suffix. */
    val name: String,
    /** Declares the variables and constraints into a fresh scope. */
    val build: (ConstraintScope) -> Unit,
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
                DifferentialCase("reified-relations") { scope ->
                    val x = scope.intVar("x", -2..2)
                    val y = scope.intVar("y", -2..2)
                    val b = scope.boolVar("b")
                    scope.add(BoolEquality(b, IntLessThan(x, y)))
                    scope.add(BoolOr(b, IntEquality(x, IntLiteral(2))))
                    scope.add(BoolNot(IntLessOrEqual(IntNegation(y), IntLiteral(-2))))
                },
                DifferentialCase("nested-all-different") { scope ->
                    val xs = (0 until 3).map { scope.intVar("x$it", 0..2) }
                    scope.add(BoolNot(AllDifferent(xs)))
                    scope.add(IntLessOrEqual(xs[0], xs[1]))
                },
                DifferentialCase("nested-table-and-empty-table") { scope ->
                    val x = scope.intVar("x", 0..3)
                    val y = scope.intVar("y", 0..3)
                    val rows = listOf(listOf(BigInteger.ONE, BigInteger.TWO), listOf(BigInteger.of(3), BigInteger.ZERO))
                    scope.add(BoolOr(Table(listOf(x, y), rows), IntEquality(x, y)))
                    scope.add(BoolNot(Table(listOf(x), emptyList())))
                },
                DifferentialCase("cardinality-implies-and-conjunction") { scope ->
                    val flags = (0 until 3).map { scope.boolVar("f$it") }
                    val z = scope.intVar("z", 0..3)
                    scope.add(BoolImplies(BoolCardinality(flags, 2, CardinalityRelation.AT_LEAST), IntLessThan(IntLiteral(1), z)))
                    scope.add(BoolCardinality(listOf(BoolAnd(flags[0], BoolNot(flags[1])), flags[2]), 1, CardinalityRelation.AT_MOST))
                    scope.add(BoolOr(BoolCardinality(flags, 0, CardinalityRelation.EXACTLY), IntLessOrEqual(z, IntLiteral(2))))
                },
                DifferentialCase("min-max-reified-and-nested") { scope ->
                    val x = scope.intVar("x", -2..3)
                    val y = scope.intVar("y", 0..2)
                    val b = scope.boolVar("b")
                    scope.add(BoolEquality(b, IntEquality(IntMax(listOf(x, y, IntLiteral(1))), IntLiteral(2))))
                    scope.add(IntLessOrEqual(IntSum(IntMin(listOf(x, IntNegation(y))), IntMax(listOf(y))), IntLiteral(1)))
                },
                DifferentialCase("bool-to-int-counting") { scope ->
                    val xs = (0 until 3).map { scope.intVar("x$it", 0..2) }
                    val z = scope.intVar("z", 0..3)
                    val b = scope.boolVar("b")
                    val ones = xs.map { BoolToInt(IntEquality(it, IntLiteral(1))) }
                    scope.add(IntEquality(LinearSum(ones, List(3) { BigInteger.ONE }), z))
                    scope.add(IntLessOrEqual(IntSum(BoolToInt(b), BoolToInt(BoolOr(b, IntLessThan(xs[0], xs[1])))), IntLiteral(1)))
                },
                DifferentialCase("inverse-top-level") { scope ->
                    val f = (0 until 3).map { scope.intVar("f$it", -1..3) }
                    val g = (0 until 3).map { scope.intVar("g$it", 0..2) }
                    scope.add(Inverse(f, g))
                    scope.add(IntLessThan(f[0], f[1]))
                },
                DifferentialCase("inverse-reified") { scope ->
                    val f = (0 until 2).map { scope.intVar("f$it", -1..2) }
                    val g = (0 until 2).map { scope.intVar("g$it", 0..1) }
                    val b = scope.boolVar("b")
                    scope.add(BoolEquality(b, Inverse(f, g)))
                },
                DifferentialCase("circuit-top-level") { scope ->
                    val next = (0 until 4).map { scope.intVar("n$it", 0..4) }
                    scope.add(Circuit(next))
                    scope.add(BoolNot(IntEquality(next[0], IntLiteral(1))))
                },
                DifferentialCase("circuit-reified") { scope ->
                    val next = (0 until 3).map { scope.intVar("n$it", -1..3) }
                    val b = scope.boolVar("b")
                    scope.add(BoolEquality(b, Circuit(next)))
                },
                DifferentialCase("cumulative-top-level-and-reified") { scope ->
                    val starts = (0 until 3).map { scope.intVar("s$it", 0..3) }
                    val b = scope.boolVar("b")
                    scope.add(Cumulative(starts, big(2, 1, 2), big(1, 1, 2), BigInteger.TWO))
                    scope.add(BoolEquality(b, Cumulative(starts.take(2), big(2, 2), big(1, 1), BigInteger.ONE)))
                },
                DifferentialCase("cumulative-makespan-optimization") { scope ->
                    val starts = (0 until 3).map { scope.intVar("s$it", 0..6) }
                    val durations = big(2, 3, 1)
                    scope.add(Cumulative(starts, durations, big(2, 1, 1), BigInteger.TWO))
                    scope.minimize(IntMax(starts.zip(durations) { s, d -> IntSum(s, IntLiteral(d)) }))
                },
                DifferentialCase("products-and-negative-values") { scope ->
                    val x = scope.intVar("x", -3..3)
                    val y = scope.intVar("y", -3..3)
                    val b = scope.boolVar("b")
                    scope.add(BoolEquality(b, IntEquality(IntProduct(x, y), IntLiteral(-2))))
                    scope.add(IntLessOrEqual(IntDifference(IntProduct(x, IntProduct(x, y)), y), IntLiteral(1)))
                },
                DifferentialCase("affine-products-and-negative-values") { scope ->
                    val x = scope.intVar("x", -3..3)
                    val y = scope.intVar("y", -3..3)
                    val b = scope.boolVar("b")
                    scope.add(BoolEquality(b, IntEquality(IntProduct(IntLiteral(-2), x), y)))
                    scope.add(IntLessOrEqual(IntDifference(IntProduct(x, IntNegation(IntLiteral(3))), y), IntLiteral(1)))
                },
                DifferentialCase("element-definedness-is-top-level") { scope ->
                    val i = scope.intVar("i", -1..3)
                    val b = scope.boolVar("b")
                    scope.add(BoolOr(b, IntEquality(Element(i, listOf(IntLiteral(3), IntLiteral(1), IntLiteral(3))), IntLiteral(3))))
                },
                DifferentialCase("domains-with-holes-and-empty-domains") { scope ->
                    val x = scope.intVar("x", IntDomain.ofIntervals(listOf(interval(-4, -2), interval(1, 1), interval(5, 6))))
                    val y = scope.intVar("y", IntDomain.values(0, 3))
                    scope.add(IntLessOrEqual(IntSum(x, y), IntLiteral(5)))
                },
                DifferentialCase("empty-domain-is-infeasible") { scope ->
                    scope.intVar("x", IntDomain.empty())
                    scope.boolVar("b")
                },
                DifferentialCase("constants-and-hidden-variables") { scope ->
                    val x = scope.intVar("x", 0..2)
                    val hidden = scope.intVar("hidden", 0..2)
                    scope.add(BoolOr(BoolLiteral(false), IntLessOrEqual(x, hidden)))
                    scope.add(BoolImplies(BoolLiteral(true), BoolLiteral(true)))
                    scope.project(x)
                },
                DifferentialCase("optimization-over-reified-globals") { scope ->
                    val xs = (0 until 3).map { scope.intVar("x$it", 0..3) }
                    val b = scope.boolVar("b")
                    scope.add(BoolEquality(b, AllDifferent(xs)))
                    scope.add(IntEquality(Element(xs[0], listOf(IntLiteral(0), IntLiteral(2), IntLiteral(1), IntLiteral(0))), xs[1]))
                    scope.maximize(LinearSum(xs, listOf(BigInteger.ONE, BigInteger.of(-2), BigInteger.of(3)), BigInteger.of(-1)))
                },
                DifferentialCase("infeasible-optimization") { scope ->
                    val x = scope.intVar("x", 0..3)
                    scope.add(IntLessThan(x, IntLiteral(0)))
                    scope.minimize(x)
                },
            )

        private fun big(vararg values: Long): List<BigInteger> = values.map { BigInteger.of(it) }

        private fun interval(
            min: Long,
            max: Long,
        ): IntInterval = IntInterval(BigInteger.of(min), BigInteger.of(max))
    }
}
