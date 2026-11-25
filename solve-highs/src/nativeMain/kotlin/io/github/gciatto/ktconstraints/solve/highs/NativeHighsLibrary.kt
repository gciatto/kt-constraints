@file:OptIn(ExperimentalForeignApi::class, ExperimentalAtomicApi::class, ExperimentalNativeApi::class)

package io.github.gciatto.ktconstraints.solve.highs

import io.github.gciatto.ktconstraints.solve.highs.capi.Highs_version
import io.github.gciatto.ktconstraints.solve.highs.capi.khs_failure
import io.github.gciatto.ktconstraints.solve.highs.capi.khs_load
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.experimental.ExperimentalNativeApi

/** Process-wide run-time loading of one HiGHS C library, whose symbols back the `capi` shim. */
internal object NativeHighsLibrary {
    /** Environment variable naming the HiGHS library file to load. */
    const val PATH_VARIABLE = "KT_CONSTRAINTS_HIGHS_LIBRARY"

    private val lock = AtomicInt(0)
    private var loadedPath: String? = null

    /** The configured library: [PATH_VARIABLE] when set, else the platform's default name on the loader path. */
    val defaultPath: String
        get() =
            getenv(PATH_VARIABLE)?.toKString()?.takeIf { it.isNotEmpty() }
                ?: when (Platform.osFamily) {
                    OsFamily.WINDOWS -> "libhighs.dll"
                    OsFamily.LINUX, OsFamily.ANDROID -> "libhighs.so"
                    else -> "libhighs.dylib"
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
                loaded != null -> "HiGHS is already loaded from $loaded; one library per process"
                khs_load(path) -> {
                    loadedPath = path
                    null
                }
                else -> "Cannot load HiGHS from $path: ${khs_failure?.toKString() ?: "unknown loader failure"}"
            }
        } finally {
            lock.store(0)
        }
    }

    /** Version of the loaded library; call only after a successful [load]. */
    fun version(): String = Highs_version()?.toKString() ?: "unknown"
}
