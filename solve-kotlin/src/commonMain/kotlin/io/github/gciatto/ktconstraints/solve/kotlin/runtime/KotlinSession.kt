package io.github.gciatto.ktconstraints.solve.kotlin.runtime
import io.github.gciatto.ktconstraints.core.Variable
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.assignment.project
import io.github.gciatto.ktconstraints.solve.incremental.CheckResult
import io.github.gciatto.ktconstraints.solve.incremental.CheckStatus
import io.github.gciatto.ktconstraints.solve.incremental.Checkpoint
import io.github.gciatto.ktconstraints.solve.incremental.IncrementalSession
import io.github.gciatto.ktconstraints.solve.kotlin.KotlinConfiguration
import io.github.gciatto.ktconstraints.solve.kotlin.KotlinSupport
import io.github.gciatto.ktconstraints.solve.kotlin.search.SearchBudget
import io.github.gciatto.ktconstraints.solve.kotlin.search.SearchStopped
import io.github.gciatto.ktconstraints.solve.spi.exception.UnsupportedModelException
import io.github.gciatto.ktconstraints.solve.spi.managed.CheckpointStack
import kotlin.concurrent.atomics.AtomicBoolean
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

    private suspend fun checkActive(
        assumptions: List<BoolExpr>,
        budget: SearchBudget,
    ): CheckResult {
        val current =
            engine ?: KotlinEngine(model, configuration, budget).also {
                it.initialize(budget, includeObjective = false)
                engine = it
            }
        current.circuit.useBudget(budget)
        val declared = declarations.flatten()
        for (variable in declared) current.translation.declare(variable)
        val active = frames.flatten()
        val literals = mutableListOf<Int>()
        for (assertion in active) {
            budget.checkpoint()
            val literal = assertion.literal ?: current.literal(assertion.expression, budget).also { assertion.literal = it }
            literals += literal
        }
        for (assumption in assumptions) literals += current.literal(assumption, budget)
        val full = current.check(literals.toIntArray(), budget)
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
        if (closed.compareAndSet(false, true)) onClosed(this)
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
