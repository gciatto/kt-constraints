@file:JvmMultifileClass
@file:JvmName("Expressions")

package io.github.gciatto.ktconstraints.dsl

import io.github.gciatto.ktconstraints.core.bool.BoolAnd
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.bool.BoolLiteral
import io.github.gciatto.ktconstraints.core.bool.BoolNot
import io.github.gciatto.ktconstraints.core.bool.BoolOr
import io.github.gciatto.ktconstraints.core.bool.BoolToInt
import io.github.gciatto.ktconstraints.core.integer.IntExpr
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.arithmetic.LinearSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import org.gciatto.kt.math.BigInteger
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

/** 1 when this holds, 0 otherwise. */
fun BoolExpr.toInt(): BoolToInt = BoolToInt(this)

/** Number of [operands] equal to [value]. */
fun count(
    value: IntExpr,
    vararg operands: IntExpr,
): LinearSum = count(value, operands.asList())

/** Number of [operands] equal to [value]. */
fun count(
    value: IntExpr,
    operands: List<IntExpr>,
): LinearSum = ones(operands.map { IntEquality(it, value) })

/** Number of [operands] whose value is one of [values]. */
fun among(
    values: Collection<BigInteger>,
    vararg operands: IntExpr,
): LinearSum = among(values, operands.asList())

/** Number of [operands] whose value is one of [values]. */
fun among(
    values: Collection<BigInteger>,
    operands: List<IntExpr>,
): LinearSum =
    ones(
        operands.map { operand ->
            values.map<BigInteger, BoolExpr> { IntEquality(operand, IntLiteral(it)) }.reduceOrNull(::BoolOr) ?: BoolLiteral(false)
        },
    )

/** Each `values[k]` occurs exactly `counts[k]` times among [operands]; other values are unconstrained. */
fun globalCardinality(
    operands: List<IntExpr>,
    values: List<BigInteger>,
    counts: List<IntExpr>,
): BoolExpr {
    require(values.size == counts.size) { "globalCardinality requires one count per value" }
    return BoolAnd.of(
        values.zip(counts) { value, occurrences -> IntEquality(count(IntLiteral(value), operands), occurrences) },
    )
}

/** Number of distinct values among [operands]. */
fun nValue(vararg operands: IntExpr): LinearSum = nValue(operands.asList())

/** Number of distinct values among [operands]: those differing from every earlier operand. */
fun nValue(operands: List<IntExpr>): LinearSum =
    ones(operands.indices.map { i -> BoolAnd.of((0 until i).map { j -> BoolNot(IntEquality(operands[i], operands[j])) }) })

private fun ones(conditions: List<BoolExpr>): LinearSum =
    LinearSum(
        conditions.map {
            BoolToInt(it)
        },
        List(conditions.size) { BigInteger.ONE },
    )
