package io.github.gciatto.ktconstraints.solve.spi

/** Where selection happens: the [runtime] and a lower-case operating-system family such as `linux` or `browser`. */
data class Platform(
    /** Kotlin runtime. */
    val runtime: Runtime,
    /** Operating-system family, lower case. */
    val os: String,
) {
    /** Detection. */
    companion object {
        /** The platform this code runs on. */
        fun current(): Platform = detectPlatform()
    }
}

/** Platform detection, one per Kotlin target. */
internal expect fun detectPlatform(): Platform
