kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":core"))
                api(project(":model"))
                api(project(":dsl"))
            }
        }
    }
}
