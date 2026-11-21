package io.github.gciatto.ktconstraints.solve.mip

import io.github.gciatto.ktconstraints.core.AllDifferent
import io.github.gciatto.ktconstraints.core.BoolOr
import io.github.gciatto.ktconstraints.core.Element
import io.github.gciatto.ktconstraints.core.IntDomain
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntProduct
import io.github.gciatto.ktconstraints.core.IntSum
import io.github.gciatto.ktconstraints.core.LinearSum
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.RealEquality
import io.github.gciatto.ktconstraints.core.RealLiteral
import io.github.gciatto.ktconstraints.core.Table
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.spi.UnsupportedModelException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger
import kotlin.time.Duration.Companion.seconds

/** Native-free exact linear lowering: numeric guards, affine products and declared decompositions. */
class MipLoweringSpec :
    FunSpec({
        withTests(
            nameFn = { "mip-lowering/guard/${it.first}" },
            listOf<Pair<String, (ConstraintScope) -> Unit>>(
                "domain-bound" to { scope -> scope.intVar("x", IntDomain.of(BigInteger.ZERO, BigInteger.TWO.pow(21))) },
                "literal" to { scope ->
                    val x = scope.intVar("x", 0..1)
                    scope.add(IntLessOrEqual(x, IntLiteral(BigInteger.TWO.pow(30))))
                },
                "sum-magnitude" to { scope ->
                    val x = scope.intVar("x", 0..600_000)
                    val y = scope.intVar("y", 0..600_000)
                    scope.add(IntLessOrEqual(IntSum(x, y), IntLiteral(5)))
                },
                "non-affine-product" to { scope ->
                    val x = scope.intVar("x", 0..3)
                    val y = scope.intVar("y", 0..3)
                    scope.add(IntEquality(IntProduct(x, y), IntLiteral(2)))
                },
                "objective-magnitude" to { scope ->
                    val x = scope.intVar("x", 0..600_000)
                    scope.maximize(LinearSum(listOf(x), listOf(BigInteger.TWO)))
                },
                "reals" to { scope ->
                    scope.add(RealEquality(scope.realVar("r"), RealLiteral(Rational.of(1, 2))))
                },
                "bit-vectors" to { scope -> scope.bitVecVar("v", 8) },
            ),
        ) { (_, build) ->
            val scope = ConstraintScope.empty()
            build(scope)
            val model = scope.build()
            MipLowering.report(model).isSupported shouldBe false
            shouldThrow<UnsupportedModelException> { MipLowering.lower(model) }
        }

        test("mip-lowering/products-with-a-fixed-factor-are-affine") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", -3..3)
            scope.add(IntEquality(IntProduct(IntSum(IntLiteral(1), IntLiteral(2)), x), IntLiteral(6)))
            MipLowering.report(scope.build()).isSupported shouldBe true
        }

        test("mip-lowering/decompositions-are-declared") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", IntDomain.values(0, 2, 3))
            val y = scope.intVar("y", 0..2)
            scope.add(AllDifferent(listOf(x, y)))
            scope.add(BoolOr(Table(listOf(x, y), listOf(listOf(BigInteger.ZERO, BigInteger.ONE))), IntEquality(x, y)))
            scope.add(IntEquality(Element(y, listOf(IntLiteral(1), x, IntLiteral(2))), IntLiteral(2)))
            val report = MipLowering.report(scope.build())
            report.isSupported shouldBe true
            report.losslessDecompositions shouldContainAll
                listOf(
                    MipLowering.HOLES,
                    MipLowering.ALL_DIFFERENT,
                    MipLowering.TABLE,
                    MipLowering.ELEMENT,
                    MipLowering.REIFICATION,
                )
        }

        test("mip-lowering/large-all-different-lowers-in-quadratic-time").config(timeout = 5.seconds) {
            val scope = ConstraintScope.empty()
            val xs = (0 until 250).map { scope.intVar("x$it", 0..249) }
            scope.add(AllDifferent(xs))
            MipLowering.report(scope.build()).isSupported shouldBe true
        }

        test("mip-lowering/program-bounds-and-coefficients-are-exact") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", -4..9)
            val b = scope.boolVar("b")
            scope.add(IntLessOrEqual(LinearSum(listOf(x), listOf(BigInteger.of(3)), BigInteger.ONE), IntLiteral(10)))
            scope.minimize(IntSum(x, IntLiteral(7)))
            val program = MipLowering.lower(scope.build())
            program.variables[program.integers.getValue(x)] shouldBe MipVariable(BigInteger.of(-4), BigInteger.of(9))
            program.variables[program.booleans.getValue(b)] shouldBe MipVariable(BigInteger.ZERO, BigInteger.ONE)
            program.rows shouldBe listOf(MipRow(mapOf(program.integers.getValue(x) to BigInteger.of(3)), null, BigInteger.of(9)))
            program.objective shouldBe
                MipObjective(mapOf(program.integers.getValue(x) to BigInteger.ONE), BigInteger.of(7), maximize = false)
        }
    })
