package io.github.gciatto.ktconstraints.solve.auto.examples.howto

// --8<-- [start:imports]
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.auto.AutoBackend
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.spi.withSolver
// --8<-- [end:imports]

// --8<-- [start:milp]

/** Maximizes 3x + 2y over an integer x in 0..10 and a real y >= 0, subject to x + y <= 4.5 and x + 3y <= 6. */
internal suspend fun solveMixedIntegerProgram(): SolveResult =
    ConstraintScope.empty {
        val x = intVar("x", 0..10)
        val y = realVar("y")
        add(y ge 0)
        add((x.asRealExpr() + y) le Rational.parse("4.5"))
        add((x.asRealExpr() + y * 3) le 6)
        maximize(x.asRealExpr() * 3 + y * 2)
        val model = buildModel()
        AutoBackend.load().use { auto -> auto.withSolver { it.solve(model) } }
    }
// --8<-- [end:milp]
