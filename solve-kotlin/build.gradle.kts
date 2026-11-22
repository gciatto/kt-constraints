kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":solve-spi"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        commonTest {
            dependencies { implementation(project(":test-solve")) }
        }
    }
}

tasks.register<JavaExec>("jvmBenchmark") {
    group = "benchmark"
    description = "Reports raw model, initialization, solve, projection and incremental restoration wall times."
    val compilation = kotlin.jvm().compilations.getByName("test")
    dependsOn("jvmTestClasses")
    classpath(compilation.output.allOutputs, compilation.runtimeDependencyFiles)
    mainClass.set("io.github.gciatto.ktconstraints.solve.kotlin.KotlinBenchmark")
}

tasks.register<JavaExec>("jvmFamilyBenchmark") {
    group = "benchmark"
    description = "Reports median, minimum and maximum kotlin/direct solve times over seeded benchmark instances."
    val compilation = kotlin.jvm().compilations.getByName("test")
    dependsOn("jvmTestClasses")
    classpath(compilation.output.allOutputs, compilation.runtimeDependencyFiles)
    mainClass.set("io.github.gciatto.ktconstraints.solve.kotlin.KotlinFamilyBenchmark")
    maxHeapSize = "2g"
}

// Kotlin/JS test runners do not inherit arbitrary variables; forward the opt-in cross-platform benchmark settings.
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest>().configureEach {
    listOf("KT_CONSTRAINTS_PLATFORM_BENCHMARK", "KT_CONSTRAINTS_PLATFORM_BENCHMARK_SCALES", "KT_CONSTRAINTS_PLATFORM_BENCHMARK_SEEDS")
        .forEach { name -> providers.environmentVariable(name).orNull?.let { environment(name, it) } }
}

