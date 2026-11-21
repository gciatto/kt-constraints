@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.gciatto.ktconstraints.js

import io.github.gciatto.ktconstraints.core.Transition
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsBigInt
import io.github.gciatto.ktconstraints.core.Automaton as CoreAutomaton

/** Deterministic automaton builder starting in [initial] and accepting in [accepting]; missing transitions reject. */
@JsExport
class Automaton(
    private val initial: Int,
    accepting: Array<Int>,
) {
    private val accepting = accepting.toSet()
    private val transitions = mutableListOf<Transition>()

    /** Reading [on] in state [from] moves to state [to]; returns this builder. */
    fun transition(
        from: Int,
        on: JsBigInt,
        to: Int,
    ): Automaton = apply { transitions += Transition(from, on.toBigInteger(), to) }

    internal fun build(): CoreAutomaton = CoreAutomaton(initial, accepting, transitions)
}
