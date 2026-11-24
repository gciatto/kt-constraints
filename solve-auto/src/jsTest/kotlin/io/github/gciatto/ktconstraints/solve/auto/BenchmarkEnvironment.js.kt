package io.github.gciatto.ktconstraints.solve.auto

private val environment: dynamic = js("(typeof process !== 'undefined' && process.env != null) ? process.env : null")

internal actual fun benchmarkEnvironment(name: String): String? = if (environment == null) null else environment[name] as? String
