package io.github.gciatto.ktconstraints.test.solve

/** Reads the JVM process environment. */
actual fun benchmarkEnvironment(name: String): String? = System.getenv(name)
