package io.github.gciatto.ktconstraints.solve.highs

import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.SolverDescriptor

/** Route identities of the direct HiGHS bindings. */
object HighsRoutes {
    private val CAPABILITIES =
        setOf(
            SolverCapability.SATISFACTION,
            SolverCapability.OPTIMIZATION,
            SolverCapability.INTEGER_ARITHMETIC,
            SolverCapability.REAL_ARITHMETIC,
            SolverCapability.FLOATING_POINT_REALS,
        )

    /** `highs/native`: the HiGHS C API on Kotlin/Native, loaded at run time. */
    val NATIVE: SolverDescriptor = SolverDescriptor("highs", "native", CAPABILITIES)

    /** `highs/wasm`: HiGHS compiled to WebAssembly, in JavaScript. */
    val WASM: SolverDescriptor = SolverDescriptor("highs", "wasm", CAPABILITIES)
}
