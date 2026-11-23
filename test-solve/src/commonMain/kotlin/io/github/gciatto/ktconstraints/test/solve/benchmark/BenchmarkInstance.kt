package io.github.gciatto.ktconstraints.test.solve.benchmark
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import org.gciatto.kt.math.BigInteger

/** A benchmark model with its known answer: the proof status a complete route must reach and the optimum, if any. */
class BenchmarkInstance(
    /** Family, such as `n-queens`. */
    val family: String,
    /** Size parameter within the family. */
    val size: Int,
    /** The model. */
    val model: ConstraintModel,
    /** `FEASIBLE` for satisfaction, `OPTIMAL` for optimization, `INFEASIBLE` for refutations. */
    val expectedStatus: SolveStatus,
    /** Known optimum, for optimization instances. */
    val expectedObjective: BigInteger? = null,
) {
    override fun toString(): String = "$family-$size"
}
