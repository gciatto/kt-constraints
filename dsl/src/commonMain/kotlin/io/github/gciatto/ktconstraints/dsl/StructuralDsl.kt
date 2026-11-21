@file:JvmMultifileClass
@file:JvmName("Expressions")

package io.github.gciatto.ktconstraints.dsl

import io.github.gciatto.ktconstraints.core.BinPacking
import io.github.gciatto.ktconstraints.core.Circuit
import io.github.gciatto.ktconstraints.core.Cumulative
import io.github.gciatto.ktconstraints.core.IntExpr
import io.github.gciatto.ktconstraints.core.Inverse
import io.github.gciatto.ktconstraints.core.NoOverlap2D
import io.github.gciatto.ktconstraints.core.SubCircuit
import org.gciatto.kt.math.BigInteger
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

/** [direct] and [inverse] are mutually inverse zero-based permutations. */
fun inverse(
    direct: List<IntExpr>,
    inverse: List<IntExpr>,
): Inverse = Inverse(direct, inverse)

/** Zero-based [successors] form one cycle through every node. */
fun circuit(vararg successors: IntExpr): Circuit = Circuit(successors.asList())

/** Tasks never use more than [capacity] at once; durations positive, demands non-negative. */
fun cumulative(
    starts: List<IntExpr>,
    durations: List<BigInteger>,
    demands: List<BigInteger>,
    capacity: BigInteger,
): Cumulative = Cumulative(starts, durations, demands, capacity)

/** Tasks with positive [durations] never overlap. */
fun disjunctive(
    starts: List<IntExpr>,
    durations: List<BigInteger>,
): Cumulative = Cumulative(starts, durations, List(starts.size) { BigInteger.ONE }, BigInteger.ONE)

/** Nodes pointing to themselves are excluded; the other zero-based [successors] form one cycle, if any. */
fun subCircuit(vararg successors: IntExpr): SubCircuit = SubCircuit(successors.asList())

/** Item `i` of size `sizes[i]` goes into the zero-based bin `bins[i]`; `loads[b]` is the total size in bin `b`. */
fun binPacking(
    bins: List<IntExpr>,
    sizes: List<BigInteger>,
    loads: List<IntExpr>,
): BinPacking = BinPacking(bins, sizes, loads)

/** Rectangles at `(xs[i], ys[i])` with positive [widths] and [heights] never overlap; they may touch. */
fun noOverlap2D(
    xs: List<IntExpr>,
    ys: List<IntExpr>,
    widths: List<BigInteger>,
    heights: List<BigInteger>,
): NoOverlap2D = NoOverlap2D(xs, ys, widths, heights)
