package io.github.gciatto.ktconstraints.solve.spi.ranking
import io.github.gciatto.ktconstraints.solve.ExperimentalKtConstraintsApi
import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.spi.Platform
import io.github.gciatto.ktconstraints.solve.spi.feature.ModelFeatures
import io.github.gciatto.ktconstraints.solve.spi.feature.ObjectiveKind
import io.github.gciatto.ktconstraints.solve.spi.feature.ProblemClass
import kotlin.math.log10

/**
 * Built-in ranking heuristics. Engines are grouped by paradigm from their engine ID: SMT (`z3`, `cvc5`), CP
 * (`choco`, `cp-sat`, `gecode`, `chuffed`) and MIP (`scip`, `highs`, `cbc`, `gurobi`). Unknown engines get no opinion.
 */
object Heuristics {
    private const val SMT = "SMT"
    private const val CP = "CP"
    private const val MIP = "MIP"
    private const val UNUSED_CAPABILITY_PENALTY = 0.05
    private const val MAX_UNUSED_PENALTY = 0.25
    private const val COMPILED_ROUTE_PENALTY = 0.5
    private const val SECONDS_PER_UNIT = 3.0
    private val paradigms =
        mapOf("z3" to SMT, "cvc5" to SMT) +
            listOf("choco", "cp-sat", "gecode", "chuffed").associateWith { CP } +
            listOf("scip", "highs", "cbc", "gurobi").associateWith { MIP }

    /** Prefers backends offering few capabilities the model does not need: a cheap proxy for specialisation. */
    val capabilityFit: RankingHeuristic =
        heuristic("capability-fit") { features, descriptor, _ ->
            val unused = descriptor.capabilities - requiredCapabilities(features)
            Score(-(unused.size * UNUSED_CAPABILITY_PENALTY).coerceAtMost(MAX_UNUSED_PENALTY), "${unused.size} capabilities unused")
        }

    /** Matches the model's [ProblemClass] to the engine's paradigm. */
    val featureAffinity: RankingHeuristic =
        heuristic("feature-affinity") { features, descriptor, _ ->
            paradigms[descriptor.engine]?.let { paradigm -> affinity(features, paradigm, descriptor.engine) }
        }

    /** Prefers in-process routes (direct, OR-Tools, Wasm, Native) to routes compiling through MiniZinc. */
    val routeOverhead: RankingHeuristic =
        heuristic("route-overhead") { _, descriptor, platform ->
            if ("minizinc" in descriptor.route) {
                Score(-COMPILED_ROUTE_PENALTY, "compiles through MiniZinc on ${platform.runtime}")
            } else {
                Score(0.0, "runs in process")
            }
        }

    /**
     * Scores the routes of [engines] -1 and gives others no opinion. Weighted heavily, it ranks those routes after
     * every other eligible route, so they only answer models no other route can take.
     */
    fun lastResort(engines: Set<String>): RankingHeuristic =
        heuristic("last-resort") { _, descriptor, _ ->
            if (descriptor.engine in engines) Score(-1.0, "${descriptor.engine} is a last resort") else null
        }

    /** Converts [evidence] into scores: one unit lost per thousandfold slowdown; no opinion without evidence. */
    @ExperimentalKtConstraintsApi
    fun evidence(evidence: PerformanceEvidence): RankingHeuristic =
        heuristic("evidence") { features, descriptor, _ ->
            evidence.expectedMillis(features.problemClass, descriptor.id, features)?.let { millis ->
                Score(-log10(1 + millis) / SECONDS_PER_UNIT, "expected ${millis}ms on ${features.problemClass}")
            }
        }

    /** Capabilities a model with [features] needs from any backend. */
    fun requiredCapabilities(features: ModelFeatures): Set<SolverCapability> =
        buildSet {
            add(SolverCapability.SATISFACTION)
            if (features.objective != ObjectiveKind.NONE) add(SolverCapability.OPTIMIZATION)
            if (features.usesIntegers) add(SolverCapability.INTEGER_ARITHMETIC)
            if (features.usesReals) add(SolverCapability.REAL_ARITHMETIC)
            if (features.usesBitVectors) add(SolverCapability.BITVECTORS)
            if (features.globals.isNotEmpty()) add(SolverCapability.GLOBAL_CONSTRAINTS)
            if (features.realObjective && features.objective == ObjectiveKind.NONLINEAR) add(SolverCapability.NONLINEAR_REAL_OBJECTIVES)
        }

    @Suppress("CyclomaticComplexMethod", "MagicNumber") // One row per (problem class, paradigm) pair of the table.
    private fun affinity(
        features: ModelFeatures,
        paradigm: String,
        engine: String,
    ): Score =
        when (features.problemClass) {
            ProblemClass.LINEAR_PROGRAM, ProblemClass.MIXED_INTEGER_PROGRAM ->
                when (paradigm) {
                    MIP -> Score(1.0, "$MIP solves linear programs by simplex and branch and bound")
                    SMT -> Score(0.5, "$SMT optimizes linear arithmetic exactly")
                    else -> Score(0.0, "$paradigm handles linear programs")
                }
            ProblemClass.BINARY_PROGRAM ->
                when {
                    engine == "cp-sat" -> Score(1.0, "CP-SAT is a SAT-based engine for 0-1 programs")
                    paradigm == MIP -> Score(if (features.objective == ObjectiveKind.NONE) 0.5 else 1.0, "$MIP suits 0-1 programs")
                    paradigm == CP -> Score(0.5, "$CP handles 0-1 programs")
                    else -> Score(0.25, "$SMT handles 0-1 programs")
                }
            ProblemClass.SMT_REAL, ProblemClass.SMT_BITVECTOR ->
                if (paradigm == SMT) Score(1.0, "$SMT suits ${features.problemClass}") else Score(-1.0, "$paradigm lacks SMT sorts")
            ProblemClass.LINEAR_INTEGER ->
                when (paradigm) {
                    MIP -> Score(if (features.objective == ObjectiveKind.NONE) 0.5 else 1.0, "$MIP suits linear integer models")
                    CP -> Score(0.5, "$CP handles linear integer models")
                    else -> Score(0.0, "$SMT handles linear integer models")
                }
            ProblemClass.NONLINEAR_INTEGER ->
                when (paradigm) {
                    CP -> Score(1.0, "$CP propagates products natively")
                    SMT -> Score(0.5, "$SMT handles non-linear integers")
                    else -> Score(-1.0, "$MIP needs affine products")
                }
            ProblemClass.CP_GLOBALS ->
                when (paradigm) {
                    CP -> Score(1.0, "$CP propagates globals and reification natively")
                    SMT -> Score(0.25, "$SMT decomposes globals")
                    else -> Score(-0.5, "$MIP decomposes globals into big-M rows")
                }
            ProblemClass.PURE_BOOLEAN ->
                when {
                    engine == "cp-sat" -> Score(1.0, "CP-SAT is a SAT-based engine")
                    paradigm == MIP -> Score(0.0, "$MIP handles 0/1 models")
                    else -> Score(0.5, "$paradigm handles Boolean models")
                }
        }

    private fun heuristic(
        name: String,
        score: (ModelFeatures, SolverDescriptor, Platform) -> Score?,
    ): RankingHeuristic =
        object : RankingHeuristic {
            override val name: String = name

            override fun score(
                features: ModelFeatures,
                descriptor: SolverDescriptor,
                platform: Platform,
            ): Score? = score(features, descriptor, platform)

            override fun toString(): String = name
        }
}
