package io.github.gciatto.ktconstraints.solve.kotlin.sat

import kotlin.math.abs

/** Permanent input and learned clauses; the first two entries in each non-unit clause are watched. */
internal class SatDatabase {
    val clauses = mutableListOf<IntArray>()
    val units = IntVector()
    private val learned = IntVector()
    private val blockDistances = IntVector()
    private var learnedLimit = FIRST_REDUCTION
    private val watches = mutableListOf(IntVector(), IntVector())

    var variableCount: Int = 0
        private set

    var inconsistent: Boolean = false
        private set

    fun newVariable(): Int {
        check(variableCount < (Int.MAX_VALUE - 2) / 2) { "SAT variable capacity exhausted" }
        watches.add(IntVector())
        watches.add(IntVector())
        return ++variableCount
    }

    fun addClause(literals: IntArray) {
        literals.forEach(::validateLiteral)
        val unique = linkedSetOf<Int>()
        for (literal in literals) {
            if (-literal in unique) return
            unique.add(literal)
        }
        insertClause(unique.toIntArray())
    }

    fun insertClause(literals: IntArray): Int {
        val id = clauses.size
        clauses.add(literals)
        when (literals.size) {
            0 -> inconsistent = true
            1 -> units.add(id)
            else -> {
                watching(literals[0]).add(id)
                watching(literals[1]).add(id)
            }
        }
        return id
    }

    /** Inserts a learned clause that [reduce] may later delete. */
    fun insertLearned(
        literals: IntArray,
        blockDistance: Int,
    ): Int =
        insertClause(literals).also {
            learned.add(it)
            blockDistances.add(blockDistance)
        }

    /**
     * Deletes the worse half of the learned clauses once their number passes a growing limit; clauses spanning at
     * most two decision levels are kept. Call only at decision level zero, where no learned clause is a reason.
     */
    fun reduce() {
        if (learned.size < learnedLimit) return
        learnedLimit += REDUCTION_INCREMENT
        val order = (0 until learned.size).sortedWith(compareBy({ -blockDistances[it] }, { -clauses[learned[it]].size }))
        val deleted = BooleanArray(learned.size)
        var remaining = learned.size / 2
        for (position in order) {
            if (remaining == 0) break
            if (blockDistances[position] > 2) {
                deleted[position] = true
                clauses[learned[position]] = DELETED
                remaining--
            }
        }
        var kept = 0
        for (position in 0 until learned.size) {
            if (!deleted[position]) {
                learned[kept] = learned[position]
                blockDistances[kept++] = blockDistances[position]
            }
        }
        learned.truncate(kept)
        blockDistances.truncate(kept)
    }

    fun watching(literal: Int): IntVector = watches[abs(literal) * 2 + if (literal > 0) 0 else 1]

    fun validateLiteral(literal: Int) {
        require(literal != 0 && literal != Int.MIN_VALUE && abs(literal) <= variableCount) { "Unknown SAT literal: $literal" }
    }

    companion object {
        /** Placeholder of a deleted learned clause; watch lists drop it lazily. */
        val DELETED = IntArray(0)
        private const val FIRST_REDUCTION = 2000
        private const val REDUCTION_INCREMENT = 300
    }
}
