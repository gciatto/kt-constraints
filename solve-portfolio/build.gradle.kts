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

// Kotlin/JS test runners do not inherit arbitrary variables; forward the opt-in route benchmark settings.
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest>().configureEach {
    listOf("", "_SCALES", "_SEEDS", "_TIMEOUT")
        .map { "KT_CONSTRAINTS_ROUTE_BENCHMARK$it" }
        .forEach { name -> providers.environmentVariable(name).orNull?.let { environment(name, it) } }
}

// Release test binaries measure what users get: `./gradlew :solve-portfolio:linkReleaseTestLinuxX64`, then run
// build/bin/linuxX64/releaseTest/test.kexe with KT_CONSTRAINTS_ROUTE_BENCHMARK=1 and KT_CONSTRAINTS_Z3_LIBRARY set.
kotlin.targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
    binaries.test(listOf(org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType.RELEASE))
}
