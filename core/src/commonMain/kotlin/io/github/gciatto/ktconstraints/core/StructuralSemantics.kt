package io.github.gciatto.ktconstraints.core

import org.gciatto.kt.math.BigInteger

/** Direct ground semantics of structural globals, independent of their decompositions. */
internal object StructuralSemantics {
    fun holds(
        node: DecomposableConstraint,
        values: List<BigInteger>,
    ): Boolean =
        when (node) {
            is Inverse -> inverse(values.subList(0, node.direct.size), values.subList(node.direct.size, values.size))
            is Circuit -> circuit(values)
            is Cumulative -> cumulative(node, values)
            else -> throw IllegalArgumentException("Unsupported global: $node")
        }

    private fun indices(
        values: List<BigInteger>,
        size: Int,
    ): List<Int>? = if (values.all { it.signum >= 0 && it < BigInteger.of(size) }) values.map { it.toIntExact() } else null

    private fun inverse(
        direct: List<BigInteger>,
        inverse: List<BigInteger>,
    ): Boolean {
        val forward = indices(direct, direct.size) ?: return false
        val backward = indices(inverse, inverse.size) ?: return false
        return forward.indices.all { backward[forward[it]] == it }
    }

    /** The walk from node 0 first returns to it after exactly `n` steps. */
    private fun circuit(successors: List<BigInteger>): Boolean {
        val next = indices(successors, successors.size) ?: return false
        if (next.isEmpty()) return true
        var position = 0
        for (step in 1..next.size) {
            position = next[position]
            if (position == 0) return step == next.size
        }
        return false
    }

    private fun cumulative(
        node: Cumulative,
        starts: List<BigInteger>,
    ): Boolean =
        starts.indices.all { j ->
            val load =
                starts.indices
                    .filter { i -> starts[i] <= starts[j] && starts[j] < starts[i] + node.durations[i] }
                    .fold(BigInteger.ZERO) { total, i -> total + node.demands[i] }
            load <= node.capacity
        }
}
