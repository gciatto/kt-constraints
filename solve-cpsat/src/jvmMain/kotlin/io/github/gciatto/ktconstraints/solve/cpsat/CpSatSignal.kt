package io.github.gciatto.ktconstraints.solve.cpsat

import com.google.ortools.sat.CpSolverStatus
import io.github.gciatto.ktconstraints.solve.SolutionFound

/** Message from a CP-SAT search thread to its stream consumer. */
internal sealed interface CpSatSignal {
    /** A validated, projected solution. */
    class Found(
        val solution: SolutionFound,
    ) : CpSatSignal

    /** The search or a callback failed; the failure is not evidence about the model. */
    class Failed(
        val problem: Throwable,
    ) : CpSatSignal

    /** The search returned [status]. */
    class Done(
        val status: CpSolverStatus,
    ) : CpSatSignal
}
