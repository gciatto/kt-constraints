package io.github.gciatto.ktconstraints.solve.auto.examples.howto

// --8<-- [start:imports]
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.kotlin.KotlinBackend
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
        KotlinBackend().withSolver { solver ->
            solver.solve(model).also { result ->
                check(result.assignment?.valueOf(x)?.toString() == "4")
                check(result.assignment?.valueOf(y) == Rational.of(1, 2))
                maximize(x)
                val integerGoal = solver.solve(buildModel())
                check(integerGoal.objectiveValue?.toString() == "4")
                check(integerGoal.realObjectiveValue == null)
            }
        }
    }
// --8<-- [end:milp]

// --8<-- [start:lp]

/** Solves the continuous relaxation: its optimum is 27/2 at x = 9/2 and y = 0. */
internal suspend fun solveLinearProgram(): SolveResult =
    ConstraintScope.empty {
        val x = realVar("x")
        val y = realVar("y")
        add(x ge 0)
        add(y ge 0)
        add((x + y) le Rational.parse("4.5"))
        add((x + y * 3) le 6)
        maximize(x * 3 + y * 2)
        val model = buildModel()
        KotlinBackend().withSolver { solver ->
            solver.solve(model).also { result ->
                check(result.assignment?.valueOf(x) == Rational.of(9, 2))
                check(result.assignment?.valueOf(y) == Rational.ZERO)
            }
        }
    }
// --8<-- [end:lp]
