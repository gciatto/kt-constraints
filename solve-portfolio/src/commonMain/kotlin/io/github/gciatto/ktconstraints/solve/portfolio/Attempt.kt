package io.github.gciatto.ktconstraints.solve.portfolio

import io.github.gciatto.ktconstraints.solve.result.SolveResult

/** Outcome of running one backend inside a portfolio. */
internal sealed interface Attempt {
    /** The backend answered, possibly with UNKNOWN. */
    data class Answer(
        val result: SolveResult,
    ) : Attempt

    /** The backend could not run or failed; never evidence about the model. */
    data class Failure(
        val message: String,
    ) : Attempt
}
