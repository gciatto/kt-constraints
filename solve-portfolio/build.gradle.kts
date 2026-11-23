kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":solve-spi"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        jvmMain {
            dependencies {
                implementation(project(":solve-z3"))
                implementation(project(":solve-choco"))
                implementation(project(":solve-cpsat"))
                implementation(project(":solve-mip"))
                implementation(project(":solve-minizinc"))
            }
        }
        jsMain {
            dependencies {
                implementation(project(":solve-z3"))
                implementation(project(":solve-minizinc"))
            }
        }
        commonTest {
            dependencies {
                implementation(project(":test-solve"))
                implementation(project(":solve-kotlin"))
                // The documentation's Kotlin examples (tutorials and how-tos) live in this module's tests.
                implementation(project(":dsl"))
            }
        }
        nativeMain {
            dependencies { implementation(project(":solve-z3")) }
        }
    }
}

tasks.register<JavaExec>("jvmBenchmark") {
    group = "benchmark"
    description = "Runs every benchmark family on every eligible bundled JVM route and prints CSV."
    val compilation = kotlin.jvm().compilations.getByName("test")
    dependsOn("jvmTestClasses")
    classpath(compilation.output.allOutputs, compilation.runtimeDependencyFiles)
    mainClass.set("io.github.gciatto.ktconstraints.solve.portfolio.PortfolioBenchmark")
}

// Kotlin/JS test runners do not inherit arbitrary variables; forward the opt-in route benchmark settings and the
// MiniZinc executable that registers the Node CLI route.
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest>().configureEach {
    (listOf("", "_SCALES", "_SEEDS", "_TIMEOUT").map { "KT_CONSTRAINTS_ROUTE_BENCHMARK$it" } + "MINIZINC_EXECUTABLE")
        .forEach { name -> providers.environmentVariable(name).orNull?.let { environment(name, it) } }
}

// Release test binaries measure what users get: `./gradlew :solve-portfolio:linkReleaseTestLinuxX64`, then run
// build/bin/linuxX64/releaseTest/test.kexe with KT_CONSTRAINTS_ROUTE_BENCHMARK=1 and KT_CONSTRAINTS_Z3_LIBRARY set.
kotlin.targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
    binaries.test(listOf(org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType.RELEASE))
}

// Native tests (including the documentation's examples) solve through Z3's C API: load the pinned build that
// :solve-z3 extracts for this host.
mapOf("linuxX64" to "libz3.so", "macosArm64" to "libz3.dylib", "mingwX64" to "libz3.dll").forEach { (target, library) ->
    tasks
        .withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest>()
        .matching { it.name == "${target}Test" }
        .configureEach {
            dependsOn(":solve-z3:extractZ3Native")
            environment(
                "KT_CONSTRAINTS_Z3_LIBRARY",
                project(":solve-z3")
                    .layout.buildDirectory
                    .file("z3-native/$target/$library")
                    .get()
                    .asFile.absolutePath,
            )
        }
}
