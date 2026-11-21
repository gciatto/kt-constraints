package io.github.gciatto.ktconstraints.core

/** Global constraint: zero-based [successors] form a single cycle through every node. */
class Circuit(
    successors: Collection<IntExpr>,
) : DecomposableConstraint {
    /** Detached immutable successor of each node. */
    val successors: List<IntExpr> = buildList { addAll(successors) }

    override val children: List<Expr> get() = successors

    /**
     * Range and distinctness make the successors a permutation; walking from node 0 must not return to it in fewer than
     * `n` steps. The walk uses clamped element lookups, so it adds no definedness constraint.
     */
    override val decomposition: BoolExpr by lazy {
        val n = this.successors.size
        val parts = this.successors.map { Decompositions.inRange(it, n) }.toMutableList<BoolExpr>()
        if (n > 1) parts += AllDifferent(this.successors)
        var position = this.successors.firstOrNull()
        for (step in 1 until n) {
            val current = requireNotNull(position)
            parts += BoolNot(IntEquality(current, IntLiteral(0)))
            position = Element(Decompositions.clamp(current, n), this.successors)
        }
        // shortcut: the walk nests n element lookups, so lowering it is cubic in n; native routes avoid it.
        BoolAnd.of(parts)
    }

    /** Positions along the cycle from node 0: each arc into a node other than 0, self-loops included, increments it. */
    override fun decompose(auxiliaries: VariableFactory): BoolExpr {
        val n = successors.size
        if (n <= 1) return decomposition
        val position =
            listOf(IntLiteral(0)) + (1 until n).map { auxiliaries.intVar("circuit.position$it", IntDomain.of(1L, n - 1L)) }
        val arcs =
            successors.indices.flatMap { i ->
                (1 until n).map { j ->
                    BoolImplies(IntEquality(successors[i], IntLiteral(j)), IntEquality(position[j], IntSum(position[i], IntLiteral(1))))
                }
            }
        return BoolAnd.of(successors.map { Decompositions.inRange(it, n) } + AllDifferent(successors) + arcs)
    }

    override fun equals(other: Any?): Boolean = other is Circuit && successors == other.successors

    override fun hashCode(): Int = 17 * successors.hashCode() + 1

    override fun toString(): String = "circuit($successors)"
}
