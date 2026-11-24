package io.github.gciatto.ktconstraints.solve.auto.examples.howto

// --8<-- [start:imports]
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.auto.loadAutoBackend
import io.github.gciatto.ktconstraints.solve.blocking.withBlockingSolver
import io.github.gciatto.ktconstraints.solve.stream.SolutionFound
// --8<-- [end:imports]

// --8<-- [start:blocking]

/** Lists every x in 0..3 with x + y = 3 and x > y, without suspending the caller. */
internal fun blockingSolutions(): List<Int> =
    ConstraintScope.empty {
        val x = intVar("x", 0..3)
        val y = intVar("y", 0..3)
        add(x + y eq 3)
        add(x gt y)
        project(x)
        val model = buildModel()
        val values = mutableListOf<Int>()
        loadAutoBackend().use { backend ->
            backend.withBlockingSolver { solver ->
                solver.solutions(model).use { stream ->
                    for (event in stream) {
                        if (event is SolutionFound) values += checkNotNull(event.assignment.intValueOf(x))
                    }
                }
            }
        }
        values
    }
// --8<-- [end:blocking]
