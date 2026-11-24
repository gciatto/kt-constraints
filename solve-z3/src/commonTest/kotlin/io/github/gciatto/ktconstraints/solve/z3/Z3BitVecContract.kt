package io.github.gciatto.ktconstraints.solve.z3

import io.github.gciatto.ktconstraints.core.bitvector.BitVecLiteral
import io.github.gciatto.ktconstraints.core.bitvector.BitVecToInt
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecBinary
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecComparison
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecOperator
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecRelation
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.incremental.CheckStatus
import io.github.gciatto.ktconstraints.solve.incremental.IncrementalConstraintSolver
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.stream.SolutionFound
import io.github.gciatto.ktconstraints.solve.stream.SolveCompleted
import io.kotest.core.factory.TestFactory
import io.kotest.core.spec.style.funSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** SMT-LIB bit-vector semantics shared by every Z3 route: wrapping arithmetic, signedness and exact wide values. */
fun z3BitVecContract(create: () -> IncrementalConstraintSolver): TestFactory =
    funSpec {
        test("z3-bitvec/addition-wraps-around") {
            ConstraintScope.empty {
                val x = bitVecVar("x", 8)
                add(BitVecComparison(BitVecRelation.EQUAL, BitVecBinary(BitVecOperator.ADD, x, BitVecLiteral(1, 8)), BitVecLiteral(0, 8)))
                create().use { solver -> solver.solve(buildModel()).assignment?.valueOf(x) shouldBe BigInteger.of(255) }
            }
        }

        test("z3-bitvec/signed-and-unsigned-orders-are-distinct") {
            ConstraintScope.empty {
                val x = bitVecVar("x", 8)
                add(BitVecComparison(BitVecRelation.SIGNED_LESS_THAN, x, BitVecLiteral(0, 8)))
                add(BitVecComparison(BitVecRelation.UNSIGNED_LESS_THAN, BitVecLiteral(200, 8), x))
                create().use { solver ->
                    val value = requireNotNull(solver.solve(buildModel()).assignment?.valueOf(x))
                    (value > BigInteger.of(200) && value <= BigInteger.of(255)) shouldBe true
                }
            }
        }

        test("z3-bitvec/even-products-cannot-be-odd") {
            val model =
                ConstraintModel.of {
                    val x = bitVecVar("x", 8)
                    add(
                        BitVecComparison(
                            BitVecRelation.EQUAL,
                            BitVecBinary(BitVecOperator.MULTIPLY, x, BitVecLiteral(2, 8)),
                            BitVecLiteral(1, 8),
                        ),
                    )
                }
            create().use { solver -> solver.solve(model).status shouldBe SolveStatus.INFEASIBLE }
        }

        test("z3-bitvec/wide-vectors-are-exact") {
            ConstraintScope.empty {
                val x = bitVecVar("x", 100)
                add(
                    BitVecComparison(
                        BitVecRelation.EQUAL,
                        BitVecBinary(BitVecOperator.ADD, x, BitVecLiteral(1, 100)),
                        BitVecLiteral(0, 100),
                    ),
                )
                create().use { solver ->
                    solver.solve(buildModel()).assignment?.valueOf(x) shouldBe BigInteger.TWO.pow(100) - BigInteger.ONE
                }
            }
        }

        test("z3-bitvec/shifts-and-signed-conversion") {
            ConstraintScope.empty {
                val x = bitVecVar("x", 8)
                val y = bitVecVar("y", 4)
                add(
                    BitVecComparison(
                        BitVecRelation.EQUAL,
                        BitVecBinary(BitVecOperator.SHIFT_LEFT, x, BitVecLiteral(4, 8)),
                        BitVecLiteral(240, 8),
                    ),
                )
                add(
                    BitVecComparison(
                        BitVecRelation.EQUAL,
                        BitVecBinary(BitVecOperator.LOGICAL_SHIFT_RIGHT, x, BitVecLiteral(4, 8)),
                        BitVecLiteral(0, 8),
                    ),
                )
                add(IntEquality(BitVecToInt(y, signed = true), IntLiteral(-3)))
                create().use { solver ->
                    val assignment = solver.solve(buildModel()).assignment
                    assignment?.valueOf(x) shouldBe BigInteger.of(15)
                    assignment?.valueOf(y) shouldBe BigInteger.of(13)
                }
            }
        }

        test("z3-bitvec/enumeration-blocks-each-value") {
            ConstraintScope.empty {
                val x = bitVecVar("x", 8)
                add(BitVecComparison(BitVecRelation.UNSIGNED_LESS_THAN, x, BitVecLiteral(3, 8)))
                create().use { solver ->
                    val values = mutableSetOf<BigInteger?>()
                    solver.solutions(buildModel()).use { stream ->
                        var event = stream.next()
                        while (event is SolutionFound) {
                            values += event.assignment.valueOf(x)
                            event = stream.next()
                        }
                        (event as SolveCompleted).result.terminationReason shouldBe TerminationReason.COMPLETED
                    }
                    values shouldBe setOf(BigInteger.ZERO, BigInteger.ONE, BigInteger.TWO)
                }
            }
        }

        test("z3-bitvec/sessions-check-bit-vector-assumptions") {
            ConstraintScope.empty {
                val x = bitVecVar("x", 8)
                add(BitVecComparison(BitVecRelation.UNSIGNED_LESS_THAN, x, BitVecLiteral(10, 8)))
                val large = BitVecComparison(BitVecRelation.EQUAL, x, BitVecLiteral(200, 8))
                create().use { solver ->
                    solver.openSession(buildModel()).use { session ->
                        val refuted = session.check(listOf(large))
                        refuted.status shouldBe CheckStatus.UNSATISFIABLE
                        refuted.unsatCore shouldBe listOf(large)
                        session.check().status shouldBe CheckStatus.SATISFIABLE
                    }
                }
            }
        }
    }
