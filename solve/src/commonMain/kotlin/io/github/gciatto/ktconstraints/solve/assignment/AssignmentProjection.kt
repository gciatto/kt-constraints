package io.github.gciatto.ktconstraints.solve.assignment
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.Variable
import io.github.gciatto.ktconstraints.core.bitvector.BitVecVar
import io.github.gciatto.ktconstraints.core.bool.BoolVar
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.core.real.RealVar
import org.gciatto.kt.math.BigInteger

/** Copies a requested projection, requiring a value for every requested variable. */
fun Assignment.project(variables: Collection<Variable>): Assignment {
    val booleans = linkedMapOf<BoolVar, Boolean>()
    val integers = linkedMapOf<IntVar, BigInteger>()
    val reals = linkedMapOf<RealVar, Rational>()
    val bitVectors = linkedMapOf<BitVecVar, BigInteger>()
    for (variable in variables) {
        when (variable) {
            is BoolVar -> booleans[variable] = requireNotNull(valueOf(variable)) { "Missing projected value: ${variable.name}" }
            is IntVar -> integers[variable] = requireNotNull(valueOf(variable)) { "Missing projected value: ${variable.name}" }
            is RealVar -> reals[variable] = requireNotNull(valueOf(variable)) { "Missing projected value: ${variable.name}" }
            is BitVecVar -> bitVectors[variable] = requireNotNull(valueOf(variable)) { "Missing projected value: ${variable.name}" }
            else -> throw IllegalArgumentException("Unsupported projection variable: ${variable.name}")
        }
    }
    return Assignment.of(booleans, integers, reals, bitVectors)
}
