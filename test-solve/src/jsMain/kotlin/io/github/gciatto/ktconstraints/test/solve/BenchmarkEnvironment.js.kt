package io.github.gciatto.ktconstraints.test.solve

private val environment: dynamic = js("(typeof process !== 'undefined' && process.env != null) ? process.env : null")

/** Reads Node's `process.env`; browsers have none. */
actual fun benchmarkEnvironment(name: String): String? = if (environment == null) null else environment[name] as? String
