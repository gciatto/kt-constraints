kotlin {
    // `processMain` holds the MiniZinc driver runs that the JVM and Native share: JavaScript has its own runtime.
    applyDefaultHierarchyTemplate {
        common {
            group("process") {
                withJvm()
                group("native")
            }
        }
    }
    // src/nativeInterop/cinterop/process.def starts and stops the driver with posix_spawn on Linux and macOS.
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        compilations.getByName("main").cinterops.create("process")
    }
    sourceSets {
        commonMain {
            dependencies {
                api(project(":solve-spi"))
                api(project(":model"))
                implementation(libs.kotlinx.serialization.json)
            }
        }
        commonTest {
            dependencies { implementation(project(":test-solve")) }
        }
        jsMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(npm("minizinc", libs.versions.minizincJs.get()))
            }
        }
        val processMain by getting {
            dependencies { implementation(libs.kotlinx.coroutines.core) }
        }
    }
}

tasks.register<JavaExec>("jvmBenchmark") {
    group = "benchmark"
    description = "Measures model, compiler, startup, runtime, decoding and projection phases using the selected real MiniZinc engine."
    val compilation = kotlin.jvm().compilations.getByName("test")
    dependsOn("jvmTestClasses")
    classpath(compilation.output.allOutputs, compilation.runtimeDependencyFiles)
    mainClass.set("io.github.gciatto.ktconstraints.solve.minizinc.MiniZincBenchmark")
}

val miniZincExecutable = providers.environmentVariable("MINIZINC_EXECUTABLE")
tasks.named<org.gradle.api.tasks.testing.Test>("jvmTest") {
    testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    inputs.property("miniZincExecutable", miniZincExecutable.orElse(""))
    inputs.property("miniZincEngines", providers.environmentVariable("MINIZINC_ENGINES").orElse(""))
    outputs.upToDateWhen { !miniZincExecutable.isPresent }
    outputs.doNotCacheIf("The explicitly requested external solver runtime must be probed again") { miniZincExecutable.isPresent }
}

kotlin.js {
    browser {
        testTask {
            useKarma { useChromeHeadless() }
            onlyIf("Browser runtime verification is explicitly enabled") {
                providers.environmentVariable("MINIZINC_BROWSER_TEST").isPresent
            }
        }
    }
}

val miniZincBrowserTest = providers.environmentVariable("MINIZINC_BROWSER_TEST")
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest>().configureEach {
    inputs.property("miniZincExecutable", miniZincExecutable.orElse(""))
    inputs.property("miniZincEngines", providers.environmentVariable("MINIZINC_ENGINES").orElse(""))
    outputs.upToDateWhen { !miniZincExecutable.isPresent }
}

tasks.matching { it.name == "jsNodeTest" || it.name == "jsBrowserTest" }.configureEach {
    inputs.property("miniZincExecutable", miniZincExecutable.orElse(""))
    inputs.property("miniZincBrowserTest", miniZincBrowserTest.orElse(""))
    outputs.upToDateWhen { !miniZincExecutable.isPresent && !miniZincBrowserTest.isPresent }
    outputs.doNotCacheIf("Explicit JS runtime verification must execute against its actual runtime") {
        miniZincExecutable.isPresent || miniZincBrowserTest.isPresent
    }
}

// Kotest setup failures can omit a JS spec without failing Karma; require the real contract report.
tasks.matching { it.name == "jsNodeTest" || it.name == "jsBrowserTest" }.configureEach {
    doLast {
        if (name == "jsBrowserTest" || miniZincExecutable.isPresent) {
            val report =
                layout.buildDirectory
                    .file(
                        "test-results/$name/TEST-$name.io.github.gciatto.ktconstraints.solve.minizinc.MiniZincJsSpec.xml",
                    ).get()
                    .asFile
            check(report.isFile && report.readText().contains("js/route-identifies-the-actual-transport")) {
                "The requested MiniZinc JS runtime contract did not execute; inspect runtime setup failures"
            }
        }
    }
}
