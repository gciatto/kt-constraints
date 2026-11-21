package io.github.gciatto.ktconstraints.solve.kotlin.sat

/** Boolean assignment indexed by positive variable IDs; index zero is unused. */
internal class SatResult(
    val status: SatStatus,
    val values: BooleanArray? = null,
) {
    init {
        require((status == SatStatus.SATISFIABLE) == (values != null))
    }
}
