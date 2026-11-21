package io.github.gciatto.ktconstraints.solve.spi

/** Kotlin runtime a backend would execute in. */
enum class Runtime {
    /** The JVM, including Android. */
    JVM,

    /** Kotlin/JS on Node.js. */
    NODE,

    /** Kotlin/JS in a browser or worker. */
    BROWSER,

    /** Kotlin/Native. */
    NATIVE,
}
