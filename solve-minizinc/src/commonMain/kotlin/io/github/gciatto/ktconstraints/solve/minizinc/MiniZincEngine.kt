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
    /** Whether this route accepts affine arithmetic and non-enumerating MIP search. */
    val isMip: Boolean = false,
) {
    /** Gecode's finite integer subset. */
    GECODE("gecode", "org.gecode.gecode", BigInteger.of(2_147_483_646)),

    /** Chuffed excludes +/-500,000,000, which are reserved as unbounded sentinels. */
    CHUFFED("chuffed", "org.chuffed.chuffed", BigInteger.of(499_999_999)),

    /** CP-SAT initially shares Gecode's checked subset, rather than claiming its full int64 capacity. */
    CP_SAT("cp-sat", "cp-sat", BigInteger.of(2_147_483_646)),

    /** HiGHS integer MIP subset, without exhaustive enumeration. */
    HIGHS("highs", "org.minizinc.mip.highs", BigInteger.of(1_000_000), true),

    /** CBC integer MIP subset, without exhaustive enumeration. */
    CBC("cbc", "org.minizinc.mip.coin-bc", BigInteger.of(1_000_000), true),
    ;

    /** Capabilities covered by the shared contracts for this engine/route. */
    val descriptor: SolverDescriptor =
        SolverDescriptor(
            engineId,
            "minizinc",
            setOf(
                SolverCapability.SATISFACTION,
                SolverCapability.OPTIMIZATION,
                SolverCapability.INTEGER_ARITHMETIC,
                SolverCapability.GLOBAL_CONSTRAINTS,
            ) + if (isMip) emptySet() else setOf(SolverCapability.ENUMERATION),
        )
}
