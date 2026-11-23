package io.github.gciatto.ktconstraints.solve.minizinc.runtime

/** The official `minizinc` package's runtime API, bound to its named exports. */
internal object MiniZincApi {
    fun init(config: dynamic): dynamic = miniZincInit(config)

    fun version(): dynamic = miniZincVersion()

    fun solvers(): dynamic = miniZincSolvers()

    fun shutdown() = miniZincShutdown()
}

internal fun newMiniZincModel(): dynamic = js("Reflect.construct")(miniZincModel, emptyArray<Any>())
