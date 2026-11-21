kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":solve-spi"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        jvmMain {
            dependencies {
                implementation(project(":solve-z3"))
                implementation(project(":solve-choco"))
                implementation(project(":solve-cpsat"))
                implementation(project(":solve-mip"))
                implementation(project(":solve-minizinc"))
            }
        }
        jsMain {
            dependencies {
                implementation(project(":solve-z3"))
                implementation(project(":solve-minizinc"))
            }
        }
        commonTest {
            dependencies { implementation(project(":test-solve")) }
        }
        nativeMain {
            dependencies { implementation(project(":solve-z3")) }
        }
    }
}
