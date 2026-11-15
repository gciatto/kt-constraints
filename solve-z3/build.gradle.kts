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
        jsMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(npm("z3-solver", libs.versions.z3Js.get()))
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.z3.turnkey)
            }
        }
    }
}

tasks.named<org.gradle.api.tasks.testing.Test>("jvmTest") {
    testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
}

// The official z3-solver needs SharedArrayBuffer: browsers require cross-origin isolation, not yet configured.
kotlin.js {
    browser {
        testTask {
            onlyIf("Cross-origin-isolated browser verification is explicitly enabled") {
                providers.environmentVariable("Z3_BROWSER_TEST").isPresent
            }
        }
    }
}

// Kotest setup failures can omit a JS spec without failing the task; require the real Node contract report.
tasks.matching { it.name == "jsNodeTest" }.configureEach {
    doLast {
        val report =
            layout.buildDirectory
                .file("test-results/$name/TEST-$name.io.github.gciatto.ktconstraints.solve.z3.Z3JsSpec.xml")
                .get()
                .asFile
        check(report.isFile && report.readText().contains("z3-js/route-identifies-the-wasm-binding")) {
            "The z3-solver Node contract did not execute; inspect runtime setup failures"
        }
    }
}
