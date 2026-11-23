package io.github.gciatto.ktconstraints.solve.assignment
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.bitvector.BitVecVar
import io.github.gciatto.ktconstraints.core.bool.BoolVar
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.core.real.RealVar
import org.gciatto.kt.math.BigInteger

internal class ImmutableAssignment(
    booleans: Map<BoolVar, Boolean>,
    integers: Map<IntVar, BigInteger>,
    reals: Map<RealVar, Rational> = emptyMap(),
    bitVectors: Map<BitVecVar, BigInteger> = emptyMap(),
) : Assignment {
    private val reals: Map<RealVar, Rational> = buildMap { putAll(reals) }
    private val bitVectors: Map<BitVecVar, BigInteger> =
        buildMap {
            bitVectors.forEach { (variable, value) ->
                require(value.signum >= 0 && value.bitLength <= variable.width) { "Value $value does not fit in ${variable.name}" }
                put(variable, value)
            }
        }

    private val booleans: Map<BoolVar, Boolean> = buildMap { putAll(booleans) }
    private val integers: Map<IntVar, BigInteger> =
        buildMap {
            integers.forEach { (variable, value) ->
                require(value in variable.domain) { "Value $value is outside the domain of ${variable.name}" }
                put(variable, value)
            }
        }

    override fun valueOf(variable: BoolVar): Boolean? = booleans[variable]

    override fun valueOf(variable: IntVar): BigInteger? = integers[variable]

    override fun valueOf(variable: RealVar): Rational? = reals[variable]

    override fun valueOf(variable: BitVecVar): BigInteger? = bitVectors[variable]
}
