package io.github.gciatto.ktconstraints.solve.kotlin.sat

/**
 * Single-consumer CDCL solver with watched clauses, first-UIP learning and temporary assumptions.
 * Positive integers name variables, negation changes the sign, and zero is never a literal.
 * Clauses and learned consequences persist across checks; assignments and assumptions do not.
 */
internal class SatSolver {
    private val database = SatDatabase()
    private val state = SatState()
    private val statistics = SatStatistics()
    private var solving = false

    val decisions: Long get() = statistics.decisions
    val conflicts: Long get() = statistics.conflicts
    val propagations: Long get() = statistics.propagations

    /** Allocates a fresh variable; IDs remain valid for this solver's entire lifetime. */
    fun newVariable(): Int {
        check(!solving) { "Cannot change the variable universe during a SAT check" }
        return database.newVariable()
    }

    /** Adds a permanent clause after validating every literal, then removing duplicates and tautologies. */
    fun addClause(literals: IntArray) {
        check(!solving) { "Cannot add clauses during a SAT check" }
        database.addClause(literals)
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
        assumptions.forEach(database::validateLiteral)
        val temporary = assumptions.copyOf()
        solving = true
        try {
            checkpoint()
            state.reset(database.variableCount)
            val budget = SatBudget(checkpoint)
            val propagation = SatPropagation(database, state, statistics, budget)
            if (database.inconsistent || !propagation.assertUnits()) return SatResult(SatStatus.UNSATISFIABLE)
            return search(temporary, budget, propagation)
        } finally {
            solving = false
        }
    }

    private suspend fun search(
        assumptions: IntArray,
        budget: SatBudget,
        propagation: SatPropagation,
    ): SatResult {
        val analysis = SatConflictAnalysis(database, state, budget)
        while (true) {
            budget.tick()
            budget.poll()
            val conflict = propagation.propagate()
            if (conflict == SatPropagation.PAUSED) continue
            if (conflict >= 0) {
                statistics.conflicts++
                if (state.levels.size == 0) return SatResult(SatStatus.UNSATISFIABLE)
                val learned = analysis.analyze(conflict)
                state.backtrack(learned.backtrackLevel, budget)
                val reason = database.insertClause(learned.clause)
                check(state.enqueue(learned.clause[0], reason)) { "Learned clause must be asserting after backjump" }
                state.decayActivity()
            } else {
                var decision = 0
                while (state.levels.size < assumptions.size && decision == 0) {
                    budget.tick()
                    val literal = assumptions[state.levels.size]
                    when (state.value(literal)) {
                        1 -> state.levels.add(state.trail.size)
                        -1 -> return SatResult(SatStatus.UNSATISFIABLE)
                        else -> decision = literal
                    }
                }
                if (decision == 0) decision = state.chooseDecision(budget)
                if (decision == 0) {
                    return SatResult(
                        SatStatus.SATISFIABLE,
                        BooleanArray(database.variableCount + 1) { state.assignments[it] > 0 },
                    )
                }
                state.levels.add(state.trail.size)
                statistics.decisions++
                check(state.enqueue(decision, -1))
            }
        }
    }
}
