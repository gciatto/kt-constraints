package io.github.gciatto.ktconstraints.solve.auto.examples.problems

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Checks both the preserved global and its variable-free decomposition. */
class BinPackingSpec :
    FunSpec({
        listOf(false, true).forEach { useDecomposition ->
            test("minimize bins with decomposition=$useDecomposition") {
                val sizes = listOf(4, 3, 2)
                val packing = binPackingExample(useDecomposition)
                packing.binCount shouldBe BigInteger.TWO
                packing.binOf.toSet().size shouldBe 2
                packing.loads.sorted() shouldBe listOf(0, 4, 5)
                packing.loads.forEachIndexed { bin, load ->
                    sizes.indices.filter { packing.binOf[it] == bin }.sumOf { sizes[it] } shouldBe load
                }
            }
        }
    })
