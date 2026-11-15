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

// The official z3-solver needs SharedArrayBuffer; karma.config.d/z3.js serves it with COOP/COEP isolation.
kotlin.js {
    browser {
        testTask {
            useKarma { useChromeHeadless() }
            onlyIf("Browser runtime verification is explicitly enabled") {
                providers.environmentVariable("Z3_BROWSER_TEST").isPresent
            }
        }
    }
}

// Kotest setup failures can omit a JS spec without failing the task; require the real contract report.
tasks.matching { it.name == "jsNodeTest" || it.name == "jsBrowserTest" }.configureEach {
    doLast {
        val report =
            layout.buildDirectory
                .file("test-results/$name/TEST-$name.io.github.gciatto.ktconstraints.solve.z3.Z3JsSpec.xml")
                .get()
                .asFile
        check(report.isFile && report.readText().contains("z3-js/route-identifies-the-wasm-binding")) {
            "The z3-solver contract did not execute in $name; inspect runtime setup failures"
        }
    }
}
