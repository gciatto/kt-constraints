package io.github.gciatto.ktconstraints.solve.spi

/** No registered backend satisfies all eligibility checks for a requested solve. */
class NoCompatibleBackendException(
    rejections: Map<String, List<String>>,
) : IllegalStateException("No compatible solver backend: $rejections") {
    /** Detached route-scoped explanations, in registration order. */
    val rejections: Map<String, List<String>> = buildMap {
        rejections.forEach { (id, reasons) -> put(id, buildList { addAll(reasons) }) }
    }
}
