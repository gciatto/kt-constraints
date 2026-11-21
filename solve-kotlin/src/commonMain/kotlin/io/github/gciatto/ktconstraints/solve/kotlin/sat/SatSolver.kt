package io.github.gciatto.ktconstraints.solve.kotlin.sat

import kotlin.math.abs

/**
 * Single-consumer CDCL solver with watched clauses, first-UIP learning and temporary assumptions.
 * Positive integers name variables, negation changes the sign, and zero is never a literal.
 * Clauses and learned consequences persist across checks; assignments and assumptions do not.
 */
internal class SatSolver {
    private val clauses = mutableListOf<IntArray>()
    private val units = IntVector()
    private val watches = mutableListOf(IntVector(), IntVector())
    private val trail = IntVector()
    private val levels = IntVector()
    private var variableCount = 0
    private var assignments = IntArray(16)
    private var assignmentLevels = IntArray(16)
    private var reasons = IntArray(16) { -1 }
    private var phases = IntArray(16)
    private var activity = DoubleArray(16)
    private var seen = BooleanArray(16)
    private var activityIncrement = 1.0
    private var propagated = 0
    private var inconsistent = false
    private var solving = false
    private var work = 0
    private var checkpoint: suspend () -> Unit = {}

    var decisions: Long = 0
        private set

    var conflicts: Long = 0
        private set

    var propagations: Long = 0
        private set

    /** Allocates a fresh variable; IDs remain valid for this solver's entire lifetime. */
    fun newVariable(): Int {
        check(!solving) { "Cannot change the variable universe during a SAT check" }
        check(variableCount < (Int.MAX_VALUE - 2) / 2) { "SAT variable capacity exhausted" }
        val variable = ++variableCount
        if (variable >= assignments.size) {
            val capacity = assignments.size * 2
            assignments = assignments.copyOf(capacity)
            assignmentLevels = assignmentLevels.copyOf(capacity)
            reasons = reasons.copyOf(capacity)
            phases = phases.copyOf(capacity)
            activity = activity.copyOf(capacity)
            seen = seen.copyOf(capacity)
        }
        watches.add(IntVector())
        watches.add(IntVector())
        return variable
    }

    /** Adds a permanent clause after validating every literal, then removing duplicates and tautologies. */
    fun addClause(literals: IntArray) {
        check(!solving) { "Cannot add clauses during a SAT check" }
        literals.forEach(::validateLiteral)
        val unique = linkedSetOf<Int>()
        for (literal in literals) {
            if (-literal in unique) return
            unique.add(literal)
        }
        insertClause(unique.toIntArray())
    }

    /**
     * Searches under temporary [assumptions]. Interruption propagates from [checkpoint], including while
     * scanning long clauses or resolving conflicts, and the solver can be checked again afterwards.
     */
    suspend fun solve(
        assumptions: IntArray = intArrayOf(),
        checkpoint: suspend () -> Unit = {},
    ): SatResult {
        check(!solving) { "Only one consumer may run a SAT check" }
        assumptions.forEach(::validateLiteral)
        val temporary = assumptions.copyOf()
        solving = true
        this.checkpoint = checkpoint
        try {
            resetAssignments()
            checkpoint()
            if (inconsistent || !assertUnits()) return SatResult(SatStatus.UNSATISFIABLE)
            return search(temporary)
        } finally {
            this.checkpoint = {}
            solving = false
        }
    }

    private suspend fun search(assumptions: IntArray): SatResult {
        while (true) {
            tick()
            val conflict = propagate()
            if (conflict >= 0) {
                conflicts++
                if (levels.size == 0) return SatResult(SatStatus.UNSATISFIABLE)
                val learned = analyze(conflict)
                backtrack(learned.backtrackLevel)
                val reason = insertClause(learned.clause)
                check(enqueue(learned.clause[0], reason)) { "Learned clause must be asserting after backjump" }
                activityIncrement /= 0.95
            } else {
                var decision = 0
                while (levels.size < assumptions.size && decision == 0) {
                    tick()
                    val literal = assumptions[levels.size]
                    when (value(literal)) {
                        1 -> levels.add(trail.size)
                        -1 -> return SatResult(SatStatus.UNSATISFIABLE)
                        else -> decision = literal
                    }
                }
                if (decision == 0) decision = chooseDecision()
                if (decision == 0) {
                    return SatResult(SatStatus.SATISFIABLE, BooleanArray(variableCount + 1) { assignments[it] > 0 })
                }
                levels.add(trail.size)
                decisions++
                check(enqueue(decision, -1))
            }
        }
    }

    private fun insertClause(literals: IntArray): Int {
        val id = clauses.size
        clauses.add(literals)
        when (literals.size) {
            0 -> inconsistent = true
            1 -> units.add(id)
            else -> {
                watches[watchIndex(literals[0])].add(id)
                watches[watchIndex(literals[1])].add(id)
            }
        }
        return id
    }

    private fun resetAssignments() {
        assignments.fill(0)
        assignmentLevels.fill(0)
        reasons.fill(-1)
        seen.fill(false)
        trail.clear()
        levels.clear()
        propagated = 0
        work = 0
    }

    private suspend fun assertUnits(): Boolean {
        for (index in 0 until units.size) {
            tick()
            val reason = units[index]
            if (!enqueue(clauses[reason][0], reason)) return false
        }
        return true
    }

    private fun enqueue(literal: Int, reason: Int): Boolean {
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

    private suspend fun propagate(): Int {
        while (propagated < trail.size) {
            tick()
            propagations++
            val falsified = -trail[propagated++]
            val watched = watches[watchIndex(falsified)]
            var index = 0
            while (index < watched.size) {
                tick()
                val id = watched[index]
                val clause = clauses[id]
                if (clause[0] == falsified) {
                    clause[0] = clause[1]
                    clause[1] = falsified
                }
                check(clause[1] == falsified) { "Stale clause watch" }
                val other = clause[0]
                if (value(other) > 0) {
                    index++
                    continue
                }
                var replacement = 2
                while (replacement < clause.size && value(clause[replacement]) < 0) {
                    tick()
                    replacement++
                }
                if (replacement < clause.size) {
                    val literal = clause[replacement]
                    clause[replacement] = falsified
                    clause[1] = literal
                    watched.removeAtSwap(index)
                    watches[watchIndex(literal)].add(id)
                } else {
                    if (!enqueue(other, id)) return id
                    index++
                }
            }
        }
        return -1
    }

    private suspend fun analyze(conflict: Int): SatLearning {
        val learned = IntVector()
        val touched = IntVector()
        learned.add(0)
        var unresolved = 0
        var pivot = 0
        var reason = conflict
        var cursor = trail.size - 1
        do {
            for (literal in clauses[reason]) {
                tick()
                val variable = abs(literal)
                if (variable != abs(pivot) && !seen[variable] && assignmentLevels[variable] > 0) {
                    seen[variable] = true
                    touched.add(variable)
                    bump(variable)
                    if (assignmentLevels[variable] == levels.size) unresolved++ else learned.add(literal)
                }
            }
            do {
                tick()
                check(cursor >= 0) { "Conflict has no decision-level antecedent" }
                pivot = trail[cursor--]
            } while (!seen[abs(pivot)])
            seen[abs(pivot)] = false
            unresolved--
            reason = reasons[abs(pivot)]
            check(unresolved == 0 || reason >= 0) { "A non-UIP antecedent must have a reason" }
        } while (unresolved > 0)
        learned[0] = -pivot
        var jump = 0
        var highest = 1
        for (index in 1 until learned.size) {
            tick()
            val level = assignmentLevels[abs(learned[index])]
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
        for (index in 0 until touched.size) seen[touched[index]] = false
        return SatLearning(learned.toIntArray(), jump)
    }

    private suspend fun bump(variable: Int) {
        activity[variable] += activityIncrement
        if (activity[variable] > 1e100) {
            for (index in 1..variableCount) {
                tick()
                activity[index] *= 1e-100
            }
            activityIncrement *= 1e-100
        }
    }

    private suspend fun backtrack(level: Int) {
        if (levels.size <= level) return
        val restoredSize = levels[level]
        while (trail.size > restoredSize) {
            tick()
            val variable = abs(trail.removeLast())
            assignments[variable] = 0
            assignmentLevels[variable] = 0
            reasons[variable] = -1
        }
        levels.truncate(level)
        propagated = minOf(propagated, trail.size)
    }

    private suspend fun chooseDecision(): Int {
        var variable = 0
        var score = -1.0
        for (candidate in 1..variableCount) {
            tick()
            if (assignments[candidate] == 0 && activity[candidate] > score) {
                variable = candidate
                score = activity[candidate]
            }
        }
        return if (variable == 0 || phases[variable] >= 0) variable else -variable
    }

    private fun validateLiteral(literal: Int) {
        require(literal != 0 && literal != Int.MIN_VALUE && abs(literal) <= variableCount) { "Unknown SAT literal: $literal" }
    }

    private fun value(literal: Int): Int = assignments[abs(literal)] * if (literal > 0) 1 else -1

    private fun watchIndex(literal: Int): Int = abs(literal) * 2 + if (literal > 0) 0 else 1

    private suspend fun tick() {
        if (++work and 255 == 0) checkpoint()
    }
}
