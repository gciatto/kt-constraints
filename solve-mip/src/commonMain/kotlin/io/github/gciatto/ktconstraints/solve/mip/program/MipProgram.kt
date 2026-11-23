package io.github.gciatto.ktconstraints.solve.mip.program
import io.github.gciatto.ktconstraints.core.bool.BoolVar
import io.github.gciatto.ktconstraints.core.integer.IntVar

/** An exact pure integer program and the program index of every source variable. */
internal data class MipProgram(
    val variables: List<MipVariable>,
    val rows: List<MipRow>,
    val objective: MipObjective?,
    val booleans: Map<BoolVar, Int>,
    val integers: Map<IntVar, Int>,
)
