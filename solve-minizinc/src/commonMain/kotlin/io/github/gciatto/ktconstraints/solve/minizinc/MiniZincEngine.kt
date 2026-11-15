package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import org.gciatto.kt.math.BigInteger

/** Tested MiniZinc engine configurations; availability is established separately by the JVM runtime. */
enum class MiniZincEngine(
    /** Stable engine identity, independent of its execution route. */
    val engineId: String,
    /** Exact MiniZinc configuration ID; no default solver substitution is allowed. */
    val configurationId: String,
    /** Conservative symmetric bound applied to literals, domains and all integer intermediates. */
    val maximumInteger: BigInteger,
) {
    /** Gecode's finite integer subset. */
    GECODE("gecode", "org.gecode.gecode", BigInteger.of(2_147_483_646)),

    /** Chuffed's deliberately conservative finite integer subset. */
    CHUFFED("chuffed", "org.chuffed.chuffed", BigInteger.of(500_000_000)),

    /** CP-SAT initially shares Gecode's checked subset, rather than claiming its full int64 capacity. */
    CP_SAT("cp-sat", "cp-sat", BigInteger.of(2_147_483_646)),
    ;

    /** Capabilities covered by the shared contracts for this engine/route. */
    val descriptor: SolverDescriptor =
        SolverDescriptor(
            engineId,
            "minizinc",
            setOf(
                SolverCapability.SATISFACTION,
                SolverCapability.OPTIMIZATION,
                SolverCapability.ENUMERATION,
                SolverCapability.INTEGER_ARITHMETIC,
                SolverCapability.GLOBAL_CONSTRAINTS,
            ),
        )
}
