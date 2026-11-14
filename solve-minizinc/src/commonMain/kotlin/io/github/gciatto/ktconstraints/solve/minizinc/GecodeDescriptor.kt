package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.SolverDescriptor

internal val gecodeDescriptor =
    SolverDescriptor(
        "gecode",
        "minizinc",
        setOf(
            SolverCapability.SATISFACTION,
            SolverCapability.OPTIMIZATION,
            SolverCapability.ENUMERATION,
            SolverCapability.INTEGER_ARITHMETIC,
            SolverCapability.GLOBAL_CONSTRAINTS,
        ),
    )
