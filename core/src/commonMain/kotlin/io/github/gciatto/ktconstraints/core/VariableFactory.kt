package io.github.gciatto.ktconstraints.core

import io.github.gciatto.ktconstraints.core.bitvector.BitVecVar
import io.github.gciatto.ktconstraints.core.bool.BoolVar
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.core.real.RealVar
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

    /** Creates a fresh bit-vector variable of a positive [width]. */
    fun bitVecVar(
        name: String,
        width: Int,
    ): BitVecVar = BitVecVar(next(), name, width)

    private fun next(): VariableId = VariableId(owner, nextId++)

    /** Factory methods. */
    companion object {
        /** Creates an independent scope identity. */
        @JvmStatic
        fun empty(): VariableFactory = VariableFactory()
    }
}
