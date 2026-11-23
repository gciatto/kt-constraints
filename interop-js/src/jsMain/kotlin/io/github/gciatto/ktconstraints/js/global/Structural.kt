@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.gciatto.ktconstraints.js.global
import io.github.gciatto.ktconstraints.core.global.graph.Circuit
import io.github.gciatto.ktconstraints.core.global.graph.Inverse
import io.github.gciatto.ktconstraints.core.global.scheduling.Cumulative
import io.github.gciatto.ktconstraints.js.expression.BoolExpression
import io.github.gciatto.ktconstraints.js.expression.IntExpression
import io.github.gciatto.ktconstraints.js.platform.toBigInteger
import org.gciatto.kt.math.BigInteger
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsBigInt

/** [direct] and [inverse] are mutually inverse zero-based permutations. */
@JsExport
fun inverse(
    direct: Array<IntExpression>,
    inverse: Array<IntExpression>,
): BoolExpression = BoolExpression(Inverse(direct.map { it.expr }, inverse.map { it.expr }))

/** Zero-based [successors] form one cycle through every node. */
@JsExport
fun circuit(successors: Array<IntExpression>): BoolExpression = BoolExpression(Circuit(successors.map { it.expr }))

/** Tasks never use more than [capacity] at once; durations positive, demands non-negative. */
@JsExport
fun cumulative(
    starts: Array<IntExpression>,
    durations: Array<JsBigInt>,
    demands: Array<JsBigInt>,
    capacity: JsBigInt,
): BoolExpression =
    BoolExpression(
        Cumulative(starts.map { it.expr }, durations.map { it.toBigInteger() }, demands.map { it.toBigInteger() }, capacity.toBigInteger()),
    )

/** Tasks with positive [durations] never overlap. */
@JsExport
fun disjunctive(
    starts: Array<IntExpression>,
    durations: Array<JsBigInt>,
): BoolExpression =
    BoolExpression(Cumulative(starts.map { it.expr }, durations.map { it.toBigInteger() }, starts.map { BigInteger.ONE }, BigInteger.ONE))
