package io.github.gciatto.ktconstraints.solve.kotlin

/**
 * The environment variable [name] of the test process, or null where unset or unavailable. Kept in test sources: a
 * shared `nativeMain` in `:test-solve` fails metadata compilation on a KLIB name clash with Kotest's xmlutil `core`.
 */
internal expect fun benchmarkEnvironment(name: String): String?
