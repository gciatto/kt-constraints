package io.github.gciatto.ktconstraints.interop

import io.kotest.core.spec.style.FunSpec

/** Calls the javac-compiled consumer with the portable backend. */
class KotlinJavaUsageSpec :
    FunSpec({
        test("java/kotlin/direct/blocking-satisfaction") { JavaUsage.satisfy(KotlinJavaUsage.backend()) }
        test("java/kotlin/direct/configuration") { JavaUsage.satisfy(KotlinJavaUsage.configured()) }
        test("java/kotlin/direct/async-optimization") { JavaUsage.optimizeAsync(KotlinJavaUsage.backend()) }
        test("java/kotlin/direct/enumeration") { JavaUsage.enumerate(KotlinJavaUsage.backend()) }
        test("java/kotlin/direct/exact-integers") { JavaUsage.exactIntegers(KotlinJavaUsage.backend()) }
        test("java/kotlin/direct/exact-reals") { JavaUsage.reals(KotlinJavaUsage.backend()) }
        test("java/kotlin/direct/incremental-session") { JavaUsage.sessions(KotlinJavaUsage.backend()) }
    })
