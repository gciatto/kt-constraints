package io.github.gciatto.ktconstraints.solve.spi

import io.github.gciatto.ktconstraints.core.AllDifferent
import io.github.gciatto.ktconstraints.core.BitVecComparison
import io.github.gciatto.ktconstraints.core.BitVecLiteral
import io.github.gciatto.ktconstraints.core.BitVecRelation
import io.github.gciatto.ktconstraints.core.BoolCardinality
import io.github.gciatto.ktconstraints.core.BoolOr
import io.github.gciatto.ktconstraints.core.BoolToInt
import io.github.gciatto.ktconstraints.core.CardinalityRelation
import io.github.gciatto.ktconstraints.core.Circuit
import io.github.gciatto.ktconstraints.core.Cumulative
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntMax
import io.github.gciatto.ktconstraints.core.IntMin
import io.github.gciatto.ktconstraints.core.IntProduct
import io.github.gciatto.ktconstraints.core.IntSum
import io.github.gciatto.ktconstraints.core.Inverse
import io.github.gciatto.ktconstraints.core.LinearSum
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.RealLessThan
import io.github.gciatto.ktconstraints.core.RealLiteral
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Feature extraction drives ranking, so each flag and the derived problem class are pinned down here. */
class ModelFeaturesSpec :
    FunSpec({
        withTests(
            nameFn = { "model-features/problem-class/${it.first}" },
            listOf<Triple<String, ConstraintModel, ProblemClass>>(
                Triple(
                    "pure-boolean",
                    ConstraintModel.of {
                        val flags = List(3) { boolVar("b$it") }
                        add(BoolCardinality(flags, 1, CardinalityRelation.EXACTLY))
                    },
                    ProblemClass.PURE_BOOLEAN,
                ),
                Triple(
                    "linear-integer",
                    ConstraintModel.of {
                        val x = intVar("x", 0..9)
                        val y = intVar("y", 0..9)
                        add(IntLessOrEqual(IntSum(x, y), IntLiteral(10)))
                        maximize(LinearSum(listOf(x, y), listOf(BigInteger.of(3), BigInteger.TWO)))
                    },
                    ProblemClass.LINEAR_INTEGER,
                ),
                Triple(
                    "nonlinear-integer",
                    ConstraintModel.of {
                        val x = intVar("x", 0..9)
                        val y = intVar("y", 0..9)
                        add(IntEquality(IntProduct(x, y), IntLiteral(12)))
                    },
                    ProblemClass.NONLINEAR_INTEGER,
                ),
                Triple(
                    "globals",
                    ConstraintModel.of {
                        val xs = List(3) { intVar("x$it", 0..2) }
                        add(AllDifferent(xs))
                    },
                    ProblemClass.CP_GLOBALS,
                ),
                Triple(
                    "reified-relations",
                    ConstraintModel.of {
                        val x = intVar("x", 0..9)
                        add(BoolOr(IntEquality(x, IntLiteral(1)), IntEquality(x, IntLiteral(2))))
                    },
                    ProblemClass.CP_GLOBALS,
                ),
                Triple(
                    "reals",
                    ConstraintModel.of {
                        val r = realVar("r")
                        add(RealLessThan(RealLiteral(Rational.of(1, 2)), r))
                    },
                    ProblemClass.SMT_REAL,
                ),
                Triple(
                    "bit-vectors",
                    ConstraintModel.of {
                        val v = bitVecVar("v", 8)
                        add(BitVecComparison(BitVecRelation.UNSIGNED_LESS_THAN, v, BitVecLiteral(BigInteger.of(3), 8)))
                    },
                    ProblemClass.SMT_BITVECTOR,
                ),
            ),
        ) { (_, model, expected) -> ModelFeatures.of(model).problemClass shouldBe expected }

        test("model-features/flags-sizes-and-magnitudes") {
            val model =
                ConstraintModel.of {
                    val x = intVar("x", -5..9)
                    val y = intVar("y", 0..1_000)
                    val b = boolVar("b")
                    add(BoolOr(b, IntEquality(IntProduct(x, y), IntLiteral(-20_000))))
                    add(AllDifferent(listOf(x, y)))
                    minimize(IntProduct(x, y))
                }
            val features = ModelFeatures.of(model)
            features.usesBooleans shouldBe true
            features.usesIntegers shouldBe true
            features.usesReals shouldBe false
            features.usesBitVectors shouldBe false
            features.nonlinear shouldBe true
            features.reified shouldBe true
            features.globals shouldBe setOf(GlobalKind.ALL_DIFFERENT)
            features.objective shouldBe ObjectiveKind.NONLINEAR
            features.variables shouldBe 3
            features.constraints shouldBe 2
            features.maxMagnitude shouldBe BigInteger.of(20_000)
        }

        test("model-features/min-and-max-are-globals") {
            val model =
                ConstraintModel.of {
                    val x = intVar("x", 0..3)
                    val y = intVar("y", 0..3)
                    add(IntLessOrEqual(IntMax(listOf(x, y)), IntMin(listOf(x, IntLiteral(2)))))
                }
            val features = ModelFeatures.of(model)
            features.globals shouldBe setOf(GlobalKind.MIN_MAX)
            features.problemClass shouldBe ProblemClass.CP_GLOBALS
        }

        test("model-features/structural-globals-and-bool-to-int") {
            val model =
                ConstraintModel.of {
                    val xs = List(3) { intVar("x$it", 0..2) }
                    add(Inverse(xs, xs))
                    add(Circuit(xs))
                    add(Cumulative(xs, List(3) { BigInteger.ONE }, List(3) { BigInteger.ONE }, BigInteger.TWO))
                    add(IntLessOrEqual(BoolToInt(IntEquality(xs[0], IntLiteral(1))), xs[1]))
                }
            val features = ModelFeatures.of(model)
            features.globals shouldBe setOf(GlobalKind.INVERSE, GlobalKind.CIRCUIT, GlobalKind.CUMULATIVE)
            features.reified shouldBe true
        }

        test("model-features/satisfaction-has-no-objective") {
            ModelFeatures.of(ConstraintModel.of { intVar("x", 0..1) }).objective shouldBe ObjectiveKind.NONE
        }
    })
