package io.github.gciatto.ktconstraints.test.solve

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

/** Reads the process environment through POSIX `getenv`. */
@OptIn(ExperimentalForeignApi::class)
actual fun benchmarkEnvironment(name: String): String? = getenv(name)?.toKString()
