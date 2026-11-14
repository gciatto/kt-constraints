package io.github.gciatto.ktconstraints.solve

import io.github.gciatto.ktconstraints.core.BoolVar
import io.github.gciatto.ktconstraints.core.IntVar
import org.gciatto.kt.math.BigInteger
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/** Read-only projection of one solver assignment. */
interface Assignment {
    /** Returns null when the variable is not assigned or not part of the projection. */
    fun valueOf(variable: BoolVar): Boolean?

    /** Returns the exact integer value, or null when outside the assignment projection. */
    fun valueOf(variable: IntVar): BigInteger?

    /** Factories for detached typed assignments. */
    companion object {
        /** Copies values and rejects integer values outside their declared domains. */
        @JvmStatic
        @JvmOverloads
        fun of(
            booleans: Map<BoolVar, Boolean> = emptyMap(),
            integers: Map<IntVar, BigInteger> = emptyMap(),
        ): Assignment = ImmutableAssignment(booleans, integers)

        /** Creates an assignment with no projected variables. */
        @JvmStatic
        fun empty(): Assignment = of()
    }
}
