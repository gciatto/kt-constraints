package io.github.gciatto.ktconstraints.solve.kotlin

/** The environment variable [name] of the test process, or null where unset or unavailable. */
internal expect fun benchmarkEnvironment(name: String): String?
