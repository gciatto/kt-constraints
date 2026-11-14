// JS-only exported facade; @JsExport surface and TypeScript contracts are future milestones.
kotlin {
    sourceSets {
        jsMain {
            dependencies {
                api(project(":model"))
                api(project(":solve"))
            }
        }
    }
}
