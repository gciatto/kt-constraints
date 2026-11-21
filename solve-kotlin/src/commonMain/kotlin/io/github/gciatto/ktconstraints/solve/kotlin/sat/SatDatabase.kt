package io.github.gciatto.ktconstraints.solve.kotlin.sat

import kotlin.math.abs

/** Permanent input and learned clauses; the first two entries in each non-unit clause are watched. */
internal class SatDatabase {
    val clauses = mutableListOf<IntArray>()
    val units = IntVector()
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

    fun watching(literal: Int): IntVector = watches[abs(literal) * 2 + if (literal > 0) 0 else 1]

    fun validateLiteral(literal: Int) {
        require(literal != 0 && literal != Int.MIN_VALUE && abs(literal) <= variableCount) { "Unknown SAT literal: $literal" }
    }
}
