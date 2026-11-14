package io.github.gciatto.ktconstraints.solve.spi

import io.github.gciatto.ktconstraints.core.Variable

/** Owned compiled representation with a stable mapping back to source variables. */
interface CompiledModel : AutoCloseable {
    /** Internal backend identifiers mapped to their original variables. */
    val sourceVariables: Map<String, Variable>
}
