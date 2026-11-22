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
            dependencies { implementation(project(":test-solve")) }
        }
        jvmTest {
            dependencies { implementation(project(":solve-kotlin")) }
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
