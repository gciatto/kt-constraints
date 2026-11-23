@file:JvmMultifileClass
@file:JvmName("Expressions")

package io.github.gciatto.ktconstraints.dsl

import io.github.gciatto.ktconstraints.core.bool.BoolAnd
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.bool.BoolLiteral
import io.github.gciatto.ktconstraints.core.bool.BoolOr
import io.github.gciatto.ktconstraints.core.integer.IntExpr
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessThan
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

/** Each operand is at most the next. */
fun increasing(vararg operands: IntExpr): BoolExpr = increasing(operands.asList())

/** Each operand is at most the next. */
fun increasing(operands: List<IntExpr>): BoolExpr = BoolAnd.of(operands.zipWithNext { a, b -> IntLessOrEqual(a, b) })

/** Each operand is at least the next. */
fun decreasing(vararg operands: IntExpr): BoolExpr = decreasing(operands.asList())

/** Each operand is at least the next. */
fun decreasing(operands: List<IntExpr>): BoolExpr = BoolAnd.of(operands.zipWithNext { a, b -> IntLessOrEqual(b, a) })

/** [left] precedes [right] lexicographically; equal-length arrays. */
fun lexLess(
    left: List<IntExpr>,
    right: List<IntExpr>,
): BoolExpr = lex(left, right, strict = true)

/** [left] precedes or equals [right] lexicographically; equal-length arrays. */
fun lexLessOrEqual(
    left: List<IntExpr>,
    right: List<IntExpr>,
): BoolExpr = lex(left, right, strict = false)

/** `l₀ < r₀ ∨ (l₀ = r₀ ∧ lex(rest))`, built from the last position backwards. */
private fun lex(
    left: List<IntExpr>,
    right: List<IntExpr>,
    strict: Boolean,
): BoolExpr {
    require(left.size == right.size) { "Lexicographic ordering requires arrays of equal length" }
    return left.indices.reversed().fold<Int, BoolExpr>(BoolLiteral(!strict)) { rest, i ->
        BoolOr(IntLessThan(left[i], right[i]), BoolAnd(IntEquality(left[i], right[i]), rest))
    }
}
