package io.github.gciatto.ktconstraints.solve.kotlin

import io.github.gciatto.ktconstraints.core.AllDifferent
import io.github.gciatto.ktconstraints.core.BoolNot
import io.github.gciatto.ktconstraints.core.BoolOr
import io.github.gciatto.ktconstraints.core.Element
import io.github.gciatto.ktconstraints.core.IntDifference
import io.github.gciatto.ktconstraints.core.IntDomain
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntSum
import io.github.gciatto.ktconstraints.core.Table
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.kotest.core.spec.style.FunSpec
import org.gciatto.kt.math.BigInteger

/** Small domains use value literals; every use agrees with the finite oracle, holes and constants included. */
class KotlinDirectEncodingSpec :
    FunSpec({
        test("kotlin-direct/queens-diagonals-as-top-level-all-different-over-offsets") {
            val scope = ConstraintScope.empty()
            val queens = List(5) { scope.intVar("q$it", 0..4) }
            scope.add(AllDifferent(queens))
            scope.add(AllDifferent(queens.mapIndexed { row, queen -> IntSum(queen, IntLiteral(row)) }))
            scope.add(AllDifferent(queens.mapIndexed { row, queen -> IntDifference(queen, IntLiteral(row)) }))
            agreesWithOracle(scope.build())
        }

        test("kotlin-direct/top-level-disequalities-between-shifted-variables-and-differences") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", IntDomain.values(0, 2, 3, 6))
            val y = scope.intVar("y", -1..3)
            val z = scope.intVar("z", IntDomain.values(1, 4))
            scope.add(BoolNot(IntEquality(IntDifference(x, y), IntLiteral(3))))
            scope.add(BoolNot(IntEquality(IntSum(y, IntLiteral(2)), IntDifference(z, IntLiteral(-1)))))
            scope.add(BoolNot(IntEquality(IntLiteral(4), z)))
            scope.add(BoolNot(IntEquality(x, IntSum(IntLiteral(1), z))))
            scope.add(BoolNot(IntEquality(IntDifference(y, x), IntLiteral(-2))))
            agreesWithOracle(scope.build())
        }

        test("kotlin-direct/all-different-with-holes-constants-and-a-reified-copy") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", IntDomain.values(-3, 0, 4, 9))
            val y = scope.intVar("y", IntDomain.values(0, 4, 5))
            val z = scope.intVar("z", -1..2)
            scope.add(AllDifferent(listOf(x, IntSum(IntLiteral(4), y), IntLiteral(9), IntDifference(z, IntLiteral(-2)))))
            scope.add(BoolOr(BoolNot(AllDifferent(listOf(x, y, z))), IntEquality(z, IntLiteral(1))))
            agreesWithOracle(scope.build())
        }

        test("kotlin-direct/value-literals-serve-equalities-tables-and-element-indices") {
            val scope = ConstraintScope.empty()
            val index = scope.intVar("index", IntDomain.values(0, 2, 3, 7))
            val x = scope.intVar("x", IntDomain.values(1, 3, 6))
            val y = scope.intVar("y", -2..2)
            scope.add(IntEquality(Element(index, listOf(IntLiteral(5), x, IntLiteral(3), y)), IntLiteral(3)))
            scope.add(
                Table(
                    listOf(x, y),
                    listOf(listOf(1, -2), listOf(3, 0), listOf(6, 2), listOf(4, 1)).map { row ->
                        row.map { BigInteger.of(it.toLong()) }
                    },
                ),
            )
            scope.add(BoolNot(IntEquality(x, IntLiteral(5))))
            scope.add(BoolOr(IntEquality(y, IntLiteral(9)), BoolNot(IntEquality(index, IntLiteral(3)))))
            agreesWithOracle(scope.build())
        }

        test("kotlin-direct/permutation-and-pigeonhole-at-the-top-level") {
            val permutation = ConstraintScope.empty()
            val xs = List(4) { permutation.intVar("x$it", 0..3) }
            permutation.add(AllDifferent(xs))
            permutation.add(IntEquality(Element(xs[0], xs.map { it }), IntLiteral(0)))
            agreesWithOracle(permutation.build())

            val pigeons = ConstraintScope.empty()
            val ps = List(4) { pigeons.intVar("p$it", IntDomain.values(1, 5, 8)) }
            pigeons.add(BoolOr(AllDifferent(ps.take(3)), AllDifferent(ps.drop(1))))
            agreesWithOracle(pigeons.build())
        }
    })
