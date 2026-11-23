package io.github.gciatto.ktconstraints.core.global
import io.github.gciatto.ktconstraints.core.evaluation.fold
import io.github.gciatto.ktconstraints.core.global.extensional.Automaton
import io.github.gciatto.ktconstraints.core.global.extensional.Regular
import io.github.gciatto.ktconstraints.core.global.graph.Circuit
import io.github.gciatto.ktconstraints.core.global.graph.Inverse
import io.github.gciatto.ktconstraints.core.global.graph.SubCircuit
import io.github.gciatto.ktconstraints.core.global.scheduling.BinPacking
import io.github.gciatto.ktconstraints.core.global.scheduling.Cumulative
import io.github.gciatto.ktconstraints.core.global.scheduling.NoOverlap2D
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
            is Regular -> regular(node.automaton, values)
            is SubCircuit -> subCircuit(values)
            is BinPacking -> binPacking(node, values.subList(0, node.bins.size), values.subList(node.bins.size, values.size))
            is NoOverlap2D -> noOverlap2D(node, values.subList(0, node.xs.size), values.subList(node.xs.size, values.size))
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

    private fun regular(
        automaton: Automaton,
        word: List<BigInteger>,
    ): Boolean {
        var state: Int = automaton.initial
        for (symbol in word) state = automaton.next(state, symbol) ?: return false
        return state in automaton.accepting
    }

    /** A permutation whose non-fixed points form one cycle. */
    private fun subCircuit(successors: List<BigInteger>): Boolean {
        val next = indices(successors, successors.size) ?: return false
        if (next.toSet().size != next.size) return false
        val included = next.indices.filter { next[it] != it }
        val start = included.firstOrNull() ?: return true
        var position = start
        repeat(included.size) { position = next[position] }
        return position == start && generateSequence(next[start]) { next[it] }.takeWhile { it != start }.count() + 1 == included.size
    }

    private fun binPacking(
        node: BinPacking,
        bins: List<BigInteger>,
        loads: List<BigInteger>,
    ): Boolean {
        val assigned = indices(bins, loads.size) ?: return false
        return loads.indices.all { bin ->
            loads[bin] == assigned.indices.filter { assigned[it] == bin }.fold(BigInteger.ZERO) { total, item -> total + node.sizes[item] }
        }
    }

    private fun noOverlap2D(
        node: NoOverlap2D,
        xs: List<BigInteger>,
        ys: List<BigInteger>,
    ): Boolean =
        xs.indices.all { i ->
            (i + 1 until xs.size).all { j ->
                xs[i] + node.widths[i] <= xs[j] ||
                    xs[j] + node.widths[j] <= xs[i] ||
                    ys[i] + node.heights[i] <= ys[j] ||
                    ys[j] + node.heights[j] <= ys[i]
            }
        }
}
