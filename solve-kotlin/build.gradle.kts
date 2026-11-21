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
