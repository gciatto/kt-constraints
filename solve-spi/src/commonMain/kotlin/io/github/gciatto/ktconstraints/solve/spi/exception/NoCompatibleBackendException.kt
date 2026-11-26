package io.github.gciatto.ktconstraints.solve.spi.exception

/** No registered backend satisfies all eligibility checks for a requested solve. */
class NoCompatibleBackendException(
    rejections: Map<String, List<String>>,
    diagnosis: List<String> = emptyList(),
) : IllegalStateException(
        "No compatible solver backend: " + (diagnosis + rejections.map { (id, reasons) -> "$id: $reasons" }).joinToString("; "),
    ) {
    /** What makes the model unsolvable by any registered backend, such as a capability none of them declares. */
    val diagnosis: List<String> = buildList { addAll(diagnosis) }

    /** Detached route-scoped explanations, in registration order. */
    val rejections: Map<String, List<String>> =
        buildMap {
            rejections.forEach { (id, reasons) -> put(id, buildList { addAll(reasons) }) }
        }
}
