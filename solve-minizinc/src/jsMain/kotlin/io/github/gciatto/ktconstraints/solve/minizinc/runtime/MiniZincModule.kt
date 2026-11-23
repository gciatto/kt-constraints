// Named exports: the official package's ES modules have no default export, unlike its CommonJS builds.
@file:JsModule("minizinc")
@file:JsNonModule

package io.github.gciatto.ktconstraints.solve.minizinc.runtime

@JsName("Model")
internal external val miniZincModel: dynamic

@JsName("init")
internal external fun miniZincInit(config: dynamic): dynamic

@JsName("version")
internal external fun miniZincVersion(): dynamic

@JsName("solvers")
internal external fun miniZincSolvers(): dynamic

@JsName("shutdown")
internal external fun miniZincShutdown()
