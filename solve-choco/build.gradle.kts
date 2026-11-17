kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":solve-spi"))
                api(project(":model"))
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.choco.solver)
            }
        }
        jvmTest {
            dependencies { implementation(project(":test-solve")) }
        }
    }
}

tasks.named<org.gradle.api.tasks.testing.Test>("jvmTest") {
    testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
}
