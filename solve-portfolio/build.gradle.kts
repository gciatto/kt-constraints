kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":solve-spi"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}
