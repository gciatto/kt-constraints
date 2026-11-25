package io.github.gciatto.ktconstraints.solve.auto

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.incremental.IncrementalConstraintSolver
import io.github.gciatto.ktconstraints.solve.spi.BackendAvailability
import io.github.gciatto.ktconstraints.solve.spi.Platform
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.solve.spi.SolverBackendLoader
import io.github.gciatto.ktconstraints.solve.spi.ranking.RankingPolicy
import io.github.gciatto.ktconstraints.solve.spi.registry.SolverRegistry
import io.github.gciatto.ktconstraints.solve.spi.support.SupportReport

/**
 * A backend that picks, per model, among [backends]: it ranks the eligible ones by [policy] on [platform] and runs
 * them with [strategy]. Its solvers answer with the descriptor of the route that produced each result, list every
 * skipped or failed route in the result's metadata messages, and report `UNKNOWN` from this backend's own
 * descriptor (`auto/fallback` or `auto/race`) when no route answers. Enumerations and incremental sessions go to the
 * best route offering them. Closing it closes [backends], which own loaded runtimes.
 */
class AutoBackend(
    backends: List<SolverBackend>,
    /** How ranked routes run. */
    val strategy: AutoStrategy = AutoStrategy.Fallback,
    private val policy: RankingPolicy = RankingPolicy.DEFAULT,
    private val platform: Platform = Platform.current(),
) : SolverBackend {
    private val backends: List<SolverBackend> = backends.toList()

    /** The routes this backend chooses among, in tie-breaking order. */
    val registry: SolverRegistry = SolverRegistry.of(this.backends)

    /** `auto/<strategy>`, with the batch and session capabilities that at least one route declares. */
    override val descriptor: SolverDescriptor =
        SolverDescriptor(
            ENGINE,
            strategy.route,
            this.backends
                .flatMap { it.descriptor.capabilities }
                .filter { it in DELEGATED_CAPABILITIES }
                .toSet(),
        )

    override fun availability(): BackendAvailability {
        val reasons = backends.map { it.descriptor.id to it.availability() }
        return if (reasons.any { it.second.available }) {
            BackendAvailability(true)
        } else {
            val explanation = reasons.joinToString("; ") { (id, availability) -> "$id: ${availability.reason}" }
            BackendAvailability(false, if (backends.isEmpty()) "No route is registered" else "No route is available: $explanation")
        }
    }

    private val portfolio = Portfolio(registry, policy, platform, descriptor)

    override fun support(
        model: ConstraintModel,
        options: SolveOptions,
    ): SupportReport = portfolio.support(model, options)

    override fun createSolver(): IncrementalConstraintSolver = AutoSolver(descriptor, portfolio, strategy)

    override fun close() = backends.forEach { it.close() }

    /** Factories for the routes bundled with this module. */
    companion object : SolverBackendLoader<AutoBackend> {
        /** Engine identifier of every automatic backend. */
        const val ENGINE: String = "auto"

        private val DELEGATED_CAPABILITIES =
            setOf(
                SolverCapability.SATISFACTION,
                SolverCapability.OPTIMIZATION,
                SolverCapability.ENUMERATION,
                SolverCapability.INTEGER_ARITHMETIC,
                SolverCapability.REAL_ARITHMETIC,
                SolverCapability.BITVECTORS,
                SolverCapability.GLOBAL_CONSTRAINTS,
                SolverCapability.INCREMENTAL_ASSERTIONS,
                SolverCapability.BACKTRACKING,
                SolverCapability.ASSUMPTIONS,
                SolverCapability.UNSAT_CORES,
            )

        /**
         * Loads the routes bundled for the current platform, fastest first according to the recorded benchmarks: on
         * the JVM CP-SAT, Choco, pure Kotlin, Z3, SCIP and HiGHS; on JS pure Kotlin and Z3 Wasm, with MiniZinc's Node CLI between them; on Native pure
         * Kotlin and Z3 through its C API; MiniZinc's routes need `MINIZINC_EXECUTABLE`, and come last
         * on the JVM and Native. The default [policy] keeps that order: each model goes to the first route that has its capabilities,
         * is available and supports it. Pass [RankingPolicy.DEFAULT] to rank routes by model features instead. Close
         * the result to release loaded runtimes.
         */
        suspend fun load(
            strategy: AutoStrategy,
            policy: RankingPolicy = RankingPolicy.REGISTRATION_ORDER,
        ): AutoBackend = AutoBackend(DefaultBackends.load(), strategy, policy)

        /** Loads the bundled routes with the [AutoStrategy.Fallback] strategy, in their platform performance order. */
        override suspend fun load(): AutoBackend = load(AutoStrategy.Fallback)
    }
}
