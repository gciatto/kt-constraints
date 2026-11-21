package io.github.gciatto.ktconstraints.solve.kotlin.sat

/** Two-watched-literal propagation; changing watch positions remains valid after assignment rollback. */
internal class SatPropagation(
    private val database: SatDatabase,
    private val state: SatState,
    private val statistics: SatStatistics,
    private val budget: SatBudget,
) {
    suspend fun assertUnits(): Boolean {
        for (index in 0 until database.units.size) {
            budget.tick()
            budget.poll()
            val reason = database.units[index]
            if (!state.enqueue(database.clauses[reason][0], reason)) return false
        }
        return true
    }

    /** Returns a conflicting clause, [FIXPOINT], or [PAUSED] when a checkpoint is due between trail literals. */
    fun propagate(): Int {
        while (state.propagated < state.trail.size) {
            if (budget.due) return PAUSED
            budget.tick()
            statistics.propagations++
            val falsified = -state.trail[state.propagated++]
            val watched = database.watching(falsified)
            var index = 0
            while (index < watched.size) {
                budget.tick()
                val id = watched[index]
                val clause = database.clauses[id]
                if (clause.size == 0) {
                    watched.removeAtSwap(index)
                    continue
                }
                if (clause[0] == falsified) {
                    clause[0] = clause[1]
                    clause[1] = falsified
                }
                val other = clause[0]
                if (state.value(other) > 0) {
                    index++
                    continue
                }
                var replacement = 2
                while (replacement < clause.size && state.value(clause[replacement]) < 0) replacement++
                budget.tick(replacement - 2)
                if (replacement < clause.size) {
                    val literal = clause[replacement]
                    clause[replacement] = falsified
                    clause[1] = literal
                    watched.removeAtSwap(index)
                    database.watching(literal).add(id)
                } else {
                    if (!state.enqueue(other, id)) return id
                    index++
                }
            }
        }
        return FIXPOINT
    }

    companion object {
        const val FIXPOINT = -1
        const val PAUSED = -2
    }
}
