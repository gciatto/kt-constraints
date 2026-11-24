package io.github.gciatto.ktconstraints.solve.mip

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.bool.BoolOr
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.core.global.counting.AllDifferent
import io.github.gciatto.ktconstraints.core.global.extensional.Element
import io.github.gciatto.ktconstraints.core.global.extensional.Table
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntProduct
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntSum
import io.github.gciatto.ktconstraints.core.integer.arithmetic.LinearSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.real.RealLiteral
import io.github.gciatto.ktconstraints.core.real.relation.RealEquality
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.mip.program.MipLowering
import io.github.gciatto.ktconstraints.solve.mip.program.MipObjective
import io.github.gciatto.ktconstraints.solve.mip.program.MipRow
import io.github.gciatto.ktconstraints.solve.mip.program.MipVariable
import io.github.gciatto.ktconstraints.solve.spi.exception.UnsupportedModelException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Native-free exact linear lowering: numeric guards, affine products and declared decompositions. */
class MipLoweringSpec :
    FunSpec({
        withTests(
            nameFn = { "mip-lowering/guard/${it.first}" },
            listOf<Pair<String, ConstraintScope.() -> Unit>>(
                "domain-bound" to { intVar("x", IntDomain.of(BigInteger.ZERO, BigInteger.TWO.pow(21))) },
                "literal" to {
                    val x = intVar("x", 0..1)
                    add(IntLessOrEqual(x, IntLiteral(BigInteger.TWO.pow(30))))
                },
                "sum-magnitude" to {
                    val x = intVar("x", 0..600_000)
                    val y = intVar("y", 0..600_000)
                    add(IntLessOrEqual(IntSum(x, y), IntLiteral(5)))
                },
                "non-affine-product" to {
                    val x = intVar("x", 0..3)
                    val y = intVar("y", 0..3)
                    add(IntEquality(IntProduct(x, y), IntLiteral(2)))
                },
                "objective-magnitude" to {
                    val x = intVar("x", 0..600_000)
                    maximize(LinearSum(listOf(x), listOf(BigInteger.TWO)))
                },
                "reals" to {
                    add(RealEquality(realVar("r"), RealLiteral(Rational.of(1, 2))))
                },
                "bit-vectors" to { bitVecVar("v", 8) },
            ),
        ) { (_, build) ->
            val model = ConstraintModel.of(build)
            MipLowering.report(model).isSupported shouldBe false
            shouldThrow<UnsupportedModelException> { MipLowering.lower(model) }
        }

        test("mip-lowering/products-with-a-fixed-factor-are-affine") {
            val model =
                ConstraintModel.of {
                    val x = intVar("x", -3..3)
                    add(IntEquality(IntProduct(IntSum(IntLiteral(1), IntLiteral(2)), x), IntLiteral(6)))
                }
            MipLowering.report(model).isSupported shouldBe true
        }

        test("mip-lowering/decompositions-are-declared") {
            val model =
                ConstraintModel.of {
                    val x = intVar("x", IntDomain.values(0, 2, 3))
                    val y = intVar("y", 0..2)
                    add(AllDifferent(listOf(x, y)))
                    add(BoolOr(Table(listOf(x, y), listOf(listOf(BigInteger.ZERO, BigInteger.ONE))), IntEquality(x, y)))
                    add(IntEquality(Element(y, listOf(IntLiteral(1), x, IntLiteral(2))), IntLiteral(2)))
                }
            val report = MipLowering.report(model)
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

        test("mip-lowering/program-bounds-and-coefficients-are-exact") {
            ConstraintScope.empty {
                val x = intVar("x", -4..9)
                val b = boolVar("b")
                add(IntLessOrEqual(LinearSum(listOf(x), listOf(BigInteger.of(3)), BigInteger.ONE), IntLiteral(10)))
                minimize(IntSum(x, IntLiteral(7)))
                val program = MipLowering.lower(buildModel())
                program.variables[program.integers.getValue(x)] shouldBe MipVariable(BigInteger.of(-4), BigInteger.of(9))
                program.variables[program.booleans.getValue(b)] shouldBe MipVariable(BigInteger.ZERO, BigInteger.ONE)
                program.rows shouldBe listOf(MipRow(mapOf(program.integers.getValue(x) to BigInteger.of(3)), null, BigInteger.of(9)))
                program.objective shouldBe
                    MipObjective(mapOf(program.integers.getValue(x) to BigInteger.ONE), BigInteger.of(7), maximize = false)
            }
        }
    })
