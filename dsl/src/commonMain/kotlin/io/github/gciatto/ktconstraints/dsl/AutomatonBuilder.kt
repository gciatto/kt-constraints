package io.github.gciatto.ktconstraints.dsl

import io.github.gciatto.ktconstraints.core.Automaton
import io.github.gciatto.ktconstraints.core.Transition
import org.gciatto.kt.math.BigInteger

/** Collects the transitions of a deterministic automaton inside [automaton]. */
class AutomatonBuilder internal constructor() {
    private val transitions = mutableListOf<Transition>()

    /** Reading [on] in state [from] moves to state [to]. */
    fun transition(
        from: Int,
        on: Long,
        to: Int,
    ) = transition(from, BigInteger.of(on), to)

    /** Reading [on] in state [from] moves to state [to]. */
    fun transition(
        from: Int,
        on: BigInteger,
        to: Int,
    ) {
        transitions += Transition(from, on, to)
    }

    internal fun build(
        initial: Int,
        accepting: Set<Int>,
    ): Automaton = Automaton(initial, accepting, transitions)
}
