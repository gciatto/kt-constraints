package io.github.gciatto.ktconstraints.test.solve

import io.github.gciatto.ktconstraints.core.BoolVar
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.Assignment
import io.github.gciatto.ktconstraints.solve.spi.SupportReport
import io.github.gciatto.ktconstraints.solve.spi.UnsupportedModelException
import org.gciatto.kt.math.BigInteger

internal class OracleCandidates(
    private val model: ConstraintModel,
) {
    private val count = cardinality()
    private var position = 0
    private val values: Map<IntVar, List<BigInteger>> =
        if (count == 0) {
            emptyMap()
        } else {
            model.variables.filterIsInstance<IntVar>().associateWith { variable ->
                buildList {
                    for (interval in variable.domain.intervals) {
                        var value = interval.min
                        while (value <= interval.max) {
                            add(value)
                            value += BigInteger.ONE
                        }
                    }
                }
            }
        }

    val exhausted: Boolean get() = position == count

    fun next(): Assignment {
        check(!exhausted)
        var ordinal = position++
        val booleans = linkedMapOf<BoolVar, Boolean>()
        val integers = linkedMapOf<IntVar, BigInteger>()
        for (variable in model.variables) {
            when (variable) {
                is BoolVar -> {
                    booleans[variable] = ordinal % 2 != 0
                    ordinal /= 2
                }
                is IntVar -> {
                    val domain = values.getValue(variable)
                    integers[variable] = domain[ordinal % domain.size]
                    ordinal /= domain.size
                }
            }
        }
        return Assignment.of(booleans, integers)
    }

    private fun cardinality(): Int {
        if (model.variables.filterIsInstance<IntVar>().any { it.domain.isEmpty }) return 0
        var product = BigInteger.ONE
        for (variable in model.variables) {
            product *=
                when (variable) {
                    is BoolVar -> BigInteger.TWO
                    is IntVar -> variable.domain.cardinality
                    else -> error("Unsupported variable")
                }
            if (product > BigInteger.of(FiniteOracle.MAX_CANDIDATES)) {
                throw UnsupportedModelException(SupportReport(listOf("Oracle candidate cap exceeded: $product")))
            }
        }
        return product.toIntExact()
    }
}
