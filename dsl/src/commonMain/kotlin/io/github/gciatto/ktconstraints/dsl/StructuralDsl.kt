@file:JvmMultifileClass
@file:JvmName("Expressions")

package io.github.gciatto.ktconstraints.dsl

import io.github.gciatto.ktconstraints.core.Circuit
import io.github.gciatto.ktconstraints.core.Cumulative
import io.github.gciatto.ktconstraints.core.IntExpr
import io.github.gciatto.ktconstraints.core.Inverse
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
