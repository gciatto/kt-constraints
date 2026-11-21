package io.github.gciatto.ktconstraints.solve.kotlin

import io.github.gciatto.ktconstraints.core.BitVecVar
import io.github.gciatto.ktconstraints.core.BoolVar
import io.github.gciatto.ktconstraints.core.GroundEvaluator
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.core.RealVar
import io.github.gciatto.ktconstraints.core.Variable
import io.github.gciatto.ktconstraints.solve.Assignment
import io.github.gciatto.ktconstraints.solve.CheckStatus
import io.github.gciatto.ktconstraints.solve.project
import kotlinx.coroutines.runBlocking

/** Manual raw JVM phase measurements, invoked only by `:solve-kotlin:jvmBenchmark`, outside routine check. */
object KotlinBenchmark {
    private const val TIMEOUT_MILLIS = 30_000L
    private const val SAMPLES = 3

    /** Prints raw samples and environment metadata; correctness failures or timeouts abort the run. */
    @JvmStatic
    fun main(args: Array<String>) =
        runBlocking {
            require(args.isEmpty()) { "This deterministic benchmark does not accept arguments" }
            printHeader()
            for (create in listOf(KotlinBenchmarkCases::sat, KotlinBenchmarkCases::scheduling, KotlinBenchmarkCases::mixedReal)) {
                repeat(SAMPLES) { sample -> measureSample(create, sample) }
            }
        }

    private suspend fun measureSample(create: () -> KotlinBenchmarkCase, sample: Int) {
        val elapsed = linkedMapOf<String, Long>()
        val case = timed(elapsed, "model_construction") { create() }
        val budget = SearchBudget(TIMEOUT_MILLIS, null) { false }
        val engine = timed(elapsed, "engine_setup") { KotlinEngine(case.model, KotlinConfiguration(), budget) }
        timed(elapsed, "presolve_and_compilation") { engine.initialize(budget, includeObjective = false) }
        val full = timed(elapsed, "first_check_decode_validate") { requireNotNull(engine.check(intArrayOf(), budget)) }
        val projected = timed(elapsed, "output_projection") { full.project(case.model.outputVariables) }
        check(case.model.constraints.all { GroundEvaluator.evaluate(it, full) }) { "Invalid benchmark witness: ${case.name}" }
        check(case.model.outputVariables.all { sameValue(it, full, projected) }) { "Projection changed a value: ${case.name}" }
        measureRestoration(case, elapsed)
        for ((phase, nanos) in elapsed) println("${case.name},$sample,$phase,$nanos")
        println("# ${case.name} sample=$sample engine_statistics=${engine.metadata(budget).statistics}")
    }

    private suspend fun measureRestoration(case: KotlinBenchmarkCase, elapsed: MutableMap<String, Long>) {
        KotlinBackend().create().use { solver ->
            timed(elapsed, "session_open") { solver.openSession(case.model) }.use { session ->
                val baseline = timed(elapsed, "session_initial_check") { session.check(timeoutMillis = TIMEOUT_MILLIS) }
                check(baseline.status == CheckStatus.SATISFIABLE) { "Initial session failed: ${case.name}, $baseline" }
                val checkpoint = timed(elapsed, "session_push_add") { session.push().also { session.add(case.pushed) } }
                val pushed = timed(elapsed, "session_pushed_check") { session.check(timeoutMillis = TIMEOUT_MILLIS) }
                check(pushed.status == CheckStatus.SATISFIABLE) { "Pushed session failed: ${case.name}, $pushed" }
                check(GroundEvaluator.evaluate(case.pushed, requireNotNull(pushed.assignment)))
                val conflict = timed(elapsed, "session_contradictory_assumption") {
                    session.check(listOf(case.restored), TIMEOUT_MILLIS)
                }
                check(conflict.status == CheckStatus.UNSATISFIABLE) { "Opposite branches did not conflict: ${case.name}, $conflict" }
                timed(elapsed, "session_pop") { session.popTo(checkpoint) }
                val restored = timed(elapsed, "session_restored_check") { session.check(listOf(case.restored), TIMEOUT_MILLIS) }
                check(restored.status == CheckStatus.SATISFIABLE) { "Restored session failed: ${case.name}, $restored" }
                check(GroundEvaluator.evaluate(case.restored, requireNotNull(restored.assignment)))
            }
        }
    }

    private fun sameValue(variable: Variable, full: Assignment, projected: Assignment): Boolean =
        when (variable) {
            is BoolVar -> full.valueOf(variable) == projected.valueOf(variable)
            is IntVar -> full.valueOf(variable) == projected.valueOf(variable)
            is RealVar -> full.valueOf(variable) == projected.valueOf(variable)
            is BitVecVar -> full.valueOf(variable) == projected.valueOf(variable)
            else -> error("Unsupported benchmark projection")
        }

    private inline fun <T> timed(elapsed: MutableMap<String, Long>, phase: String, action: () -> T): T {
        val started = System.nanoTime()
        return action().also { elapsed[phase] = System.nanoTime() - started }
    }

    private fun printHeader() {
        println("# kotlin/direct raw wall-clock nanoseconds; deterministic smoke workloads, no statistical performance claims")
        println("# Fresh model, engine and session per sample; one JVM; no warm-up, forks, GC control or averaging")
        println("# Sample 0 includes first-use costs for its case; later samples reuse the JVM with fresh solver state")
        println("# Initialization combines integer presolve and circuit compilation")
        println("# First check combines SAT, rational feasibility, assignment decoding and validation")
        println("# Session initial check includes its separate lazy engine setup and compilation")
        println("# Timeout: $TIMEOUT_MILLIS ms per engine initialization + first check, and per incremental check")
        println("# Kotlin ${KotlinVersion.CURRENT}; Java ${System.getProperty("java.version")}; ${System.getProperty("java.vm.name")}")
        println("# OS ${System.getProperty("os.name")} ${System.getProperty("os.version")}; ${System.getProperty("os.arch")}")
        println("# Available processors: ${Runtime.getRuntime().availableProcessors()}; samples per case: $SAMPLES")
        println("case,sample,phase,wall_ns")
    }
}
