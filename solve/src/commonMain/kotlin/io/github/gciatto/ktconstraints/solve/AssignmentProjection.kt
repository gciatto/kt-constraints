package io.github.gciatto.ktconstraints.solve

import io.github.gciatto.ktconstraints.core.BoolVar
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.core.Variable
import org.gciatto.kt.math.BigInteger

/** Copies a requested projection, requiring a value for every requested variable. */
fun Assignment.project(variables: Collection<Variable>): Assignment {
    val booleans = linkedMapOf<BoolVar, Boolean>()
    val integers = linkedMapOf<IntVar, BigInteger>()
    for (variable in variables) {
        when (variable) {
            is BoolVar -> booleans[variable] = requireNotNull(valueOf(variable)) { "Missing projected value: ${variable.name}" }
            is IntVar -> integers[variable] = requireNotNull(valueOf(variable)) { "Missing projected value: ${variable.name}" }
            else -> throw IllegalArgumentException("Unsupported projection variable: ${variable.name}")
        }
    }
    return Assignment.of(booleans, integers)
}
