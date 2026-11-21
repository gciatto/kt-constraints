@file:JvmMultifileClass
@file:JvmName("Expressions")

package io.github.gciatto.ktconstraints.dsl

import io.github.gciatto.ktconstraints.core.AllDifferent
import io.github.gciatto.ktconstraints.core.BoolCardinality
import io.github.gciatto.ktconstraints.core.BoolExpr
import io.github.gciatto.ktconstraints.core.CardinalityRelation
import io.github.gciatto.ktconstraints.core.Element
import io.github.gciatto.ktconstraints.core.IntExpr
import io.github.gciatto.ktconstraints.core.IntMax
import io.github.gciatto.ktconstraints.core.IntMin
import io.github.gciatto.ktconstraints.core.LinearSum
import org.gciatto.kt.math.BigInteger
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

/** Preserves a global pairwise-distinctness constraint. */
fun allDifferent(vararg operands: IntExpr): AllDifferent = AllDifferent(operands.asList())

/** Preserves a zero-based array selection expression. */
fun element(
    index: IntExpr,
    vararg values: IntExpr,
): Element = Element(index, values.asList())

/** Preserves the smallest of one or more integer operands. */
fun min(
    first: IntExpr,
    vararg rest: IntExpr,
): IntMin = IntMin(listOf(first) + rest)

/** Preserves the largest of one or more integer operands. */
fun max(
    first: IntExpr,
    vararg rest: IntExpr,
): IntMax = IntMax(listOf(first) + rest)

/** Preserves an exact integer sum, including the empty sum zero. */
fun sum(vararg terms: IntExpr): LinearSum = LinearSum(terms.asList(), List(terms.size) { BigInteger.ONE })

/** Requires at most [count] operands to be true. */
fun atMost(
    count: Int,
    vararg operands: BoolExpr,
): BoolCardinality = BoolCardinality(operands.asList(), count, CardinalityRelation.AT_MOST)

/** Requires exactly [count] operands to be true. */
fun exactly(
    count: Int,
    vararg operands: BoolExpr,
): BoolCardinality = BoolCardinality(operands.asList(), count, CardinalityRelation.EXACTLY)

/** Requires at least [count] operands to be true. */
fun atLeast(
    count: Int,
    vararg operands: BoolExpr,
): BoolCardinality = BoolCardinality(operands.asList(), count, CardinalityRelation.AT_LEAST)
