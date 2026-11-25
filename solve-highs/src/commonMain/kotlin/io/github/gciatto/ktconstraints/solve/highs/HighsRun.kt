package io.github.gciatto.ktconstraints.solve.highs

import io.github.gciatto.ktconstraints.solve.linear.LinearProgram

/** One platform run of HiGHS on a program; [interrupt] may be called from any thread while [run] is in progress. */
internal interface HighsRun {
    /** Solves [program] within [timeoutMillis] (null: no limit), optimizing to a zero MIP gap. */
    suspend fun run(
        program: LinearProgram,
        timeoutMillis: Long?,
    ): HighsOutcome

    /** Asks an in-progress [run] to stop as soon as it can. */
    fun interrupt()
}
