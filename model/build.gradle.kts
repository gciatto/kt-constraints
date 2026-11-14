kotlin {
    sourceSets {
        commonMain {
            dependencies { api(project(":core")) }
        }
        commonTest {
            dependencies { implementation(project(":test-core")) }
        }
    }
}
