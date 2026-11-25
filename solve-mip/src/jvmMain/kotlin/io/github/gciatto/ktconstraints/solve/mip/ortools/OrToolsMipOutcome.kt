package io.github.gciatto.ktconstraints.solve.mip.ortools
import com.google.ortools.linearsolver.MPSolver

/** What one solve reported: its status and the program values, if it found a solution. */
internal class OrToolsMipOutcome(
    val status: MPSolver.ResultStatus,
    val values: DoubleArray?,
)
