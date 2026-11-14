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
