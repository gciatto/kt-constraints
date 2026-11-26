package io.github.gciatto.ktconstraints.solve.kotlin.runtime
import io.github.gciatto.ktconstraints.core.Variable
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.Objective
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.assignment.Assignment
import io.github.gciatto.ktconstraints.solve.assignment.project
import io.github.gciatto.ktconstraints.solve.incremental.CheckResult
import io.github.gciatto.ktconstraints.solve.incremental.CheckStatus
import io.github.gciatto.ktconstraints.solve.incremental.Checkpoint
import io.github.gciatto.ktconstraints.solve.incremental.IncrementalSession
import io.github.gciatto.ktconstraints.solve.kotlin.KotlinConfiguration
import io.github.gciatto.ktconstraints.solve.kotlin.KotlinSupport
import io.github.gciatto.ktconstraints.solve.kotlin.lp.LinearPrograms
import io.github.gciatto.ktconstraints.solve.kotlin.search.SearchBudget
import io.github.gciatto.ktconstraints.solve.kotlin.search.SearchStopped
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.spi.exception.UnsupportedModelException
import io.github.gciatto.ktconstraints.solve.spi.incremental.SessionModels
import io.github.gciatto.ktconstraints.solve.spi.managed.CheckpointStack
import io.github.gciatto.ktconstraints.solve.stream.forEachSolution
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/** User frames select assumptions; search decision levels and learned clauses have independent lifetimes. */
@OptIn(ExperimentalAtomicApi::class)
internal class KotlinSession(
    private val model: ConstraintModel,
    override val descriptor: SolverDescriptor,
    private val configuration: KotlinConfiguration,
    private val onClosed: (KotlinSession) -> Unit,
) : IncrementalSession {
    private val closed = AtomicBoolean(false)
    private val busy = AtomicBoolean(false)
    private val checkpoints = CheckpointStack()
    private val frames = mutableListOf(mutableListOf<SessionAssertion>())
    private val declarations = mutableListOf(mutableListOf<Variable>())
    private val known = model.variables.toMutableSet()
    private var engine: KotlinEngine? = null

    /** The branch-and-bound stream of an optimization in progress, so that closing the session cancels it. */
    private val linear = AtomicReference<KotlinLinearStream?>(null)

    override fun declare(variable: Variable) =
        exclusive {
            require(known.add(variable)) { "Variable already in the session: ${variable.name}" }
            declarations.last() += variable
        }

    override fun add(constraint: BoolExpr) =
        exclusive {
            requireAccepted(listOf(constraint))
            frames.last() += SessionAssertion(constraint)
        }

    override fun push(): Checkpoint =
        exclusive {
            frames.add(mutableListOf())
            declarations.add(mutableListOf())
            checkpoints.push()
        }

    override fun popTo(checkpoint: Checkpoint) =
        exclusive {
            val count = checkpoints.popTo(checkpoint)
            repeat(count) { frames.removeAt(frames.lastIndex) }
            known -= List(count) { declarations.removeAt(declarations.lastIndex) }.flatten().toSet()
        }

    override suspend fun check(
        assumptions: List<BoolExpr>,
        timeoutMillis: Long?,
    ): CheckResult =
        exclusive {
            require(timeoutMillis == null || timeoutMillis >= 0)
            val temporary = assumptions.toList()
            requireAccepted(temporary)
            val budget = SearchBudget(timeoutMillis, configuration.workLimit) { closed.load() }
            try {
                budget.poll()
                budget.checkpoint()
                checkActive(temporary, budget)
            } catch (stopped: SearchStopped) {
                CheckResult(CheckStatus.UNKNOWN, null, descriptor, stopped.reason)
            }
        }

    /**
     * Linear snapshots (real objectives, and integer ones over linear constraints) go to branch and bound, as batch
     * solves do; any other integer objective is bisected on the session's own engine, with each bound an assumption,
     * so compiled definitions and learned clauses carry over to later checks and optimizations.
     * Branch and bound deliberately starts afresh on each call: keeping the exact tableau between calls was measured to
     * be 2x slower on facility location and 20x slower on dense LPs, because exact pivots from a warm basis carry the
     * huge fractions that the floating-point start avoids.
     */
    override suspend fun optimize(
        objective: Objective,
        assumptions: List<BoolExpr>,
        timeoutMillis: Long?,
    ): SolveResult =
        exclusive {
            require(timeoutMillis == null || timeoutMillis >= 0)
            val temporary = assumptions.toList()
            requireAccepted(temporary)
            val declared = declarations.flatten()
            val snapshot = SessionModels.of(model, declared, frames.flatten().map { it.expression } + temporary, objective)
            if (LinearPrograms.handles(snapshot)) {
                val report = LinearPrograms.report(snapshot)
                if (!report.isSupported) throw UnsupportedModelException(report)
                return@exclusive optimizeLinear(snapshot, timeoutMillis)
            }
            val report = KotlinSupport.report(snapshot)
            if (!report.isSupported) throw UnsupportedModelException(report)
            val budget = SearchBudget(timeoutMillis, configuration.workLimit) { closed.load() }
            val search = SessionOptimization(objective, descriptor, model.outputVariables + declared)
            try {
                budget.poll()
                budget.checkpoint()
                search.run(this, temporary, budget)
            } catch (stopped: SearchStopped) {
                search.stopped(stopped.reason)
            }
        }

    private suspend fun optimizeLinear(
        snapshot: ConstraintModel,
        timeoutMillis: Long?,
    ): SolveResult {
        val stream = KotlinLinearStream(snapshot, SolveOptions(timeoutMillis = timeoutMillis), descriptor) {}
        linear.store(stream)
        try {
            if (closed.load()) stream.cancel()
            return stream.forEachSolution {}
        } finally {
            linear.store(null)
            stream.close()
        }
    }

    /** The engine and the literals of the active assertions plus [assumptions]. */
    internal suspend fun active(
        assumptions: List<BoolExpr>,
        budget: SearchBudget,
    ): Pair<KotlinEngine, IntArray> {
        val current =
            engine ?: KotlinEngine(model, configuration, budget).also {
                it.initialize(budget, includeObjective = false)
                engine = it
            }
        current.circuit.useBudget(budget)
        for (variable in declarations.flatten()) current.translation.declare(variable)
        val literals = mutableListOf<Int>()
        for (assertion in frames.flatten()) {
            budget.checkpoint()
            val literal = assertion.literal ?: current.literal(assertion.expression, budget).also { assertion.literal = it }
            literals += literal
        }
        for (assumption in assumptions) literals += current.literal(assumption, budget)
        return current to literals.toIntArray()
    }

    /** Throws when [full] violates an active assertion or one of [assumptions]. */
    internal fun validate(
        current: KotlinEngine,
        full: Assignment,
        assumptions: List<BoolExpr>,
    ) = current.validate(full, frames.flatten().map { it.expression } + assumptions)

    private suspend fun checkActive(
        assumptions: List<BoolExpr>,
        budget: SearchBudget,
    ): CheckResult {
        val (current, literals) = active(assumptions, budget)
        val declared = declarations.flatten()
        val active = frames.flatten()
        val full = current.check(literals, budget)
        return if (full == null) {
            CheckResult(CheckStatus.UNSATISFIABLE, null, descriptor, unsatCore = assumptions, metadata = current.metadata(budget))
        } else {
            current.validate(full, active.map { it.expression } + assumptions)
            CheckResult(
                CheckStatus.SATISFIABLE,
                full.project(model.outputVariables + declared),
                descriptor,
                metadata = current.metadata(budget),
            )
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            linear.load()?.cancel()
            onClosed(this)
        }
        // The active coroutine owns arrays until its checkpoint observes close; clearing them here would race.
    }

    private fun requireAccepted(expressions: List<BoolExpr>) {
        val report = KotlinSupport.report(expressions)
        if (!report.isSupported) throw UnsupportedModelException(report)
        require(expressions.all { expression -> expression.variables.all { it in known } }) { "Variable not declared in the session" }
    }

    private inline fun <R> exclusive(action: () -> R): R {
        check(!closed.load()) { "Session is closed" }
        check(busy.compareAndSet(false, true)) { "Only one consumer may use a session" }
        try {
            check(!closed.load()) { "Session is closed" }
            return action()
        } finally {
            busy.store(false)
        }
    }
}
