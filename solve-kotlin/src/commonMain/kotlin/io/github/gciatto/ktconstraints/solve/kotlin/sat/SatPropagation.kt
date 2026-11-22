package io.github.gciatto.ktconstraints.solve.kotlin.sat

/** Two-watched-literal propagation; changing watch positions remains valid after assignment rollback. */
internal class SatPropagation(
    private val database: SatDatabase,
    private val state: SatState,
    private val statistics: SatStatistics,
    private val budget: SatBudget,
    private val linear: SatLinear,
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
        var processed = 0
        try {
            return drain { processed++ }
        } finally {
            // Counted locally: a Long increment per literal is software-emulated on Kotlin/JS.
            statistics.propagations += processed
        }
    }

    private inline fun drain(onLiteral: () -> Unit): Int {
        while (state.propagated < state.trail.size) {
            if (budget.due) return PAUSED
            budget.tick()
            onLiteral()
            val falsified = -state.trail[state.propagated++]
            val linearConflict = linear.assign(-falsified, budget)
            if (linearConflict >= 0) return linearConflict
            val watched = database.watching(falsified)
            var index = 0
            while (index < watched.size) {
                budget.tick()
                // A true blocker satisfies the clause, which then need not be loaded at all.
                if (state.value(watched[index + 1]) > 0) {
                    index += 2
                    continue
                }
                val id = watched[index]
                val clause = database.clauses[id]
                if (clause.size == 0) {
                    watched.removePairAtSwap(index)
                    continue
                }
                if (clause[0] == falsified) {
                    clause[0] = clause[1]
                    clause[1] = falsified
                }
                val other = clause[0]
                if (state.value(other) > 0) {
                    watched[index + 1] = other
                    index += 2
                    continue
                }
                var replacement = 2
                while (replacement < clause.size && state.value(clause[replacement]) < 0) replacement++
                budget.tick(replacement - 2)
                if (replacement < clause.size) {
                    val literal = clause[replacement]
                    clause[replacement] = falsified
                    clause[1] = literal
                    watched.removePairAtSwap(index)
                    database.watching(literal).addPair(id, other)
                } else {
                    if (!state.enqueue(other, id)) return id
                    index += 2
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
