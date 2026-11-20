package io.github.gciatto.ktconstraints.solve

import io.github.gciatto.ktconstraints.core.BitVecVar
import io.github.gciatto.ktconstraints.core.BoolVar
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.RealVar
import io.github.gciatto.ktconstraints.core.Valuation
import org.gciatto.kt.math.BigInteger
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/** Read-only projection of one solver assignment. */
interface Assignment : Valuation {
    /** Returns null when the variable is not assigned or not part of the projection. */
    override fun valueOf(variable: BoolVar): Boolean?

    /** Returns the exact integer value, or null when outside the assignment projection. */
    override fun valueOf(variable: IntVar): BigInteger?

    /** Returns the integer value as an Int, or null when unassigned; throws [ArithmeticException] if it does not fit. */
    fun intValueOf(variable: IntVar): Int? = valueOf(variable)?.toIntExact()

    /** Returns the integer value as a Long, or null when unassigned; throws [ArithmeticException] if it does not fit. */
    fun longValueOf(variable: IntVar): Long? = valueOf(variable)?.toLongExact()

    /** Returns the exact real value, or null when outside the assignment projection. */
    override fun valueOf(variable: RealVar): Rational?

    /** Returns the unsigned bit-vector value, or null when outside the assignment projection. */
    override fun valueOf(variable: BitVecVar): BigInteger?

    /** Factories for detached typed assignments. */
    companion object {
        /** Copies values and rejects integer values outside their domains and bit-vector values outside their widths. */
        @JvmStatic
        @JvmOverloads
        fun of(
            booleans: Map<BoolVar, Boolean> = emptyMap(),
            integers: Map<IntVar, BigInteger> = emptyMap(),
            reals: Map<RealVar, Rational> = emptyMap(),
            bitVectors: Map<BitVecVar, BigInteger> = emptyMap(),
        ): Assignment = ImmutableAssignment(booleans, integers, reals, bitVectors)

        /** Creates an assignment with no projected variables. */
        @JvmStatic
        fun empty(): Assignment = of()
    }
}
