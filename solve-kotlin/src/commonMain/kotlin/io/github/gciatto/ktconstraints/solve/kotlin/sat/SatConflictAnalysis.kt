package io.github.gciatto.ktconstraints.solve.kotlin.sat

import kotlin.math.abs

/** Resolves clause reasons backwards along the trail until the first unique implication point. */
internal class SatConflictAnalysis(
    private val database: SatDatabase,
    private val state: SatState,
    private val budget: SatBudget,
) {
    private val learned = IntVector()
    private val touched = IntVector()

    fun analyze(conflict: Int): SatLearning {
        learned.clear()
        touched.clear()
        learned.add(0)
        var unresolved = 0
        var pivot = 0
        var reason = conflict
        var cursor = state.trail.size - 1
        do {
            for (literal in database.clauses[reason]) {
                budget.tick()
                val variable = abs(literal)
                if (variable != abs(pivot) && !state.seen[variable] && state.assignmentLevels[variable] > 0) {
                    state.seen[variable] = true
                    touched.add(variable)
                    state.bump(variable, database.variableCount, budget)
                    if (state.assignmentLevels[variable] == state.levels.size) unresolved++ else learned.add(literal)
                }
            }
            do {
                budget.tick()
                check(cursor >= 0) { "Conflict has no decision-level antecedent" }
                pivot = state.trail[cursor--]
            } while (!state.seen[abs(pivot)])
            state.seen[abs(pivot)] = false
            unresolved--
            reason = state.reasons[abs(pivot)]
            check(unresolved == 0 || reason >= 0) { "A non-UIP antecedent must have a reason" }
        } while (unresolved > 0)
        learned[0] = -pivot
        minimize()
        val jump = orderBackjumpLiteral()
        for (index in 0 until touched.size) {
            budget.tick()
            state.seen[touched[index]] = false
        }
        return SatLearning(learned.toIntArray(), jump)
    }

    /** Drops literals whose reason clause is otherwise contained in the learned clause or fixed at level zero. */
    private fun minimize() {
        var kept = 1
        for (index in 1 until learned.size) {
            val literal = learned[index]
            if (!implied(abs(literal))) learned[kept++] = literal
        }
        learned.truncate(kept)
    }

    private fun implied(variable: Int): Boolean {
        val reason = state.reasons[variable]
        if (reason < 0) return false
        for (literal in database.clauses[reason]) {
            budget.tick()
            val other = abs(literal)
            if (other != variable && !state.seen[other] && state.assignmentLevels[other] > 0) return false
        }
        return true
    }

    private fun orderBackjumpLiteral(): Int {
        var jump = 0
        var highest = 1
        for (index in 1 until learned.size) {
            budget.tick()
            val level = state.assignmentLevels[abs(learned[index])]
            if (level > jump) {
                jump = level
                highest = index
            }
        }
        if (learned.size > 1) {
            val literal = learned[1]
            learned[1] = learned[highest]
            learned[highest] = literal
        }
        return jump
    }
}
