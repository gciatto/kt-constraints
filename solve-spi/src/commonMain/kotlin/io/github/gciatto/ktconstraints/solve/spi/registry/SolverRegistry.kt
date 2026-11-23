package io.github.gciatto.ktconstraints.solve.spi.registry
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.spi.Platform
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.solve.spi.feature.ModelFeatures
import io.github.gciatto.ktconstraints.solve.spi.ranking.Contribution
import io.github.gciatto.ktconstraints.solve.spi.ranking.Heuristics
import io.github.gciatto.ktconstraints.solve.spi.ranking.RankingPolicy
import kotlin.jvm.JvmStatic

/** Immutable explicit registry; deterministic eligibility selection never instantiates a solver. */
class SolverRegistry private constructor(
    backends: Collection<SolverBackend>,
) {
    private val backends: List<SolverBackend> = buildList { addAll(backends) }

    init {
        require(
            this.backends
                .map { it.descriptor.id }
                .toSet()
                .size == this.backends.size,
        ) { "Duplicate backend route ID" }
    }

    /** Returns a registered route, failing explicitly for unknown IDs. */
    fun require(id: String): SolverBackend =
        requireNotNull(backends.firstOrNull { it.descriptor.id == id }) { "Backend not registered: $id" }

    /** The best-ranked eligible backend under [policy]; fails with every rejection when none qualifies. */
    fun select(
        model: ConstraintModel,
        options: SolveOptions = SolveOptions.DEFAULT,
        requiredCapabilities: Set<SolverCapability> = emptySet(),
        policy: RankingPolicy = RankingPolicy.DEFAULT,
        platform: Platform = Platform.current(),
    ): SolverBackend = rank(model, options, requiredCapabilities, policy, platform).best.backend

    /**
     * Filters registered backends by the capabilities [model] needs (from its features, plus [requiredCapabilities]),
     * runtime availability and whole-model support, then ranks the eligible ones by [policy] on [platform]. Ties keep
     * registration order. Never instantiates a solver.
     */
    fun rank(
        model: ConstraintModel,
        options: SolveOptions = SolveOptions.DEFAULT,
        requiredCapabilities: Set<SolverCapability> = emptySet(),
        policy: RankingPolicy = RankingPolicy.DEFAULT,
        platform: Platform = Platform.current(),
    ): Selection {
        val features = ModelFeatures.of(model)
        val required = Heuristics.requiredCapabilities(features) + requiredCapabilities
        val rejections = linkedMapOf<String, List<String>>()
        val eligible = mutableListOf<SolverBackend>()
        for (backend in backends) {
            val reasons = rejectionReasons(backend, model, options, required)
            if (reasons.isEmpty()) eligible += backend else rejections[backend.descriptor.id] = reasons
        }
        val candidates =
            eligible
                .map { backend ->
                    val contributions =
                        policy.heuristics.mapNotNull { (heuristic, weight) ->
                            heuristic.score(features, backend.descriptor, platform)?.let { Contribution(heuristic.name, weight, it) }
                        }
                    Candidate(backend, contributions)
                }.sortedByDescending { it.total }
        return Selection(features, candidates, rejections)
    }

    private fun rejectionReasons(
        backend: SolverBackend,
        model: ConstraintModel,
        options: SolveOptions,
        required: Set<SolverCapability>,
    ): List<String> {
        val missing = required - backend.descriptor.capabilities
        if (missing.isNotEmpty()) return missing.sortedBy { it.ordinal }.map { "Missing capability: $it" }
        val availability = backend.availability()
        if (!availability.available) return listOf(requireNotNull(availability.reason))
        return backend.support(model, options).unsupported
    }

    /** Portable factories; there is no classpath discovery or implicit registration. */
    companion object {
        /** Creates a registry in the given backend preference order. */
        @JvmStatic
        fun of(vararg backends: SolverBackend): SolverRegistry = SolverRegistry(backends.asList())

        /** Creates a registry in the order of [backends]. */
        @JvmStatic
        fun of(backends: List<SolverBackend>): SolverRegistry = SolverRegistry(backends)

        /** Creates a registry with no backends. Test fakes are never registered automatically. */
        @JvmStatic
        fun empty(): SolverRegistry = of()
    }
}
