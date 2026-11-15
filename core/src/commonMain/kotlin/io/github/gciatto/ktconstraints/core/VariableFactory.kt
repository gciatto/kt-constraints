package io.github.gciatto.ktconstraints.core

import kotlin.jvm.JvmStatic

/** Owns variable identities for one symbolic scope without exposing internal constructors. */
class VariableFactory private constructor() {
    private val owner = Any()
    private var nextId = 0

    /** Creates a fresh Boolean variable. */
    fun boolVar(name: String): BoolVar = BoolVar(next(), name)

    /** Creates a fresh finite-domain integer variable. */
    fun intVar(
        name: String,
        domain: IntDomain,
    ): IntVar = IntVar(next(), name, domain)

    /** Creates a fresh unbounded real variable. */
    fun realVar(name: String): RealVar = RealVar(next(), name)

    private fun next(): VariableId = VariableId(owner, nextId++)

    /** Factory methods. */
    companion object {
        /** Creates an independent scope identity. */
        @JvmStatic
        fun empty(): VariableFactory = VariableFactory()
    }
}
