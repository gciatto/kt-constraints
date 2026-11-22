package io.github.gciatto.ktconstraints.test.solve

/** The environment variable [name] of the test process, or null where unset or unavailable; opts benchmarks in. */
expect fun benchmarkEnvironment(name: String): String?
