kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":solve"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}
