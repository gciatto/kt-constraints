package io.github.gciatto.ktconstraints.solve.z3

import io.github.gciatto.ktconstraints.core.BitVecBinary
import io.github.gciatto.ktconstraints.core.BitVecComparison
import io.github.gciatto.ktconstraints.core.BitVecLiteral
import io.github.gciatto.ktconstraints.core.BitVecOperator
import io.github.gciatto.ktconstraints.core.BitVecRelation
import io.github.gciatto.ktconstraints.core.BitVecToInt
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.CheckStatus
import io.github.gciatto.ktconstraints.solve.IncrementalConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolutionFound
import io.github.gciatto.ktconstraints.solve.SolveCompleted
import io.github.gciatto.ktconstraints.solve.SolveStatus
import io.github.gciatto.ktconstraints.solve.TerminationReason
import io.kotest.core.factory.TestFactory
import io.kotest.core.spec.style.funSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** SMT-LIB bit-vector semantics shared by every Z3 route: wrapping arithmetic, signedness and exact wide values. */
fun z3BitVecContract(create: () -> IncrementalConstraintSolver): TestFactory =
    funSpec {
        test("z3-bitvec/addition-wraps-around") {
            val scope = ConstraintScope.empty()
            val x = scope.bitVecVar("x", 8)
            scope.add(BitVecComparison(BitVecRelation.EQUAL, BitVecBinary(BitVecOperator.ADD, x, BitVecLiteral(1, 8)), BitVecLiteral(0, 8)))
            create().use { solver -> solver.solve(scope.build()).assignment?.valueOf(x) shouldBe BigInteger.of(255) }
        }

        test("z3-bitvec/signed-and-unsigned-orders-are-distinct") {
            val scope = ConstraintScope.empty()
            val x = scope.bitVecVar("x", 8)
            scope.add(BitVecComparison(BitVecRelation.SIGNED_LESS_THAN, x, BitVecLiteral(0, 8)))
            scope.add(BitVecComparison(BitVecRelation.UNSIGNED_LESS_THAN, BitVecLiteral(200, 8), x))
            create().use { solver ->
                val value = requireNotNull(solver.solve(scope.build()).assignment?.valueOf(x))
                (value > BigInteger.of(200) && value <= BigInteger.of(255)) shouldBe true
            }
        }

        test("z3-bitvec/even-products-cannot-be-odd") {
            val scope = ConstraintScope.empty()
            val x = scope.bitVecVar("x", 8)
            scope.add(
                BitVecComparison(BitVecRelation.EQUAL, BitVecBinary(BitVecOperator.MULTIPLY, x, BitVecLiteral(2, 8)), BitVecLiteral(1, 8)),
            )
            create().use { solver -> solver.solve(scope.build()).status shouldBe SolveStatus.INFEASIBLE }
        }

        test("z3-bitvec/wide-vectors-are-exact") {
            val scope = ConstraintScope.empty()
            val x = scope.bitVecVar("x", 100)
            scope.add(
                BitVecComparison(BitVecRelation.EQUAL, BitVecBinary(BitVecOperator.ADD, x, BitVecLiteral(1, 100)), BitVecLiteral(0, 100)),
            )
            create().use { solver -> solver.solve(scope.build()).assignment?.valueOf(x) shouldBe BigInteger.TWO.pow(100) - BigInteger.ONE }
        }

        test("z3-bitvec/shifts-and-signed-conversion") {
            val scope = ConstraintScope.empty()
            val x = scope.bitVecVar("x", 8)
            val y = scope.bitVecVar("y", 4)
            scope.add(
                BitVecComparison(
                    BitVecRelation.EQUAL,
                    BitVecBinary(BitVecOperator.SHIFT_LEFT, x, BitVecLiteral(4, 8)),
                    BitVecLiteral(240, 8),
                ),
            )
            scope.add(
                BitVecComparison(
                    BitVecRelation.EQUAL,
                    BitVecBinary(BitVecOperator.LOGICAL_SHIFT_RIGHT, x, BitVecLiteral(4, 8)),
                    BitVecLiteral(0, 8),
                ),
            )
            scope.add(IntEquality(BitVecToInt(y, signed = true), IntLiteral(-3)))
            create().use { solver ->
                val assignment = solver.solve(scope.build()).assignment
                assignment?.valueOf(x) shouldBe BigInteger.of(15)
                assignment?.valueOf(y) shouldBe BigInteger.of(13)
            }
        }

        test("z3-bitvec/enumeration-blocks-each-value") {
            val scope = ConstraintScope.empty()
            val x = scope.bitVecVar("x", 8)
            scope.add(BitVecComparison(BitVecRelation.UNSIGNED_LESS_THAN, x, BitVecLiteral(3, 8)))
            create().use { solver ->
                val values = mutableSetOf<BigInteger?>()
                solver.solutions(scope.build()).use { stream ->
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

        test("z3-bitvec/sessions-check-bit-vector-assumptions") {
            val scope = ConstraintScope.empty()
            val x = scope.bitVecVar("x", 8)
            scope.add(BitVecComparison(BitVecRelation.UNSIGNED_LESS_THAN, x, BitVecLiteral(10, 8)))
            val large = BitVecComparison(BitVecRelation.EQUAL, x, BitVecLiteral(200, 8))
            create().use { solver ->
                solver.openSession(scope.build()).use { session ->
                    val refuted = session.check(listOf(large))
                    refuted.status shouldBe CheckStatus.UNSATISFIABLE
                    refuted.unsatCore shouldBe listOf(large)
                    session.check().status shouldBe CheckStatus.SATISFIABLE
                }
            }
        }
    }
