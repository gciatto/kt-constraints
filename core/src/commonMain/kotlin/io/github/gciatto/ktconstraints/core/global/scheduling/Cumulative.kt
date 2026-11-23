package io.github.gciatto.ktconstraints.core.global.scheduling
import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.bool.BoolAnd
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.bool.BoolToInt
import io.github.gciatto.ktconstraints.core.global.DecomposableConstraint
import io.github.gciatto.ktconstraints.core.integer.IntExpr
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntSum
import io.github.gciatto.ktconstraints.core.integer.arithmetic.LinearSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessThan
import org.gciatto.kt.math.BigInteger

/**
 * Global constraint: tasks starting at [starts], lasting the positive [durations] and using the non-negative
 * [demands] never use more than [capacity] at once. Task `i` runs over `starts[i] ≤ t < starts[i] + durations[i]`.
 */
class Cumulative(
    starts: Collection<IntExpr>,
    durations: Collection<BigInteger>,
    demands: Collection<BigInteger>,
    /** Non-negative resource capacity. */
    val capacity: BigInteger,
) : DecomposableConstraint {
    /** Detached immutable start of each task. */
    val starts: List<IntExpr> = buildList { addAll(starts) }

    /** Detached immutable positive duration of each task. */
    val durations: List<BigInteger> = buildList { addAll(durations) }

    /** Detached immutable non-negative demand of each task. */
    val demands: List<BigInteger> = buildList { addAll(demands) }

    init {
        require(this.starts.size == this.durations.size && this.starts.size == this.demands.size) {
            "Cumulative requires one duration and one demand per task"
        }
        require(this.durations.all { it.signum > 0 }) { "Cumulative durations must be positive" }
        require(this.demands.all { it.signum >= 0 }) { "Cumulative demands must be non-negative" }
        require(capacity.signum >= 0) { "Cumulative capacity must be non-negative" }
    }

    override val children: List<Expr> get() = starts

    /** The profile only rises when a demanding task starts, so it suffices to bound it at each such start. */
    override val decomposition: BoolExpr by lazy {
        val demanding = this.starts.indices.filter { this.demands[it].signum > 0 }
        BoolAnd.of(
            demanding.map { j ->
                val others = demanding.filter { it != j }
                val running =
                    others.map { i ->
                        BoolToInt(
                            BoolAnd(
                                IntLessOrEqual(this.starts[i], this.starts[j]),
                                IntLessThan(this.starts[j], IntSum(this.starts[i], IntLiteral(this.durations[i]))),
                            ),
                        )
                    }
                IntLessOrEqual(LinearSum(running, others.map { this.demands[it] }, this.demands[j]), IntLiteral(capacity))
            },
        )
    }

    override fun equals(other: Any?): Boolean =
        other is Cumulative &&
            starts == other.starts &&
            durations == other.durations &&
            demands == other.demands &&
            capacity == other.capacity

    override fun hashCode(): Int = ((31 * starts.hashCode() + durations.hashCode()) * 31 + demands.hashCode()) * 31 + capacity.hashCode()

    override fun toString(): String = "cumulative($starts, $durations, $demands, $capacity)"
}
