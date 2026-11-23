import io.github.gciatto.kt.mpp.Plugins
import io.github.gciatto.kt.mpp.helpers.ProjectType
import io.github.gciatto.kt.mpp.utils.log
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugins.signing.Sign
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// Keep aligned with kt-math and 2p-kt until publish-on-central fixes its Gradle-coroutines clash.
buildscript {
    configurations.classpath {
        resolutionStrategy {
            force("org.danilopianini:publish-on-central:9.2.4")
        }
    }
}

plugins {
    alias(libs.plugins.gitSemVer)
    alias(libs.plugins.ktMpp.multiProjectHelper)
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotest) apply false
    alias(libs.plugins.kover)
}

group = "io.github.gciatto"

gitSemVer {
    buildMetadataSeparator.set("-")
}

log("version: $version", LogLevel.LIFECYCLE)

multiProjectHelper {
    defaultProjectType = ProjectType.KOTLIN
    jvmProjects(":interop-java", ":solve-choco", ":solve-cpsat")
    jsProjects(":interop-js")
    otherProjects(":documentation")

    val shared = setOf(Plugins.documentation, Plugins.linter, Plugins.bugFinder, Plugins.versions)
    ktProjectTemplate = shared + Plugins.multiplatform
    jvmProjectTemplate = shared + Plugins.jvmOnly
    jsProjectTemplate = shared + Plugins.jsOnly
    otherProjectTemplate = setOf(Plugins.versions)
    applyProjectTemplates()
}

// Build the common IR on the three desktop Native families and representative iOS targets.
// kt-mpp already declares JVM and JavaScript; here we add the portable Native targets.
subprojects {
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        // The JS facade has no Native counterpart; declaring empty Native targets would publish empty artifacts.
        if (path == ":interop-js") return@withId
        configure<KotlinMultiplatformExtension> {
            linuxX64()
            mingwX64()
            macosArm64()
            iosArm64()
            iosSimulatorArm64()
        }
    }
}

// Only real library modules are publishable at bootstrap. Add each backend to publishedModules
// only after its runtime works and passes shared conformance tests.
// Maven Central coordinates are io.github.gciatto:kt-constraints-<module> (+ platform variants).
val publishedModules =
    setOf(
        "core",
        "model",
        "dsl",
        "solve",
        "solve-spi",
        "solve-z3",
        "solve-choco",
        "solve-cpsat",
        "solve-mip",
        "solve-minizinc",
        "solve-portfolio",
        "interop-java",
        "interop-js",
    )
val nonPublished = setOf("documentation", "test-core", "test-solve", "interop-c")
subprojects {
    if (name in publishedModules) {
        plugins.withId("org.jetbrains.kotlin.multiplatform") {
            apply(
                plugin =
                    rootProject.libs.plugins.ktMpp.mavenPublish
                        .get()
                        .pluginId,
            )
        }
        plugins.withId("org.jetbrains.kotlin.jvm") {
            apply(
                plugin =
                    rootProject.libs.plugins.ktMpp.mavenPublish
                        .get()
                        .pluginId,
            )
        }
        plugins.withId("org.jetbrains.kotlin.js") {
            apply(
                plugin =
                    rootProject.libs.plugins.ktMpp.mavenPublish
                        .get()
                        .pluginId,
            )
        }
        plugins.withId("maven-publish") {
            val prefixArtifactIds = {
                extensions.configure<org.gradle.api.publish.PublishingExtension> {
                    publications.withType<MavenPublication>().configureEach {
                        val platformSuffix =
                            when (name.lowercase()) {
                                "kotlinmultiplatform", "maven", "mavenjava", "ossrh" -> ""
                                else -> "-${name.lowercase()}"
                            }
                        artifactId = "kt-constraints-${project.name}$platformSuffix"
                    }
                }
            }
            prefixArtifactIds()
            // Kotlin assigns Apple-target coordinates late on non-Apple hosts, overriding the prefix; reapply it last.
            gradle.projectsEvaluated { prefixArtifactIds() }
        }
    }
}

// Source builds may publish locally without a key; Maven Central still rejects unsigned uploads.
subprojects {
    tasks.withType<Sign>().configureEach {
        onlyIf("a signing key is configured") { !project.findProperty("signingKey")?.toString().isNullOrBlank() }
    }
}

// Snapshot signing dependencies before graph traversal to avoid a lazy publication self-cycle.
gradle.projectsEvaluated {
    subprojects.forEach { project ->
        project.tasks.withType<Sign>().toList().forEach { task ->
            task.setDependsOn(task.taskDependencies.getDependencies(task).filter { it != task })
        }
    }
}

// Kotest KSP discovery on JS, JVM JUnit Platform discovery, shared MPP assertions.
subprojects {
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        if (file("src").listFiles().orEmpty().none { it.isDirectory && it.name.endsWith("Test") }) return@withId
        apply(
            plugin =
                rootProject.libs.plugins.ksp
                    .get()
                    .pluginId,
        )
        apply(
            plugin =
                rootProject.libs.plugins.kotest
                    .get()
                    .pluginId,
        )
        configure<KotlinMultiplatformExtension> {
            sourceSets.named("commonTest") {
                dependencies {
                    implementation(rootProject.libs.kotest.framework.engine)
                    implementation(rootProject.libs.kotest.assertions.core)
                    implementation(rootProject.libs.kotest.property)
                }
            }
            sourceSets.matching { it.name == "jvmTest" }.configureEach {
                dependencies { implementation(rootProject.libs.kotest.runner.junit5) }
            }
        }
    }
}

// All Kotlin tests use Kotest, not kotlin.test / JUnit or nested runTest.
val bannedImports = Regex("""^import (kotlin\.test\.|org\.junit\.|kotlinx\.coroutines\.test\.runTest\b).*""", RegexOption.MULTILINE)
subprojects {
    val verifyTestImports =
        tasks.register("verifyTestImports") {
            group = "verification"
            val sources = fileTree("src") { include("**/*.kt") }
            doLast {
                val errors =
                    sources.flatMap { source ->
                        bannedImports.findAll(source.readText()).map { "${source.relativeTo(rootDir)}: ${it.value}" }
                    }
                if (errors.isNotEmpty()) throw GradleException(errors.joinToString("\n", "Use Kotest instead:\n"))
            }
        }
    tasks.matching { it.name == "check" }.configureEach { dependsOn(verifyTestImports) }
}

// Kover is JVM-centric; aggregate production modules, exclude test utilities and documentation.
subprojects {
    if (name !in nonPublished) {
        apply(plugin = "org.jetbrains.kotlinx.kover")
        rootProject.dependencies.add("kover", this)
    }
}

allprojects {
    repositories {
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}
