@file:JvmMultifileClass
@file:JvmName("Expressions")

package io.github.gciatto.ktconstraints.dsl

import io.github.gciatto.ktconstraints.core.global.extensional.Automaton
import io.github.gciatto.ktconstraints.core.global.extensional.Regular
import io.github.gciatto.ktconstraints.core.integer.IntExpr
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

/** A deterministic automaton starting in [initial] and accepting in [accepting], with the declared [transitions]. */
fun automaton(
    initial: Int,
    accepting: Set<Int>,
    transitions: AutomatonBuilder.() -> Unit,
): Automaton = AutomatonBuilder().apply(transitions).build(initial, accepting)

/** [automaton] accepts the word spelled by [sequence]. */
fun regular(
    automaton: Automaton,
    vararg sequence: IntExpr,
): Regular = regular(automaton, sequence.asList())

/** [automaton] accepts the word spelled by [sequence]. */
fun regular(
    automaton: Automaton,
    sequence: List<IntExpr>,
): Regular = Regular(sequence, automaton)
