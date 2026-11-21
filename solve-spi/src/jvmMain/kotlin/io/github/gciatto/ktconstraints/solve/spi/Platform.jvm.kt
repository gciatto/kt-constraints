package io.github.gciatto.ktconstraints.solve.spi

internal actual fun detectPlatform(): Platform =
    Platform(
        Runtime.JVM,
        System
            .getProperty("os.name")
            .orEmpty()
            .lowercase()
            .substringBefore(' ')
            .ifEmpty { "unknown" },
    )
