kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":solve-spi"))
                api(project(":model"))
                implementation(project(":solve-linear"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        commonTest {
            dependencies { implementation(project(":test-solve")) }
        }
        jsMain {
            dependencies { implementation(npm("highs", libs.versions.highsJs.get())) }
        }
    }
    // src/nativeInterop/cinterop/highs.def loads the HiGHS C API at run time, so no target links against it.
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        compilations.getByName("main").cinterops.create("highs")
    }
}

// Native tests run the real engine only when KT_CONSTRAINTS_HIGHS_LIBRARY names a HiGHS C library.
val highsLibrary = providers.environmentVariable("KT_CONSTRAINTS_HIGHS_LIBRARY")
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest>().configureEach {
    inputs.property("highsLibrary", highsLibrary.orElse(""))
    outputs.upToDateWhen { !highsLibrary.isPresent }
}
