@file:OptIn(ExperimentalForeignApi::class, ExperimentalAtomicApi::class, ExperimentalNativeApi::class)

package io.github.gciatto.ktconstraints.solve.z3

import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_get_full_version
import io.github.gciatto.ktconstraints.solve.z3.capi.kz3_failure
import io.github.gciatto.ktconstraints.solve.z3.capi.kz3_load
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.experimental.ExperimentalNativeApi

/** Process-wide run-time loading of one Z3 C library, whose symbols back the `capi` shim. */
internal object NativeZ3Library {
    /** Environment variable naming the Z3 library file to load. */
    const val PATH_VARIABLE = "KT_CONSTRAINTS_Z3_LIBRARY"

    private val lock = AtomicInt(0)
    private var loadedPath: String? = null

    /** The configured library: [PATH_VARIABLE] when set, else the platform's default name on the loader path. */
    val defaultPath: String
        get() =
            getenv(PATH_VARIABLE)?.toKString()?.takeIf { it.isNotEmpty() }
                ?: when (Platform.osFamily) {
                    OsFamily.WINDOWS -> "libz3.dll"
                    OsFamily.LINUX, OsFamily.ANDROID -> "libz3.so"
                    else -> "libz3.dylib"
                }

    /** Loads [path] unless already loaded; returns why the library is unusable, or null. */
    fun load(path: String): String? {
        while (!lock.compareAndSet(0, 1)) {
            // Loading happens once per process; contention is brief.
        }
        try {
            val loaded = loadedPath
            return when {
                loaded == path -> null
                loaded != null -> "Z3 is already loaded from $loaded; one library per process"
                kz3_load(path) -> {
                    loadedPath = path
                    null
                }
                else -> "Cannot load Z3 from $path: ${kz3_failure?.toKString() ?: "unknown loader failure"}"
            }
        } finally {
            lock.store(0)
        }
    }

    /** Full version of the loaded library; call only after a successful [load]. */
    fun version(): String = Z3_get_full_version()?.toKString() ?: "unknown"
}
