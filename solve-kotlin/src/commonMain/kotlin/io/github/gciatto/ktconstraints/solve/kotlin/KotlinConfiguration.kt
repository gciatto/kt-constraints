package io.github.gciatto.ktconstraints.solve.kotlin

/** Portable engine limits. Reaching a limit yields an unknown/feasible result, never a proof. */
data class KotlinConfiguration(
    /** Maximum width of one compiled arithmetic circuit. */
    val maxBitWidth: Int = 16_384,
    /** Maximum number of Boolean variables, including compiler auxiliaries. */
    val maxVariables: Int = 1_000_000,
    /** Maximum number of compiler clauses. */
    val maxClauses: Int = 4_000_000,
    /** Optional deterministic work budget, including compilation and arithmetic theory checks. */
    val workLimit: Long? = null,
) {
    init {
        require(maxBitWidth > 0 && maxVariables > 0 && maxClauses > 0)
        require(workLimit == null || workLimit >= 0)
    }
}
