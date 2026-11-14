package io.github.gciatto.ktconstraints.solve.spi

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.SolveOptions

/** Lossless whole-model compilation; unsupported semantics are rejected explicitly. */
interface ConstraintCompiler<out C : CompiledModel> {
    /** Assesses all constructs and requested options before execution. */
    fun support(
        model: ConstraintModel,
        options: SolveOptions = SolveOptions.DEFAULT,
    ): SupportReport

    /** Compiles the model or throws [UnsupportedModelException], without starting an engine. */
    fun compile(
        model: ConstraintModel,
        options: SolveOptions = SolveOptions.DEFAULT,
    ): C
}
