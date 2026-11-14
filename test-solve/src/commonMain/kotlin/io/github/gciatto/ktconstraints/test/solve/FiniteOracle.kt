package io.github.gciatto.ktconstraints.test.solve

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.spi.ManagedConstraintSolver
import io.github.gciatto.ktconstraints.solve.spi.ManagedSolutionStream

/** Tiny exact test oracle; unpublished and never registered as a production backend. */
class FiniteOracle : ManagedConstraintSolver() {
    override val descriptor: SolverDescriptor =
        SolverDescriptor(
            "finite-oracle",
            "test",
            setOf(
                SolverCapability.SATISFACTION,
                SolverCapability.OPTIMIZATION,
                SolverCapability.ENUMERATION,
                SolverCapability.INTEGER_ARITHMETIC,
                SolverCapability.GLOBAL_CONSTRAINTS,
            ),
        )

    override fun openStream(
        model: ConstraintModel,
        options: SolveOptions,
        onClosed: () -> Unit,
    ): ManagedSolutionStream {
        requireOracleSupported(model)
        return OracleStream(model, options, OracleCandidates(model), descriptor, onClosed)
    }

    /** Fixed safety caps; this utility is deliberately not a general solver. */
    companion object {
        /** Maximum full assignments considered by one search. */
        const val MAX_CANDIDATES: Int = 10_000

        /** Maximum variables in one tiny test model. */
        const val MAX_VARIABLES: Int = 64
    }
}
