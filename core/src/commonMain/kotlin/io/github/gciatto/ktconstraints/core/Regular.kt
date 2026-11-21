package io.github.gciatto.ktconstraints.core

import org.gciatto.kt.math.BigInteger

/** Global constraint: [automaton] accepts the word spelled by [sequence]. */
class Regular(
    sequence: Collection<IntExpr>,
    /** Deterministic automaton defining the accepted words. */
    val automaton: Automaton,
) : DecomposableConstraint {
    /** Detached immutable word. */
    val sequence: List<IntExpr> = buildList { addAll(sequence) }

    override val children: List<Expr> get() = sequence

    /**
     * Runs the automaton through element lookups in a dense transition table with an extra dead state; symbols outside
     * the alphabet are clamped onto dead columns, so every lookup is defined. Each state appears once in the next one.
     */
    override val decomposition: BoolExpr by lazy {
        val symbols = automaton.transitions.map { it.symbol }
        if (this.sequence.isEmpty() ||
            symbols.isEmpty()
        ) {
            return@lazy BoolLiteral(this.sequence.isEmpty() && automaton.initial in automaton.accepting)
        }
        val lowest = symbols.minOf { it }
        val columns = (symbols.maxOf { it } - lowest).toIntExact() + 3
        val dead = automaton.states
        val table =
            (0..dead).flatMap { state ->
                (0 until columns).map { column ->
                    val symbol = lowest + BigInteger.of(column - 1)
                    val next = if (state < dead && column in 1 until columns - 1) automaton.next(state, symbol) else null
                    IntLiteral(next ?: dead)
                }
            }
        val last =
            this.sequence.fold<IntExpr, IntExpr>(IntLiteral(automaton.initial)) { state, symbol ->
                val column =
                    IntMin(
                        listOf(
                            IntMax(listOf(LinearSum(listOf(symbol), listOf(BigInteger.ONE), BigInteger.ONE - lowest), IntLiteral(0))),
                            IntLiteral(
                                columns - 1,
                            ),
                        ),
                    )
                Element(LinearSum(listOf(state, column), listOf(BigInteger.of(columns), BigInteger.ONE)), table)
            }
        accepted(last)
    }

    /** One hidden state per position, each step an allowed-tuple table of the transitions. */
    override fun decompose(auxiliaries: VariableFactory): BoolExpr {
        if (sequence.isEmpty()) return decomposition
        val states =
            listOf(IntLiteral(automaton.initial)) +
                sequence.indices.map { auxiliaries.intVar("regular.state${it + 1}", IntDomain.of(0L, automaton.states - 1L)) }
        val rows = automaton.transitions.map { listOf(BigInteger.of(it.from), it.symbol, BigInteger.of(it.to)) }
        return BoolAnd.of(sequence.indices.map { Table(listOf(states[it], sequence[it], states[it + 1]), rows) } + accepted(states.last()))
    }

    private fun accepted(state: IntExpr): BoolExpr = Table(listOf(state), automaton.accepting.map { listOf(BigInteger.of(it)) })

    override fun equals(other: Any?): Boolean = other is Regular && sequence == other.sequence && automaton == other.automaton

    override fun hashCode(): Int = 31 * sequence.hashCode() + automaton.hashCode()

    override fun toString(): String = "regular($sequence, $automaton)"
}
