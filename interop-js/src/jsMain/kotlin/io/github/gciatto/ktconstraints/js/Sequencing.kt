@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.gciatto.ktconstraints.js

import io.github.gciatto.ktconstraints.core.BinPacking
import io.github.gciatto.ktconstraints.core.NoOverlap2D
import io.github.gciatto.ktconstraints.core.Regular
import io.github.gciatto.ktconstraints.core.SubCircuit
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsBigInt
import io.github.gciatto.ktconstraints.dsl.nValue as dslNValue

/** [automaton] accepts the word spelled by [sequence]. */
@JsExport
fun regular(
    sequence: Array<IntExpression>,
    automaton: Automaton,
): BoolExpression = BoolExpression(Regular(sequence.map { it.expr }, automaton.build()))

/** Nodes pointing to themselves are excluded; the other zero-based [successors] form one cycle, if any. */
@JsExport
fun subCircuit(successors: Array<IntExpression>): BoolExpression = BoolExpression(SubCircuit(successors.map { it.expr }))

/** Item `i` of size `sizes[i]` goes into the zero-based bin `bins[i]`; `loads[b]` is the total size in bin `b`. */
@JsExport
fun binPacking(
    bins: Array<IntExpression>,
    sizes: Array<JsBigInt>,
    loads: Array<IntExpression>,
): BoolExpression = BoolExpression(BinPacking(bins.map { it.expr }, sizes.map { it.toBigInteger() }, loads.map { it.expr }))

/** Rectangles at `(xs[i], ys[i])` with positive [widths] and [heights] never overlap; they may touch. */
@JsExport
fun noOverlap2D(
    xs: Array<IntExpression>,
    ys: Array<IntExpression>,
    widths: Array<JsBigInt>,
    heights: Array<JsBigInt>,
): BoolExpression =
    BoolExpression(NoOverlap2D(xs.map { it.expr }, ys.map { it.expr }, widths.map { it.toBigInteger() }, heights.map { it.toBigInteger() }))

/** Number of distinct values among [operands]. */
@JsExport
fun nValue(operands: Array<IntExpression>): IntExpression = IntExpression(dslNValue(operands.map { it.expr }))
