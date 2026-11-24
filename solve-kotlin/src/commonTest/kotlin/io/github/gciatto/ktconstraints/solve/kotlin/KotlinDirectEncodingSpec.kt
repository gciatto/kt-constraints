package io.github.gciatto.ktconstraints.solve.kotlin

import io.github.gciatto.ktconstraints.core.bool.BoolNot
import io.github.gciatto.ktconstraints.core.bool.BoolOr
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.core.global.counting.AllDifferent
import io.github.gciatto.ktconstraints.core.global.extensional.Element
import io.github.gciatto.ktconstraints.core.global.extensional.Table
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntDifference
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessOrEqual
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.kotest.core.spec.style.FunSpec
import org.gciatto.kt.math.BigInteger

/** Small domains use value literals; every use agrees with the finite oracle, holes and constants included. */
class KotlinDirectEncodingSpec :
    FunSpec({
        test("kotlin-direct/queens-diagonals-as-top-level-all-different-over-offsets") {
            val model =
                ConstraintModel.of {
                    val queens = List(5) { intVar("q$it", 0..4) }
                    add(AllDifferent(queens))
                    add(AllDifferent(queens.mapIndexed { row, queen -> IntSum(queen, IntLiteral(row)) }))
                    add(AllDifferent(queens.mapIndexed { row, queen -> IntDifference(queen, IntLiteral(row)) }))
                }
            agreesWithOracle(model)
        }

        test("kotlin-direct/top-level-disequalities-between-shifted-variables-and-differences") {
            val model =
                ConstraintModel.of {
                    val x = intVar("x", IntDomain.values(0, 2, 3, 6))
                    val y = intVar("y", -1..3)
                    val z = intVar("z", IntDomain.values(1, 4))
                    add(BoolNot(IntEquality(IntDifference(x, y), IntLiteral(3))))
                    add(BoolNot(IntEquality(IntSum(y, IntLiteral(2)), IntDifference(z, IntLiteral(-1)))))
                    add(BoolNot(IntEquality(IntLiteral(4), z)))
                    add(BoolNot(IntEquality(x, IntSum(IntLiteral(1), z))))
                    add(BoolNot(IntEquality(IntDifference(y, x), IntLiteral(-2))))
                }
            agreesWithOracle(model)
        }

        test("kotlin-direct/all-different-with-holes-constants-and-a-reified-copy") {
            val model =
                ConstraintModel.of {
                    val x = intVar("x", IntDomain.values(-3, 0, 4, 9))
                    val y = intVar("y", IntDomain.values(0, 4, 5))
                    val z = intVar("z", -1..2)
                    add(AllDifferent(listOf(x, IntSum(IntLiteral(4), y), IntLiteral(9), IntDifference(z, IntLiteral(-2)))))
                    add(BoolOr(BoolNot(AllDifferent(listOf(x, y, z))), IntEquality(z, IntLiteral(1))))
                }
            agreesWithOracle(model)
        }

        test("kotlin-direct/value-literals-serve-equalities-tables-and-element-indices") {
            val model =
                ConstraintModel.of {
                    val index = intVar("index", IntDomain.values(0, 2, 3, 7))
                    val x = intVar("x", IntDomain.values(1, 3, 6))
                    val y = intVar("y", -2..2)
                    add(IntEquality(Element(index, listOf(IntLiteral(5), x, IntLiteral(3), y)), IntLiteral(3)))
                    add(
                        Table(
                            listOf(x, y),
                            listOf(listOf(1, -2), listOf(3, 0), listOf(6, 2), listOf(4, 1)).map { row ->
                                row.map { BigInteger.of(it.toLong()) }
                            },
                        ),
                    )
                    add(BoolNot(IntEquality(x, IntLiteral(5))))
                    add(BoolOr(IntEquality(y, IntLiteral(9)), BoolNot(IntEquality(index, IntLiteral(3)))))
                }
            agreesWithOracle(model)
        }

        test("kotlin-direct/constant-element-lookups-are-linear-in-objectives-and-reified-comparisons") {
            val model =
                ConstraintModel.of {
                    val first = intVar("first", IntDomain.values(-1, 0, 2, 3, 5))
                    val second = intVar("second", 0..3)
                    val costs = listOf(4, -3, 7, 1).map { IntLiteral(it) }
                    val total = IntSum(Element(first, costs), Element(second, costs.reversed()))
                    add(BoolOr(IntEquality(Element(second, costs), IntLiteral(7)), IntLessOrEqual(total, IntLiteral(3))))
                    add(BoolNot(IntEquality(first, second)))
                    maximize(total)
                }
            agreesWithOracle(model)
        }

        test("kotlin-direct/permutation-and-pigeonhole-at-the-top-level") {
            val scopeModel =
                ConstraintModel.of {
                    val xs = List(4) { intVar("x$it", 0..3) }
                    add(AllDifferent(xs))
                    add(IntEquality(Element(xs[0], xs.map { it }), IntLiteral(0)))
                }
            agreesWithOracle(scopeModel)

            val model =
                ConstraintModel.of {
                    val ps = List(4) { intVar("p$it", IntDomain.values(1, 5, 8)) }
                    add(BoolOr(AllDifferent(ps.take(3)), AllDifferent(ps.drop(1))))
                }
            agreesWithOracle(model)
        }
    })
