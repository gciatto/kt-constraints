// Blocking (and, on the JVM, CompletableFuture) facade over any SolverBackend, for JVM and Native callers.
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":solve-spi"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        commonTest {
            dependencies {
                implementation(project(":test-solve"))
                implementation(project(":solve-kotlin"))
            }
        }
    }
}
