@file:JvmMultifileClass
@file:JvmName("Expressions")

package io.github.gciatto.ktconstraints.dsl

import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.bool.BoolNot
import io.github.gciatto.ktconstraints.core.bool.BoolToInt
import io.github.gciatto.ktconstraints.core.integer.arithmetic.LinearSum
import io.github.gciatto.ktconstraints.model.ConstraintScope
import org.gciatto.kt.math.BigInteger
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

/**
 * Soft constraints: minimizes the total non-negative weight of the violated constraints (weighted MaxSAT) and returns
 * that penalty, e.g. `minimizeViolations((x ge 2) to 3, (y le 0) to 1)`. Replaces any previous objective.
 */
fun ConstraintScope.minimizeViolations(vararg soft: Pair<BoolExpr, Long>): LinearSum {
    require(soft.all { it.second >= 0 }) { "Soft-constraint weights must be non-negative" }
    val penalty = LinearSum(soft.map { BoolToInt(BoolNot(it.first)) }, soft.map { BigInteger.of(it.second) })
    minimize(penalty)
    return penalty
}
