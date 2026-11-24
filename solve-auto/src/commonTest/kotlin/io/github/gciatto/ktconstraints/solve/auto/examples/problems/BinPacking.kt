package io.github.gciatto.ktconstraints.solve.auto.examples.problems

// --8<-- [start:imports]
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.dsl.binPacking
import io.github.gciatto.ktconstraints.dsl.nValue
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.auto.AutoBackend
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import org.gciatto.kt.math.BigInteger
// --8<-- [end:imports]

// --8<-- [start:model]
internal fun ConstraintScope.binPacking(
    sizes: List<BigInteger>,
    capacity: Int,
    useDecomposition: Boolean = false,
): Pair<List<IntVar>, List<IntVar>> {
    val bins = List(sizes.size) { intVar("bin-$it", sizes.indices) }
    val loads = List(sizes.size) { intVar("load-$it", 0..capacity) }
    val packing = binPacking(bins, sizes, loads)
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
    minimize(nValue(bins))
    // --8<-- [end:objective]
    return bins to loads
}
// --8<-- [end:model]

// --8<-- [start:solve]
internal suspend fun solveBinPacking(
    sizes: List<BigInteger>,
    capacity: Int,
    useDecomposition: Boolean = false,
): Triple<SolveResult, List<Int>, List<Int>> =
    ConstraintScope.empty {
        val (bins, loads) = binPacking(sizes, capacity, useDecomposition)
        val result = AutoBackend.load().use { auto -> auto.createSolver().use { it.solve(buildModel()) } }
        check(result.status == SolveStatus.OPTIMAL)
        val assignment = requireNotNull(result.assignment)
        val binOf = bins.map { requireNotNull(assignment.intValueOf(it)) }
        val actualLoads = loads.map { requireNotNull(assignment.intValueOf(it)) }
        println("Bins: ${result.objectiveValue}; sorted loads: ${actualLoads.sorted()}")
        Triple(result, binOf, actualLoads)
    }
// --8<-- [end:solve]

// --8<-- [start:example]
internal suspend fun binPackingExample(useDecomposition: Boolean = false): Triple<SolveResult, List<Int>, List<Int>> {
    val sizes = listOf(4, 3, 2).map { BigInteger.of(it) }
    val capacity = 5
    return solveBinPacking(sizes, capacity, useDecomposition)
}
// --8<-- [end:example]
