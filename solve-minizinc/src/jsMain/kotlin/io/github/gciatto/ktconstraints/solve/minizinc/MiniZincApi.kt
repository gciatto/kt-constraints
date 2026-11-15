package io.github.gciatto.ktconstraints.solve.minizinc

@JsModule("minizinc")
@JsNonModule
internal external val miniZincApi: dynamic

internal fun newMiniZincModel(): dynamic = js("Reflect.construct")(miniZincApi.Model, emptyArray<Any>())
