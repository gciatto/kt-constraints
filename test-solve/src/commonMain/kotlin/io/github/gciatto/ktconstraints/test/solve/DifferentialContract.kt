package io.github.gciatto.ktconstraints.test.solve

import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.TerminationReason
import io.kotest.core.spec.style.funSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe

/**
 * Compares [create]'s solvers with the [FiniteOracle] on [cases]: status and objective always; with [enumeration], the
 * exact set of distinct solutions, otherwise at most one solution, which must be one of the oracle's.
 */
fun differentialContract(
    prefix: String,
    enumeration: Boolean = true,
    cases: List<DifferentialCase> = DifferentialCase.catalogue(),
    create: () -> ConstraintSolver,
) = funSpec {
    withTests(nameFn = { "$prefix/matches-the-oracle/${it.name}" }, cases) { case ->
        val scope = ConstraintScope.empty()
        case.build(scope)
        val model = scope.build()
        val (expected, expectedResult) = FiniteOracle().use { enumerate(it, model) }
        val (actual, actualResult) = create().use { enumerate(it, model) }
        actualResult.status shouldBe expectedResult.status
        actualResult.terminationReason shouldBe TerminationReason.COMPLETED
        actualResult.objectiveValue shouldBe expectedResult.objectiveValue
        if (model.objective == null) {
            if (enumeration) {
                actual.size shouldBe actual.toSet().size
                actual.toSet() shouldBe expected.toSet()
            } else {
                actual.size shouldBe minOf(1, expected.size)
                actual.forEach { it shouldBeIn expected }
            }
        }
    }
}
