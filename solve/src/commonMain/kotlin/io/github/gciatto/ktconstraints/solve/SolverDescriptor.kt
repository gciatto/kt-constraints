package io.github.gciatto.ktconstraints.solve

/** Identifies an engine and its route (direct API versus MiniZinc compilation). */
class SolverDescriptor(
    /** Stable engine identifier. */
    val engine: String,
    /** Stable execution-route identifier. */
    val route: String,
    capabilities: Set<SolverCapability>,
) {
    /** Detached immutable capabilities in declaration order. */
    val capabilities: Set<SolverCapability> = buildSet { addAll(capabilities.sortedBy { it.ordinal }) }

    /** Distinguishes direct and compiler-mediated routes to the same engine. */
    val id: String get() = "$engine/$route"

    init {
        require(engine.isNotBlank() && '/' !in engine && engine.none { it.isWhitespace() }) { "Invalid engine ID: $engine" }
        require(route.isNotBlank() && '/' !in route && route.none { it.isWhitespace() }) { "Invalid route ID: $route" }
    }

    override fun equals(other: Any?): Boolean =
        other is SolverDescriptor && engine == other.engine && route == other.route && capabilities == other.capabilities

    override fun hashCode(): Int = 31 * (31 * engine.hashCode() + route.hashCode()) + capabilities.hashCode()

    override fun toString(): String = "$id $capabilities"
}
