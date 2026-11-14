kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":solve"))
                api(project(":solve-spi"))
                api(libs.kotest.framework.engine)
                api(libs.kotest.assertions.core)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}
