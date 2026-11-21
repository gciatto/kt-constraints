package io.github.gciatto.ktconstraints.solve.spi

private val nodePlatform: String? =
    js("(typeof process !== 'undefined' && process.versions != null && process.versions.node != null) ? process.platform : null")
        .unsafeCast<String?>()

internal actual fun detectPlatform(): Platform =
    nodePlatform?.let { Platform(Runtime.NODE, it.lowercase()) } ?: Platform(Runtime.BROWSER, "browser")
