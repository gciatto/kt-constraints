package io.github.gciatto.ktconstraints.solve.kotlin

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.bool.BoolImplies
import io.github.gciatto.ktconstraints.core.bool.BoolNot
import io.github.gciatto.ktconstraints.core.bool.BoolOr
import io.github.gciatto.ktconstraints.core.global.counting.BoolCardinality
import io.github.gciatto.ktconstraints.core.global.counting.CardinalityRelation
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.IntToReal
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessThan
import io.github.gciatto.ktconstraints.core.real.RealLiteral
import io.github.gciatto.ktconstraints.core.real.arithmetic.RealSum
import io.github.gciatto.ktconstraints.core.real.relation.RealLessOrEqual
import io.github.gciatto.ktconstraints.core.real.relation.RealLessThan
import io.github.gciatto.ktconstraints.model.ConstraintScope

/** Fixed, small models with known witnesses; these are smoke workloads, not a representative benchmark corpus. */
internal object KotlinBenchmarkCases {
    fun sat(): KotlinBenchmarkCase {
        val scope = ConstraintScope.empty()
        val selected = List(24) { scope.boolVar("selected$it") }
        scope.add(BoolCardinality(selected, 12, CardinalityRelation.EXACTLY))
        repeat(12) { pair ->
            scope.add(BoolOr(selected[2 * pair], selected[2 * pair + 1]))
            scope.add(BoolImplies(selected[2 * pair], BoolNot(selected[2 * ((pair + 1) % 12)])))
        }
        scope.project(*selected.take(6).toTypedArray())
        return KotlinBenchmarkCase("sat", scope.build(), selected.first(), BoolNot(selected.first()))
    }

    fun scheduling(): KotlinBenchmarkCase {
        val scope = ConstraintScope.empty()
        val durations = listOf(2, 1, 3, 2, 1, 3, 2, 1)
        val starts = durations.indices.map { scope.intVar("start$it", 0..24) }
        val ends = starts.indices.map { IntSum(starts[it], IntLiteral(durations[it])) }
        for (task in starts.indices) {
            scope.add(IntLessOrEqual(ends[task], IntLiteral(24)))
            for (other in task + 1 until starts.size) {
                if (task % 2 == other % 2) {
                    scope.add(BoolOr(IntLessOrEqual(ends[task], starts[other]), IntLessOrEqual(ends[other], starts[task])))
                }
            }
        }
        for (job in 0 until starts.size / 2) scope.add(IntLessOrEqual(ends[2 * job], starts[2 * job + 1]))
        scope.project(*starts.take(4).toTypedArray())
        return KotlinBenchmarkCase(
            "finite-scheduling",
            scope.build(),
            IntEquality(starts.first(), IntLiteral(0)),
            IntEquality(starts.first(), IntLiteral(1)),
        )
    }

    fun mixedReal(): KotlinBenchmarkCase {
        val scope = ConstraintScope.empty()
        val slots = List(4) { scope.intVar("slot$it", 0..6) }
        val starts = List(4) { scope.realVar("start$it") }
        val duration = RealLiteral(Rational.of(1, 2))
        for (task in starts.indices) {
            val lower = RealSum(IntToReal(slots[task]), RealLiteral(Rational.of(1, 3)))
            val upper = RealSum(IntToReal(slots[task]), RealLiteral(Rational.of(2, 3)))
            scope.add(RealLessOrEqual(lower, starts[task]))
            scope.add(RealLessThan(starts[task], upper))
            for (other in task + 1 until starts.size) {
                scope.add(
                    BoolOr(
                        RealLessOrEqual(RealSum(starts[task], duration), starts[other]),
                        RealLessOrEqual(RealSum(starts[other], duration), starts[task]),
                    ),
                )
            }
        }
        scope.add(IntLessThan(slots[0], slots[1]))
        scope.project(slots[0], starts[0], slots[1], starts[1])
        return KotlinBenchmarkCase(
            "mixed-real-scheduling",
            scope.build(),
            IntEquality(slots.first(), IntLiteral(0)),
            IntEquality(slots.first(), IntLiteral(1)),
        )
    }
}
