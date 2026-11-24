package io.github.gciatto.ktconstraints.solve.kotlin

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.spi.withSolver
import io.github.gciatto.ktconstraints.test.solve.oracle.FiniteOracle
import io.github.gciatto.ktconstraints.test.solve.oracle.enumerate
import io.kotest.matchers.shouldBe

/** The backend's status and solution set, or optimum, equal the finite oracle's on [model]. */
internal suspend fun agreesWithOracle(model: ConstraintModel) {
    val optimizing = model.objective != null
    val (expected, oracle) = enumerate(FiniteOracle(), model, objectives = optimizing)
    KotlinBackend().withSolver { solver ->
        val (actual, result) = enumerate(solver, model, objectives = optimizing)
        result.status shouldBe oracle.status
        if (optimizing) result.objectiveValue shouldBe oracle.objectiveValue else actual.toSet() shouldBe expected.toSet()
    }
}
