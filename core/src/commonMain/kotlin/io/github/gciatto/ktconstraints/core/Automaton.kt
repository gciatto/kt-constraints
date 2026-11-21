package io.github.gciatto.ktconstraints.core

import org.gciatto.kt.math.BigInteger

/** Deterministic finite automaton over integer symbols; states are `0 until states`, missing transitions reject. */
class Automaton(
    /** Initial state. */
    val initial: Int,
    accepting: Set<Int>,
    transitions: Collection<Transition>,
) {
    /** Detached accepting states. */
    val accepting: Set<Int> = buildSet { addAll(accepting) }

    /** Detached transitions, in declaration order. */
    val transitions: List<Transition> = buildList { addAll(transitions) }

    private val table: Map<Pair<Int, BigInteger>, Int> = this.transitions.associate { (it.from to it.symbol) to it.to }

    /** Number of states, the largest mentioned state plus one. */
    val states: Int = (listOf(initial) + this.accepting + this.transitions.flatMap { listOf(it.from, it.to) }).max() + 1

    init {
        require((listOf(initial) + this.accepting + this.transitions.flatMap { listOf(it.from, it.to) }).all { it >= 0 }) {
            "Automaton states must be non-negative"
        }
        require(table.size == this.transitions.size) { "Automaton must be deterministic: one transition per state and symbol" }
    }

    /** The state reached from [state] on [symbol], or null when the word is rejected. */
    fun next(
        state: Int,
        symbol: BigInteger,
    ): Int? = table[state to symbol]

    override fun equals(other: Any?): Boolean =
        other is Automaton && initial == other.initial && accepting == other.accepting && transitions == other.transitions

    override fun hashCode(): Int = (31 * initial + accepting.hashCode()) * 31 + transitions.hashCode()

    override fun toString(): String = "automaton($initial, $accepting, $transitions)"
}
