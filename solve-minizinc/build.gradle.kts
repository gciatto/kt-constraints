kotlin {
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
        jvmMain {
            dependencies { implementation(libs.kotlinx.coroutines.core) }
        }
    }
}

tasks.register<JavaExec>("jvmBenchmark") {
    group = "benchmark"
    description = "Measures model, compiler, startup, runtime, decoding and projection phases using real Gecode."
    val compilation = kotlin.jvm().compilations.getByName("test")
    dependsOn("jvmTestClasses")
    classpath(compilation.output.allOutputs, compilation.runtimeDependencyFiles)
    mainClass.set("io.github.gciatto.ktconstraints.solve.minizinc.MiniZincBenchmark")
}

val miniZincExecutable = providers.environmentVariable("MINIZINC_EXECUTABLE")
tasks.named<org.gradle.api.tasks.testing.Test>("jvmTest") {
    inputs.property("miniZincExecutable", miniZincExecutable.orElse(""))
    inputs.property("miniZincEngines", providers.environmentVariable("MINIZINC_ENGINES").orElse(""))
    outputs.upToDateWhen { !miniZincExecutable.isPresent }
    outputs.doNotCacheIf("The explicitly requested external solver runtime must be probed again") { miniZincExecutable.isPresent }
}
