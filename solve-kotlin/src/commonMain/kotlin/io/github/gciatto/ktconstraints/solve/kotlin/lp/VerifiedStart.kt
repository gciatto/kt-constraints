package io.github.gciatto.ktconstraints.solve.kotlin.lp

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.solve.linear.LinearProgram
import io.github.gciatto.ktconstraints.solve.linear.LinearRelation
import io.github.gciatto.ktconstraints.solve.linear.LinearSolution
import org.gciatto.kt.math.BigInteger

/**
 * Exact optima of linear programs through a floating-point guess: [FloatingSimplex] proposes an optimal basis, the
 * exact primal point and duals of that basis are solved by fraction-free elimination, and the answer is accepted only
 * when the point satisfies the program and [Certificates] confirms that the duals bound the objective at its value.
 * The exact simplex then never pivots, which matters on dense programs whose exact tableaux hold huge fractions.
 */
internal object VerifiedStart {
    /** The proven optimum of `max score·x` at [guess], for a program without integer columns or strict rows; else null. */
    fun optimum(
        program: LinearProgram,
        score: Map<Int, Rational>,
        guess: FloatingSimplex.Basis,
    ): BranchAndBound.Incumbent? {
        if (program.hasIntegers || program.hasStrictRows) return null
        val n = program.columns.size
        val basicColumns = (0 until n).filter { it in guess.basic }
        val tightRows = program.rows.indices.filter { n + it !in guess.basic }
        if (basicColumns.size != tightRows.size) return null
        val values =
            MutableList(n) { j ->
                if (j in
                    guess.basic
                ) {
                    Rational.ZERO
                } else {
                    nonbasic(program, j, j in guess.atUpper) ?: return null
                }
            }
        // Tight rows hold with equality; solve them for the basic columns.
        val primal =
            solve(
                tightRows.map { r -> basicColumns.map { j -> program.rows[r].terms[j] ?: Rational.ZERO } },
                tightRows.map { r ->
                    val row = program.rows[r]
                    row.terms.entries.fold(row.rhs) { rest, (j, a) -> if (j in guess.basic) rest else rest - a * values[j] }
                },
            ) ?: return null
        basicColumns.forEachIndexed { i, j -> values[j] = primal[i] }
        if (!LinearSolution(program, values).isFeasible) return null
        // Duals of the tight rows make every basic column's reduced cost zero; the remaining costs fall on column bounds.
        val duals =
            solve(
                basicColumns.map { j -> tightRows.map { r -> program.rows[r].terms[j] ?: Rational.ZERO } },
                basicColumns.map { score[it] ?: Rational.ZERO },
            ) ?: return null
        val uses = mutableListOf<Simplex.Use>()
        tightRows.forEachIndexed { i, r -> use(n + r, duals[i])?.let { uses += it } }
        for (j in 0 until n) {
            if (j in guess.basic) continue
            val covered =
                tightRows.indices.fold(Rational.ZERO) { sum, i ->
                    sum +
                        duals[i] * (program.rows[tightRows[i]].terms[j] ?: Rational.ZERO)
                }
            use(j, (score[j] ?: Rational.ZERO) - covered)?.let { uses += it }
        }
        if (!uses.all { usable(program, it) }) return null
        val optimum = score.entries.fold(Rational.ZERO) { sum, (j, c) -> sum + c * values[j] }
        if (!Certificates.bounds(uses.map { Certificates.fact(program, it) }, score, optimum)) return null
        return BranchAndBound.Incumbent(values, DeltaRational.of(optimum))
    }

    /** A nonbasic column's exact value: the bound the guess left it at, or zero when free. */
    private fun nonbasic(
        program: LinearProgram,
        j: Int,
        atUpper: Boolean,
    ): Rational? {
        val column = program.columns[j]
        if (atUpper) return column.upper
        return column.lower ?: Rational.ZERO.takeIf { zero -> column.upper.let { it == null || zero <= it } }
    }

    private fun use(
        index: Int,
        multiplier: Rational,
    ): Simplex.Use? =
        when {
            multiplier > Rational.ZERO -> Simplex.Use(index, true, multiplier)
            multiplier < Rational.ZERO -> Simplex.Use(index, false, -multiplier)
            else -> null
        }

    /** Whether [use]'s bound exists: a dual of the wrong sign leaves the guess unproven rather than failing. */
    private fun usable(
        program: LinearProgram,
        use: Simplex.Use,
    ): Boolean {
        val n = program.columns.size
        if (use.index < n) return (if (use.upper) program.columns[use.index].upper else program.columns[use.index].lower) != null
        return use.upper || program.rows[use.index - n].relation == LinearRelation.EQUAL
    }

    /**
     * Solves the square system `matrix·x = rhs` exactly, or null when it is singular: rows are scaled to integers and
     * eliminated fraction-free (Bareiss), so every division is exact and no intermediate needs a gcd.
     */
    fun solve(
        matrix: List<List<Rational>>,
        rhs: List<Rational>,
    ): List<Rational>? {
        val size = rhs.size
        val m =
            Array(size) { i ->
                val row = matrix[i] + rhs[i]
                val scale = row.fold(BigInteger.ONE) { lcm, it -> lcm / lcm.gcd(it.denominator) * it.denominator }
                Array(size + 1) { j -> row[j].numerator * (scale / row[j].denominator) }
            }
        var previous = BigInteger.ONE
        for (k in 0 until size) {
            val pivot = (k until size).firstOrNull { m[it][k].signum != 0 } ?: return null
            if (pivot != k) m[k] = m[pivot].also { m[pivot] = m[k] }
            for (i in k + 1 until size) {
                for (j in k + 1..size) m[i][j] = (m[i][j] * m[k][k] - m[i][k] * m[k][j]) / previous
                m[i][k] = BigInteger.ZERO
            }
            previous = m[k][k]
        }
        val x = MutableList(size) { Rational.ZERO }
        for (i in size - 1 downTo 0) {
            var rest = Rational.of(m[i][size])
            for (j in i + 1 until size) rest -= Rational.of(m[i][j]) * x[j]
            x[i] = rest / Rational.of(m[i][i])
        }
        return x
    }
}
