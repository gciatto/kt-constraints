package io.github.gciatto.ktconstraints.solve.spi

import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
internal actual fun detectPlatform(): Platform =
    Platform(
        Runtime.NATIVE,
        kotlin.native.Platform.osFamily.name
            .lowercase(),
    )
