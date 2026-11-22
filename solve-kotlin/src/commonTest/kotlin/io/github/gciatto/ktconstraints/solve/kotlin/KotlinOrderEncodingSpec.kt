package io.github.gciatto.ktconstraints.solve.kotlin

import io.github.gciatto.ktconstraints.core.BoolEquality
import io.github.gciatto.ktconstraints.core.BoolNot
import io.github.gciatto.ktconstraints.core.BoolOr
import io.github.gciatto.ktconstraints.core.IntDifference
import io.github.gciatto.ktconstraints.core.IntDomain
import io.github.gciatto.ktconstraints.core.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.IntLessThan
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntSum
import io.github.gciatto.ktconstraints.core.LinearSum
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.kotest.core.spec.style.FunSpec
import org.gciatto.kt.math.BigInteger

/** Inequalities between shifted medium-domain variables use order literals; all uses agree with the finite oracle. */
class KotlinOrderEncodingSpec :
    FunSpec({
        test("kotlin-order/reified-precedences-with-shifts-on-both-sides") {
            val scope = ConstraintScope.empty()
            val a = scope.intVar("a", 0..10)
            val b = scope.intVar("b", -3..8)
            val c = scope.intVar("c", 2..12)
            scope.add(BoolOr(IntLessOrEqual(IntSum(a, IntLiteral(4)), b), IntLessOrEqual(IntSum(IntLiteral(3), b), a)))
            scope.add(
                BoolOr(IntLessThan(IntDifference(c, IntLiteral(2)), a), IntLessOrEqual(IntSum(b, IntLiteral(7)), IntSum(c, IntLiteral(1)))),
            )
            scope.add(BoolNot(IntLessOrEqual(c, IntSum(a, IntLiteral(-6)))))
            agreesWithOracle(scope.build())
        }

        test("kotlin-order/holes-constants-and-equivalences") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", IntDomain.values(-7, -2, 0, 5, 11, 30))
            val y = scope.intVar("y", IntDomain.of(0, 4).union(IntDomain.of(20, 26)))
            val flag = scope.boolVar("flag")
            scope.add(BoolEquality(flag, IntLessThan(x, y)))
            scope.add(BoolOr(IntLessOrEqual(IntLiteral(5), x), IntLessOrEqual(y, IntLiteral(2))))
            scope.add(BoolNot(IntLessThan(IntSum(y, IntLiteral(-20)), IntDifference(x, IntLiteral(4)))))
            agreesWithOracle(scope.build())
        }

        test("kotlin-order/a-small-schedule-reaches-the-optimal-makespan") {
            val scope = ConstraintScope.empty()
            val durations = listOf(3, 2, 1)
            val starts = durations.indices.map { scope.intVar("s$it", 0..6) }
            val end = scope.intVar("end", 0..6)
            for (i in starts.indices) {
                scope.add(IntLessOrEqual(IntSum(starts[i], IntLiteral(durations[i])), end))
                for (j in i + 1 until starts.size) {
                    scope.add(
                        BoolOr(
                            IntLessOrEqual(IntSum(starts[i], IntLiteral(durations[i])), starts[j]),
                            IntLessOrEqual(IntSum(starts[j], IntLiteral(durations[j])), starts[i]),
                        ),
                    )
                }
            }
            scope.add(IntLessOrEqual(starts[1], IntLiteral(1)))
            scope.minimize(LinearSum(listOf(end, starts[2]), listOf(BigInteger.of(3), BigInteger.ONE)))
            agreesWithOracle(scope.build())
        }
    })
