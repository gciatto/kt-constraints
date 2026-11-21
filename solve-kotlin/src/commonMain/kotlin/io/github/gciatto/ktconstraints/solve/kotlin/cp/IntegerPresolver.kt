package io.github.gciatto.ktconstraints.solve.kotlin.cp

import io.github.gciatto.ktconstraints.core.AllDifferent
import io.github.gciatto.ktconstraints.core.BoolAnd
import io.github.gciatto.ktconstraints.core.BoolLiteral
import io.github.gciatto.ktconstraints.core.IntDomain
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntExpr
import io.github.gciatto.ktconstraints.core.IntInterval
import io.github.gciatto.ktconstraints.core.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.IntLessThan
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.model.ConstraintModel
import org.gciatto.kt.math.BigInteger

/**
 * Sound exact presolve for unconditionally asserted affine integer relations and assigned all-different values.
 * Every restriction is intersected with the original interval domain; intervals are never enumerated.
 * Reified/nested alternatives remain untouched. A finite pass budget returns conservative restrictions.
 */
internal class IntegerPresolver(
    private val maxPasses: Int = 64,
) {
    init {
        require(maxPasses > 0) { "The presolve pass budget must be positive" }
    }

    suspend fun run(
        model: ConstraintModel,
        checkpoint: suspend () -> Unit = {},
    ): PresolveResult {
        val store = ReversibleIntDomains(model.variables.filterIsInstance<IntVar>())
        val normalizer = AffineNormalizer(store)
        checkpoint()
        if (store.snapshot().values.any { it.isEmpty }) return result(store, conflict = true, fixedPoint = true)
        val constraints = assertions(model)
        repeat(maxPasses) {
            val revision = store.revision
            for (assertion in constraints) {
                checkpoint()
                if (propagate(assertion, store, normalizer, checkpoint)) return result(store, conflict = true, fixedPoint = true)
            }
            if (store.revision == revision) return result(store, conflict = false, fixedPoint = true)
        }
        return result(store, conflict = false, fixedPoint = false)
    }

    private fun assertions(model: ConstraintModel): List<PresolveAssertion> =
        buildList {
            val pending = ArrayDeque<PresolveAssertion>()
            model.constraints.forEachIndexed { index, expression ->
                pending.addLast(PresolveAssertion(expression, index))
                while (pending.isNotEmpty()) {
                    val current = pending.removeLast()
                    val node = current.expression
                    if (node is BoolAnd) {
                        pending.addLast(PresolveAssertion(node.right, index))
                        pending.addLast(PresolveAssertion(node.left, index))
                    } else {
                        add(current)
                    }
                }
            }
        }

    private suspend fun propagate(
        assertion: PresolveAssertion,
        store: ReversibleIntDomains,
        normalizer: AffineNormalizer,
        checkpoint: suspend () -> Unit,
    ): Boolean {
        val source = assertion.sourceIndex
        return when (val node = assertion.expression) {
            is BoolLiteral -> !node.value
            is IntEquality -> {
                val form = difference(node.left, node.right, normalizer) ?: return false
                inequality(form, store, source, checkpoint) || inequality(form.scaled(-BigInteger.ONE), store, source, checkpoint)
            }
            is IntLessOrEqual -> {
                val form = difference(node.left, node.right, normalizer) ?: return false
                inequality(form, store, source, checkpoint)
            }
            is IntLessThan -> {
                val form = difference(node.left, node.right, normalizer) ?: return false
                inequality(form.plus(AffineForm(constant = BigInteger.ONE)), store, source, checkpoint)
            }
            is AllDifferent -> allDifferent(node, store, normalizer, source, checkpoint)
            else -> false
        }
    }

    private fun difference(
        left: IntExpr,
        right: IntExpr,
        normalizer: AffineNormalizer,
    ): AffineForm? {
        val first = normalizer.normalize(left) ?: return null
        val second = normalizer.normalize(right) ?: return null
        return first.plus(second.scaled(-BigInteger.ONE))
    }

    /** Enforces the necessary individual bounds of sum(coefficients * variables) + constant <= 0. */
    private suspend fun inequality(
        form: AffineForm,
        store: ReversibleIntDomains,
        source: Int,
        checkpoint: suspend () -> Unit,
    ): Boolean {
        val minima = linkedMapOf<IntVar, BigInteger>()
        var minimum = form.constant
        for ((variable, coefficient) in form.coefficients) {
            checkpoint()
            val domain = store[variable]
            if (domain.isEmpty) return true
            val bound = if (coefficient.signum > 0) domain.min else domain.max
            val contribution = coefficient * requireNotNull(bound)
            minima[variable] = contribution
            minimum += contribution
        }
        if (minimum.signum > 0) return true
        for ((variable, coefficient) in form.coefficients) {
            checkpoint()
            val domain = store[variable]
            val otherMinimum = minimum - requireNotNull(minima[variable])
            val upper =
                if (coefficient.signum > 0) {
                    minOf(requireNotNull(domain.max), floorDivide(-otherMinimum, coefficient))
                } else {
                    requireNotNull(domain.max)
                }
            val lower =
                if (coefficient.signum < 0) {
                    maxOf(requireNotNull(domain.min), ceilDivide(-otherMinimum, coefficient))
                } else {
                    requireNotNull(domain.min)
                }
            val allowed = if (lower <= upper) IntDomain.of(lower, upper) else IntDomain.empty()
            store.narrow(variable, allowed, source)
            if (store[variable].isEmpty) return true
        }
        return false
    }

    private suspend fun allDifferent(
        constraint: AllDifferent,
        store: ReversibleIntDomains,
        normalizer: AffineNormalizer,
        source: Int,
        checkpoint: suspend () -> Unit,
    ): Boolean {
        val fixed = mutableSetOf<BigInteger>()
        for (operand in constraint.operands) {
            checkpoint()
            val form = normalizer.normalize(operand)
            if (form != null && form.coefficients.isEmpty() && !fixed.add(form.constant)) return true
        }
        if (fixed.isEmpty()) return false
        val ordered = fixed.sorted()
        for (operand in constraint.operands) {
            checkpoint()
            if (operand !is IntVar) continue
            val domain = store[operand]
            if (domain.min == domain.max) continue
            store.narrow(operand, withoutValues(domain, ordered), source)
            if (store[operand].isEmpty) return true
        }
        return false
    }

    private fun withoutValues(
        domain: IntDomain,
        values: List<BigInteger>,
    ): IntDomain =
        IntDomain.ofIntervals(
            buildList {
                for (interval in domain.intervals) {
                    var start = interval.min
                    for (value in values) {
                        if (value < start) continue
                        if (value > interval.max) break
                        if (value > start) add(IntInterval(start, value - 1))
                        start = value + 1
                    }
                    if (start <= interval.max) add(IntInterval(start, interval.max))
                }
            },
        )

    private fun floorDivide(
        numerator: BigInteger,
        denominator: BigInteger,
    ): BigInteger {
        val quotient = numerator / denominator
        val remainder = numerator.rem(denominator)
        return if (remainder.signum != 0 && numerator.signum != denominator.signum) quotient - 1 else quotient
    }

    private fun ceilDivide(
        numerator: BigInteger,
        denominator: BigInteger,
    ): BigInteger = -floorDivide(-numerator, denominator)

    private fun result(
        store: ReversibleIntDomains,
        conflict: Boolean,
        fixedPoint: Boolean,
    ): PresolveResult = PresolveResult(store.snapshot(), conflict, fixedPoint, store.reductions)
}
