package io.github.gciatto.ktconstraints.solve

import kotlin.jvm.JvmOverloads

/**
 * Consent to solve real models in floating point, with the tolerances such routes may use. Every returned solution
 * is re-checked exactly: no bound or constraint may be exceeded by more than [feasibility], and integer variables
 * are rounded only when within [integrality] of an integer. Optimality is the engine's numerical claim, not an exact
 * proof.
 */
data class NumericTolerance
    @JvmOverloads
    constructor(
        /** Largest accepted absolute excess over a bound or constraint. */
        val feasibility: Double = DEFAULT_TOLERANCE,
        /** Largest accepted distance of an integer variable's value from the integer it is rounded to. */
        val integrality: Double = DEFAULT_TOLERANCE,
    ) {
        init {
            require(feasibility.isFinite() && feasibility >= 0.0) { "Feasibility tolerance must be finite and non-negative" }
            require(integrality.isFinite() && integrality >= 0.0 && integrality < HALF) { "Integrality tolerance must be in [0, 1/2)" }
        }

        private companion object {
            const val DEFAULT_TOLERANCE = 1e-6
            const val HALF = 0.5
        }
    }
