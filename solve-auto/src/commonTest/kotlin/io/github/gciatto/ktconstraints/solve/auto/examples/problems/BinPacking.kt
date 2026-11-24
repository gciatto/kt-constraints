package io.github.gciatto.ktconstraints.solve.auto.examples.problems

// --8<-- [start:imports]
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.dsl.binPacking
import io.github.gciatto.ktconstraints.dsl.nValue
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.auto.AutoBackend
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.spi.withSolver
import org.gciatto.kt.math.BigInteger
// --8<-- [end:imports]

// --8<-- [start:model]

/** The bin of each item and the load of each bin. */
internal data class PackingVariables(
    val binOf: List<IntVar>,
    val loads: List<IntVar>,
)

/** Packs items of the given [sizes] into bins of [capacity], minimizing the bins in use. */
internal fun ConstraintScope.binPacking(
    sizes: List<BigInteger>,
    capacity: Int,
    useDecomposition: Boolean = false,
): PackingVariables {
    val binOf = List(sizes.size) { intVar("bin-$it", sizes.indices) }
    val loads = List(sizes.size) { intVar("load-$it", 0..capacity) }
    val packing = binPacking(binOf, sizes, loads)
    if (useDecomposition) {
        // --8<-- [start:decomposition]
        add(packing.decomposition)
        // --8<-- [end:decomposition]
    } else {
        // --8<-- [start:global]
        add(packing)
        // --8<-- [end:global]
    }
    // --8<-- [start:objective]
    minimize(nValue(binOf))
    // --8<-- [end:objective]
    return PackingVariables(binOf, loads)
}
// --8<-- [end:model]

// --8<-- [start:solve]

/** The bin of each item, the load of each bin and the number of bins in use. */
internal data class Packing(
    val binOf: List<Int>,
    val loads: List<Int>,
    val binCount: BigInteger,
)

/** Finds a packing that uses as few bins as possible. */
internal suspend fun solveBinPacking(
    sizes: List<BigInteger>,
    capacity: Int,
    useDecomposition: Boolean = false,
): Packing =
    ConstraintScope.empty {
        val variables = binPacking(sizes, capacity, useDecomposition)
        val model = buildModel()
        AutoBackend.load().use { auto ->
            auto.withSolver { solver ->
                val result = solver.solve(model)
                check(result.status == SolveStatus.OPTIMAL) { result.toString() }
                val assignment = checkNotNull(result.assignment)
                val packing =
                    Packing(
                        binOf = variables.binOf.map { checkNotNull(assignment.intValueOf(it)) },
                        loads = variables.loads.map { checkNotNull(assignment.intValueOf(it)) },
                        binCount = checkNotNull(result.objectiveValue),
                    )
                println("Bins: ${packing.binCount}; sorted loads: ${packing.loads.sorted()}")
                packing
            }
        }
    }
// --8<-- [end:solve]

// --8<-- [start:example]

/** Packs items of sizes 4, 3 and 2 into bins of capacity 5. */
internal suspend fun binPackingExample(useDecomposition: Boolean = false): Packing {
    val sizes = listOf(4, 3, 2).map { BigInteger.of(it) }
    val capacity = 5
    return solveBinPacking(sizes, capacity, useDecomposition)
}
// --8<-- [end:example]
