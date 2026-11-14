kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":core"))
                api(project(":model"))
                api(project(":dsl"))
                api(libs.kotest.framework.engine)
                api(libs.kotest.assertions.core)
                api(libs.kotest.property)
            }
        }
    }
}
