package io.github.gciatto.ktconstraints.solve.kotlin.sat

import kotlin.math.abs

/** Reversible assignment trail with persistent phase and conflict-activity heuristics. */
internal class SatState {
    val trail = IntVector()
    val levels = IntVector()
    var assignments = IntArray(16)
        private set
    var assignmentLevels = IntArray(16)
        private set
    var reasons = IntArray(16) { -1 }
        private set
    var seen = BooleanArray(16)
        private set
    var propagated = 0
    private var phases = IntArray(16)
    private var activity = DoubleArray(16)
    private var activityIncrement = 1.0

    fun reset(variables: Int) {
        if (variables >= assignments.size) {
            var capacity = assignments.size
            while (variables >= capacity) capacity *= 2
            assignments = assignments.copyOf(capacity)
            assignmentLevels = assignmentLevels.copyOf(capacity)
            reasons = reasons.copyOf(capacity)
            phases = phases.copyOf(capacity)
            activity = activity.copyOf(capacity)
            seen = seen.copyOf(capacity)
        }
        assignments.fill(0)
        assignmentLevels.fill(0)
        reasons.fill(-1)
        seen.fill(false)
        trail.clear()
        levels.clear()
        propagated = 0
    }

    fun value(literal: Int): Int = assignments[abs(literal)] * if (literal > 0) 1 else -1

    fun enqueue(
        literal: Int,
        reason: Int,
    ): Boolean {
        val current = value(literal)
        if (current != 0) return current > 0
        val variable = abs(literal)
        assignments[variable] = if (literal > 0) 1 else -1
        phases[variable] = assignments[variable]
        assignmentLevels[variable] = levels.size
        reasons[variable] = reason
        trail.add(literal)
        return true
    }

    fun backtrack(
        level: Int,
        budget: SatBudget,
    ) {
        if (levels.size <= level) return
        val restoredSize = levels[level]
        while (trail.size > restoredSize) {
            budget.tick()
            val variable = abs(trail.removeLast())
            assignments[variable] = 0
            assignmentLevels[variable] = 0
            reasons[variable] = -1
        }
        levels.truncate(level)
        propagated = minOf(propagated, trail.size)
    }

    fun chooseDecision(
        variables: Int,
        budget: SatBudget,
    ): Int {
        var variable = 0
        var score = -1.0
        for (candidate in 1..variables) {
            budget.tick()
            if (assignments[candidate] == 0 && activity[candidate] > score) {
                variable = candidate
                score = activity[candidate]
            }
        }
        return if (variable == 0 || phases[variable] >= 0) variable else -variable
    }

    fun bump(
        variable: Int,
        variables: Int,
        budget: SatBudget,
    ) {
        activity[variable] += activityIncrement
        if (activity[variable] > 1e100) {
            for (index in 1..variables) {
                budget.tick()
                activity[index] *= 1e-100
            }
            activityIncrement *= 1e-100
        }
    }

    fun decayActivity() {
        activityIncrement /= 0.95
    }
}
