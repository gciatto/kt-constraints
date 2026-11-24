package io.github.gciatto.ktconstraints.solve.auto.examples.problems

// --8<-- [start:imports]
import io.github.gciatto.ktconstraints.dsl.binPacking
import io.github.gciatto.ktconstraints.dsl.nValue
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.auto.solveAuto
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger
// --8<-- [end:imports]

internal object BinPacking {
    suspend fun run(useDecomposition: Boolean) {
        // --8<-- [start:model]
        val sizes = listOf(4, 3, 2).map { BigInteger.of(it) }
        val capacity = 5
        val scope = ConstraintScope.empty()
        val bins = List(sizes.size) { scope.intVar("bin-$it", sizes.indices) }
        val loads = List(sizes.size) { scope.intVar("load-$it", 0..capacity) }
        val packing = binPacking(bins, sizes, loads)
        // --8<-- [end:model]
        if (useDecomposition) {
            // --8<-- [start:decomposition]
            scope.add(packing.decomposition)
            // --8<-- [end:decomposition]
        } else {
            // --8<-- [start:global]
            scope.add(packing)
            // --8<-- [end:global]
        }
        // --8<-- [start:objective]
        scope.minimize(nValue(bins))
        // --8<-- [end:objective]
        // --8<-- [start:solve]
        val result = solveAuto(scope.build())
        check(result.status == SolveStatus.OPTIMAL)
        val assignment = requireNotNull(result.assignment)
        val binOf = bins.map { requireNotNull(assignment.intValueOf(it)) }
        val actualLoads = loads.map { requireNotNull(assignment.intValueOf(it)) }
        println("Bins: ${result.objectiveValue}; sorted loads: ${actualLoads.sorted()}")
        // --8<-- [end:solve]
        result.objectiveValue.toString() shouldBe "2"
        binOf.toSet().size shouldBe 2
        actualLoads.sorted() shouldBe listOf(0, 4, 5)
        loads.indices.forEach { bin ->
            sizes.indices.filter { binOf[it] == bin }.sumOf { sizes[it].toIntExact() } shouldBe actualLoads[bin]
        }
    }
}

/** Checks both the preserved global and its variable-free decomposition. */
class BinPackingSpec :
    FunSpec({
        listOf(false, true).forEach { useDecomposition ->
            test("minimize bins with decomposition=$useDecomposition") {
                BinPacking.run(useDecomposition)
            }
        }
    })
