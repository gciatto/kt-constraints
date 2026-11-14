package io.github.gciatto.ktconstraints.solve

import io.github.gciatto.ktconstraints.core.BoolVar
import io.github.gciatto.ktconstraints.core.IntVar
import org.gciatto.kt.math.BigInteger

internal class ImmutableAssignment(
    booleans: Map<BoolVar, Boolean>,
    integers: Map<IntVar, BigInteger>,
) : Assignment {
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
}
