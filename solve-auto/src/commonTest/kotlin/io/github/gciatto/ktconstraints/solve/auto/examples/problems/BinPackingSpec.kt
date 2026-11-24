package io.github.gciatto.ktconstraints.solve.auto.examples.problems

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Checks both the preserved global and its variable-free decomposition. */
class BinPackingSpec :
    FunSpec({
        listOf(false, true).forEach { useDecomposition ->
            test("minimize bins with decomposition=$useDecomposition") {
                val sizes = listOf(4, 3, 2).map { BigInteger.of(it) }
                val (result, binOf, actualLoads) = binPackingExample(useDecomposition)
                result.objectiveValue.toString() shouldBe "2"
                binOf.toSet().size shouldBe 2
                actualLoads.sorted() shouldBe listOf(0, 4, 5)
                actualLoads.indices.forEach { bin ->
                    sizes.indices.filter { binOf[it] == bin }.sumOf { sizes[it].toIntExact() } shouldBe actualLoads[bin]
                }
            }
        }
    })
