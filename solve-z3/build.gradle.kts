kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":solve-spi"))
                api(project(":model"))
            }
        }
        commonTest {
            dependencies { implementation(project(":test-solve")) }
        }
        jsMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(npm("z3-solver", libs.versions.z3Js.get()))
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.z3.turnkey)
            }
        }
        nativeMain {
            dependencies { implementation(libs.kotlinx.coroutines.core) }
        }
    }
    // The shim in src/nativeInterop/cinterop/z3.def loads Z3 at run time, so no target links against it.
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        compilations.getByName("main").cinterops.create("z3")
    }
}

tasks.named<org.gradle.api.tasks.testing.Test>("jvmTest") {
    testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
}

// The official z3-solver needs SharedArrayBuffer; karma.config.d/z3.js serves it with COOP/COEP isolation.
kotlin.js {
    browser {
        testTask {
            useKarma { useChromeHeadless() }
            onlyIf("Browser runtime verification is explicitly enabled") {
                providers.environmentVariable("Z3_BROWSER_TEST").isPresent
            }
        }
    }
}

// Kotest setup failures can omit a JS spec without failing the task; require the real contract report.
tasks.matching { it.name == "jsNodeTest" || it.name == "jsBrowserTest" }.configureEach {
    doLast {
        val report =
            layout.buildDirectory
                .file("test-results/$name/TEST-$name.io.github.gciatto.ktconstraints.solve.z3.Z3JsSpec.xml")
                .get()
                .asFile
        check(report.isFile && report.readText().contains("z3-js/route-identifies-the-wasm-binding")) {
            "The z3-solver contract did not execute in $name; inspect runtime setup failures"
        }
    }
}

// Native tests load the pinned z3-turnkey C library of the matching host, the same Z3 build as the JVM route.
val z3NativeRuntime: Configuration by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}
dependencies { z3NativeRuntime(libs.z3.turnkey) }

val hostOs = System.getProperty("os.name").lowercase()
val hostArm = System.getProperty("os.arch").let { it == "aarch64" || it == "arm64" }
val z3NativeHost: Triple<String, String, String>? =
    when {
        hostOs.startsWith("linux") && !hostArm -> Triple("linuxX64", "linux/amd64", "libz3.so")
        hostOs.startsWith("mac") && hostArm -> Triple("macosArm64", "osx/aarch64", "libz3.dylib")
        hostOs.startsWith("windows") && !hostArm -> Triple("mingwX64", "windows/amd64", "libz3.dll")
        else -> null
    }

z3NativeHost?.let { (target, directory, library) ->
    val extracted = layout.buildDirectory.dir("z3-native/$target")
    val extractZ3Native =
        tasks.register<Sync>("extractZ3Native") {
            description = "Extracts the pinned Z3 C library for native tests on this host."
            from({ zipTree(z3NativeRuntime.singleFile) }) {
                include("com/microsoft/z3/$directory/*")
                exclude("**/*java*", "**/*.xml")
                eachFile { path = name }
            }
            includeEmptyDirs = false
            into(extracted)
        }
    tasks
        .withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest>()
        .matching { it.name == "${target}Test" }
        .configureEach {
            dependsOn(extractZ3Native)
            environment(
                "KT_CONSTRAINTS_Z3_LIBRARY",
                extracted
                    .get()
                    .file(library)
                    .asFile.absolutePath,
            )
            // Require the provisioned contract to have run, not only the unavailability check.
            doLast {
                val report =
                    layout.buildDirectory
                        .file("test-results/$name/TEST-$name.io.github.gciatto.ktconstraints.solve.z3.Z3NativeSpec.xml")
                        .get()
                        .asFile
                check(report.isFile && report.readText().contains("z3-native/route-identifies-the-c-binding")) {
                    "The Z3 C API contract did not execute in $name; inspect library loading failures"
                }
            }
        }
}

tasks.register<JavaExec>("jvmStoreBenchmark") {
    group = "benchmark"
    description = "Times CLP-style store operations (N-queens labeling) against batch re-solving."
    val compilation = kotlin.jvm().compilations.getByName("test")
    dependsOn("jvmTestClasses")
    classpath(compilation.output.allOutputs, compilation.runtimeDependencyFiles)
    mainClass.set("io.github.gciatto.ktconstraints.solve.z3.Z3StoreBenchmark")
}
