package io.github.gciatto.ktconstraints.solve.linear

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.bool.BoolVar
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.core.real.RealVar
import io.github.gciatto.ktconstraints.solve.assignment.Assignment
import org.gciatto.kt.math.BigInteger

/**
 * Exact values for every column of [program], checked exactly. [violation] is the largest amount by which a bound or
 * a row is exceeded (a strict row counts as exceeded by `0` when it holds with equality, so check [isFeasible] for
 * exactness); [assignment] reads the values back as the model's variables, once every integer column is integral.
 */
class LinearSolution(
    /** The program solved. */
    val program: LinearProgram,
    /** One exact value per column. */
    val values: List<Rational>,
) {
    init {
        require(values.size == program.columns.size) { "Expected ${program.columns.size} values, got ${values.size}" }
    }

    /** Whether every bound, row and integrality requirement holds exactly. */
    val isFeasible: Boolean get() = violation == Rational.ZERO && isIntegral && strictRowsHold

    /** Whether every integer column has an integer value. */
    val isIntegral: Boolean get() = program.columns.indices.all { !program.columns[it].integer || values[it].isInteger }

    /** The largest exact excess over a bound or a row's right-hand side, or zero when none is exceeded. */
    val violation: Rational by lazy {
        val bounds =
            program.columns.withIndex().flatMap { (i, column) ->
                listOfNotNull(column.lower?.let { it - values[i] }, column.upper?.let { values[i] - it })
            }
        val rows =
            program.rows.map { row ->
                val excess = lhs(row) - row.rhs
                if (row.relation == LinearRelation.EQUAL) abs(excess) else excess
            }
        (bounds + rows).fold(Rational.ZERO) { worst, excess -> if (excess > worst) excess else worst }
    }

    /** The exact objective value, or null without an objective. */
    val objectiveValue: Rational? get() =
        program.objective?.let { goal -> goal.terms.entries.fold(goal.constant) { total, (i, c) -> total + c * values[i] } }

    /** The model's assignment; fails unless every integer column is integral. */
    fun assignment(): Assignment {
        check(isIntegral) { "Integer columns need integer values" }
        val booleans = mutableMapOf<BoolVar, Boolean>()
        val integers = mutableMapOf<IntVar, BigInteger>()
        val reals = mutableMapOf<RealVar, Rational>()
        program.columns.forEachIndexed { i, column ->
            when (val variable = column.variable) {
                is BoolVar -> booleans[variable] = values[i] != Rational.ZERO
                is IntVar -> integers[variable] = values[i].numerator
                is RealVar -> reals[variable] = values[i]
            }
        }
        return Assignment.of(booleans, integers, reals)
    }

    private val strictRowsHold: Boolean get() = program.rows.all { it.relation != LinearRelation.LESS || lhs(it) < it.rhs }

    private fun lhs(row: LinearRow): Rational = row.terms.entries.fold(Rational.ZERO) { total, (i, c) -> total + c * values[i] }

    /** Readings of floating-point solutions. */
    companion object {
        /**
         * Reads floating-point [values] exactly, rounding each integer column to its nearest integer when it lies within
         * [integralityTolerance]; fails when one does not, since such a value is not a solution at any tolerance.
         */
        fun ofDoubles(
            program: LinearProgram,
            values: List<Double>,
            integralityTolerance: Double,
        ): LinearSolution {
            val tolerance = exactRational(integralityTolerance)
            val exact =
                values.mapIndexed { i, value ->
                    val rational = exactRational(value)
                    if (!program.columns[i].integer) {
                        rational
                    } else {
                        val rounded = Rational.of(nearest(rational))
                        check(abs(rational - rounded) <= tolerance) { "Column $i is $value, not an integer within $integralityTolerance" }
                        rounded
                    }
                }
            return LinearSolution(program, exact)
        }
    }
}
