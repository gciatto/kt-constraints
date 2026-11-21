package io.github.gciatto.ktconstraints.solve.mip

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.spi.SupportReport
import io.github.gciatto.ktconstraints.solve.spi.UnsupportedModelException
import org.gciatto.kt.math.BigInteger

/** Exact lowering of the Boolean/integer IR to a pure integer program; the support report is the lowering itself. */
internal object MipLowering {
    /** Bound on every literal, coefficient, domain bound and affine magnitude, keeping big-M rows exact in doubles. */
    val LIMIT: BigInteger = BigInteger.of(LIMIT_VALUE)

    const val HOLES = "domain holes -> binary interval selection"
    const val ALL_DIFFERENT = "allDifferent -> pairwise disequalities"
    const val TABLE = "table -> disjunction of tuple conjunctions"
    const val ELEMENT = "element -> top-level one-hot index selection with exact big-M value links"
    const val MIN_MAX = "min/max -> bounding rows plus a one-hot exact big-M link to the selected operand"
    const val REIFICATION = "nested Boolean structure -> exact big-M reification over integer bounds"

    private const val LIMIT_VALUE = 1_000_000L

    fun report(model: ConstraintModel): SupportReport = MipBuilder(model).run { SupportReport(unsupported, decompositions) }

    fun lower(model: ConstraintModel): MipProgram {
        val builder = MipBuilder(model)
        if (builder.unsupported.isNotEmpty()) throw UnsupportedModelException(SupportReport(builder.unsupported, builder.decompositions))
        return builder.program()
    }
}
