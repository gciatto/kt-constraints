package io.github.gciatto.ktconstraints.solve.kotlin.sat.cdcl
import io.github.gciatto.ktconstraints.solve.kotlin.sat.SatBudget
import kotlin.math.abs

/**
 * Permanent linear constraints `sum(a_i * l_i) <= bound` with positive coefficients over distinct variables.
 * Each keeps the sum of its literals already processed as true; once the slack is below a coefficient, that literal
 * becomes false. Propagation reasons are explained lazily, conflicts eagerly as learned clauses.
 */
internal class SatLinear(
    private val database: SatDatabase,
    private val state: SatState,
) {
    private val literals = mutableListOf<IntArray>()
    private val coefficients = mutableListOf<LongArray>()
    private var bounds = LongArray(4)
    private var sums = LongArray(4)
    private val occurrences = mutableListOf(IntVector(), IntVector())

    fun newVariable() {
        occurrences.add(IntVector())
        occurrences.add(IntVector())
    }

    /** Adds a constraint; literals whose coefficient alone exceeds the bound become unit clauses. */
    fun add(
        terms: IntArray,
        weights: LongArray,
        bound: Long,
    ) {
        require(terms.size == weights.size) { "Each literal needs one coefficient" }
        terms.forEach(database::validateLiteral)
        require(weights.all { it > 0 }) { "Linear coefficients must be positive" }
        require(terms.map(::abs).toSet().size == terms.size) { "Linear literals must have distinct variables" }
        if (bound < 0) {
            database.addClause(intArrayOf())
            return
        }
        val kept = terms.indices.filter { weights[it] <= bound }
        terms.indices.filter { weights[it] > bound }.forEach { database.addClause(intArrayOf(-terms[it])) }
        var total = 0L
        for (index in kept) {
            require(total <= Long.MAX_VALUE - weights[index]) { "Linear coefficients overflow" }
            total += weights[index]
        }
        if (total <= bound) return
        val ordered = kept.sortedByDescending { weights[it] }
        val id = literals.size
        literals.add(IntArray(ordered.size) { terms[ordered[it]] })
        coefficients.add(LongArray(ordered.size) { weights[ordered[it]] })
        if (id == bounds.size) {
            bounds = bounds.copyOf(2 * id)
            sums = sums.copyOf(2 * id)
        }
        bounds[id] = bound
        literals[id].forEachIndexed { position, literal -> occurrences[index(literal)].addPair(id, position) }
    }

    fun reset() {
        sums.fill(0)
    }

    /**
     * Accounts for [literal] having become true. Returns a conflicting clause id, or -1 after forcing every literal
     * whose coefficient exceeds the remaining slack.
     */
    fun assign(
        literal: Int,
        budget: SatBudget,
    ): Int {
        val entries = occurrences[index(literal)]
        for (entry in 0 until entries.size step 2) sums[entries[entry]] += coefficients[entries[entry]][entries[entry + 1]]
        for (entry in 0 until entries.size step 2) {
            budget.tick()
            val id = entries[entry]
            val slack = bounds[id] - sums[id]
            if (slack < 0) return conflict(id)
            val terms = literals[id]
            val weights = coefficients[id]
            var position = 0
            while (position < terms.size && weights[position] > slack) {
                if (state.value(terms[position]) == 0) state.enqueue(-terms[position], reason(id))
                position++
            }
        }
        return -1
    }

    /** Undoes the sums of processed trail literals that backtracking to [level] removes. */
    fun backtrack(level: Int) {
        if (state.levels.size <= level) return
        for (position in state.levels[level] until minOf(state.propagated, state.trail.size)) {
            val entries = occurrences[index(state.trail[position])]
            for (entry in 0 until entries.size step 2) sums[entries[entry]] -= coefficients[entries[entry]][entries[entry + 1]]
        }
    }

    /**
     * Clause implying [variable] under the linear reason [reason]: the largest-coefficient literals true before it,
     * just enough that with the variable's own coefficient they exceed the bound.
     */
    fun explain(
        reason: Int,
        variable: Int,
    ): IntArray {
        val id = constraintOf(reason)
        val terms = literals[id]
        val weights = coefficients[id]
        val own = weights[terms.indexOfFirst { abs(it) == variable }]
        val before = state.positions[variable]
        val result = IntVector()
        result.add(if (state.assignments[variable] > 0) variable else -variable)
        var sum = own
        for (position in terms.indices) {
            if (sum > bounds[id]) break
            val literal = terms[position]
            if (abs(literal) != variable && state.value(literal) > 0 && state.positions[abs(literal)] < before) {
                result.add(-literal)
                sum += weights[position]
            }
        }
        return result.toIntArray()
    }

    /** The largest-coefficient true literals whose sum alone exceeds the bound, negated, as a learned clause. */
    private fun conflict(id: Int): Int {
        val terms = literals[id]
        val weights = coefficients[id]
        val clause = mutableListOf<Int>()
        var sum = 0L
        for (position in terms.indices) {
            if (sum > bounds[id]) break
            if (state.value(terms[position]) > 0) {
                clause += -terms[position]
                sum += weights[position]
            }
        }
        clause.sortByDescending { state.assignmentLevels[abs(it)] }
        return database.insertLearned(clause.toIntArray(), clause.size)
    }

    private fun index(literal: Int): Int = abs(literal) * 2 + if (literal > 0) 0 else 1

    companion object {
        /** Reasons below this value name linear constraints; -1 remains "no reason". */
        private const val FIRST = -2

        fun reason(id: Int): Int = FIRST - id

        fun isLinear(reason: Int): Boolean = reason <= FIRST

        fun constraintOf(reason: Int): Int = FIRST - reason
    }
}
