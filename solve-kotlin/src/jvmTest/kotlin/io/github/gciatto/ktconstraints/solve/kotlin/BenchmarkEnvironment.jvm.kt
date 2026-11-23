package io.github.gciatto.ktconstraints.solve.kotlin

internal actual fun benchmarkEnvironment(name: String): String? = System.getenv(name)
