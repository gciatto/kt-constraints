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
    }
}
