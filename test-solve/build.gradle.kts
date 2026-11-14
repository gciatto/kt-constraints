kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":solve"))
                api(project(":solve-spi"))
            }
        }
    }
}
