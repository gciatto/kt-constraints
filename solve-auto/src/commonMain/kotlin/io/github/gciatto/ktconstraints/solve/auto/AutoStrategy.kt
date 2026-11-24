package io.github.gciatto.ktconstraints.solve.auto

/** How an [AutoBackend] runs its ranked routes; the strategy names the backend's route. */
sealed interface AutoStrategy {
    /** Route identifier of the [AutoBackend] descriptor. */
    val route: String

    /** Tries ranked routes one at a time under one shared deadline, moving on only after `UNKNOWN` or a failure. */
    data object Fallback : AutoStrategy {
        override val route: String = "fallback"
    }

    /**
     * Runs up to [parallelism] ranked routes at once under one shared deadline. The first proof wins and cancels the
     * rest; otherwise the best incumbent is returned without claiming optimality.
     */
    data class Race(
        /** Maximum number of routes running at once. */
        val parallelism: Int = 2,
    ) : AutoStrategy {
        override val route: String get() = "race"

        init {
            require(parallelism > 0) { "Parallelism must be positive" }
        }
    }
}
