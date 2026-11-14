// Kotlin Multiplatform, kt-mpp tooling and publishing are configured at the root.
kotlin {
    sourceSets {
        commonMain {
            dependencies { api(libs.kt.math) }
        }
    }
}
