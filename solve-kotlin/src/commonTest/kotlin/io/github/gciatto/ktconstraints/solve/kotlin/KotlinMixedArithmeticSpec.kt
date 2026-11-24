package io.github.gciatto.ktconstraints.solve.kotlin

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.bitvector.BitVecLiteral
import io.github.gciatto.ktconstraints.core.bitvector.BitVecToInt
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecBinary
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecComparison
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecOperator
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecRelation
import io.github.gciatto.ktconstraints.core.bool.BoolNot
import io.github.gciatto.ktconstraints.core.evaluation.GroundEvaluator
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.IntToReal
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntProduct
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.core.real.RealLiteral
import io.github.gciatto.ktconstraints.core.real.relation.RealEquality
import io.github.gciatto.ktconstraints.core.real.relation.RealLessOrEqual
import io.github.gciatto.ktconstraints.core.real.relation.RealLessThan
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.incremental.CheckStatus
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

class KotlinMixedArithmeticSpec :
    FunSpec({
        test("strict real constraints return a concrete exact witness") {
            val model =
                ConstraintModel.of {
                    val r = realVar("r")
                    add(RealLessThan(RealLiteral(Rational.of(1, 3)), r))
                    add(RealLessThan(r, RealLiteral(Rational.of(1, 2))))
                }
            KotlinBackend().createSolver().use { solver ->
                val result = solver.solve(model)
                result.status shouldBe SolveStatus.FEASIBLE
                model.constraints.all { GroundEvaluator.evaluate(it, requireNotNull(result.assignment)) } shouldBe true
            }
        }

        test("strict boundary inconsistency proves infeasibility") {
            val model =
                ConstraintModel.of {
                    val r = realVar("r")
                    add(RealLessThan(r, RealLiteral(Rational.ONE)))
                    add(RealLessOrEqual(RealLiteral(Rational.ONE), r))
                }
            KotlinBackend().createSolver().use { it.solve(model).status shouldBe SolveStatus.INFEASIBLE }
        }

        test("integer products embedded into reals preserve integrality") {
            val model =
                ConstraintModel.of {
                    val x = intVar("x", -2..2)
                    val r = realVar("r")
                    add(RealEquality(r, IntToReal(IntProduct(x, x))))
                    add(RealEquality(r, RealLiteral(Rational.of(1, 2))))
                }
            KotlinBackend().createSolver().use { it.solve(model).status shouldBe SolveStatus.INFEASIBLE }
        }

        test("negative real equalities can choose either open half-space") {
            ConstraintScope.empty {
                val r = realVar("r")
                add(BoolNot(RealEquality(r, RealLiteral(Rational.ZERO))))
                add(RealLessOrEqual(r, RealLiteral(Rational.ZERO)))
                KotlinBackend().createSolver().use { solver ->
                    val result = solver.solve(buildModel())
                    (requireNotNull(result.assignment?.valueOf(r)) < Rational.ZERO) shouldBe true
                }
            }
        }

        withTests(nameFn = { "bit-vector operation $it matches exact ground semantics" }, BitVecOperator.entries.toList()) { operator ->
            for (left in 0..7) {
                for (right in 0..7) {
                    ConstraintScope.empty {
                        val a = bitVecVar("a", 3)
                        val b = bitVecVar("b", 3)
                        val result = bitVecVar("result", 3)
                        add(BitVecComparison(BitVecRelation.EQUAL, a, BitVecLiteral(BigInteger.of(left), 3)))
                        add(BitVecComparison(BitVecRelation.EQUAL, b, BitVecLiteral(BigInteger.of(right), 3)))
                        add(BitVecComparison(BitVecRelation.EQUAL, result, BitVecBinary(operator, a, b)))
                        val expected =
                            GroundEvaluator.evaluate(
                                BitVecBinary(operator, BitVecLiteral(BigInteger.of(left), 3), BitVecLiteral(BigInteger.of(right), 3)),
                            )
                        KotlinBackend().createSolver().use { solver ->
                            solver.solve(buildModel()).assignment?.valueOf(result) shouldBe expected
                        }
                    }
                }
            }
        }

        test("signed bit-vector conversion is preserved across an incremental pop") {
            ConstraintScope.empty {
                val bits = bitVecVar("bits", 5)
                KotlinBackend().createSolver().use { solver ->
                    solver.openSession(buildModel()).use { session ->
                        val frame = session.push()
                        session.add(IntEquality(BitVecToInt(bits, true), IntLiteral(-1)))
                        session.check().assignment?.valueOf(bits) shouldBe BigInteger.of(31)
                        session.popTo(frame)
                        session.add(IntEquality(BitVecToInt(bits, false), IntLiteral(0)))
                        session.check().status shouldBe CheckStatus.SATISFIABLE
                        session.check().assignment?.valueOf(bits) shouldBe BigInteger.ZERO
                    }
                }
            }
        }
    })
