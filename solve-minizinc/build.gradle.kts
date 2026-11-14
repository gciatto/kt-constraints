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
