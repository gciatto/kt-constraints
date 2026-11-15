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
        jvmMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.z3.turnkey)
            }
        }
    }
}

tasks.named<org.gradle.api.tasks.testing.Test>("jvmTest") {
    testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
}
