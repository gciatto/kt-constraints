import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.konan.target.HostManager

// C facade: a shared library and header per desktop Native target, attached to GitHub releases (see release.config.js).
// It is not a Maven publication: Kotlin/Native users depend on the klibs of the other modules instead.
kotlin {
    sourceSets {
        nativeMain {
            dependencies {
                implementation(project(":dsl"))
                implementation(project(":solve-portfolio"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}

val libraryName = "ktconstraints"
val usageLibraryTargets = setOf("linuxX64", "macosArm64")
val sharedLibraryTargets = usageLibraryTargets + "mingwX64"

val releaseAssets =
    tasks.register("releaseAssets") {
        group = "distribution"
        description = "Builds the C shared library zips attached to the GitHub release"
    }

kotlin.targets
    .withType<KotlinNativeTarget>()
    .matching { it.name in sharedLibraryTargets }
    .configureEach {
        val buildTypes =
            listOf(NativeBuildType.RELEASE) + listOf(NativeBuildType.DEBUG).filter { name in usageLibraryTargets }
        binaries.sharedLib(libraryName, buildTypes)
        val library = binaries.getSharedLib(libraryName, NativeBuildType.RELEASE)
        val zip =
            tasks.register<Zip>("zipSharedLibrary${targetName.replaceFirstChar { it.uppercase() }}") {
                group = "distribution"
                description = "Zips the C shared library and header of $targetName"
                // destinationDirectory carries no task dependency: without dependsOn, nothing gets linked (NO-SOURCE)
                dependsOn(library.linkTaskProvider)
                from(library.linkTaskProvider.flatMap { it.destinationDirectory })
                archiveBaseName.set("kt-constraints-c")
                archiveClassifier.set(targetName)
            }
        releaseAssets { dependsOn(zip) }
    }

// The C API as C code sees it: src/nativeUsageTest/c, compiled with the host's `cc` against the library of the host's
// target, then run against the same pinned Z3 build that the native Z3 tests load.
kotlin.targets
    .withType<KotlinNativeTarget>()
    .firstOrNull { it.name in usageLibraryTargets && it.konanTarget == HostManager.host }
    ?.let { target ->
        val library = target.binaries.getSharedLib(libraryName, NativeBuildType.DEBUG)
        val source = layout.projectDirectory.file("src/nativeUsageTest/c/usage.c")
        val executable = layout.buildDirectory.file("nativeUsageTest/usage")
        val compile =
            tasks.register<Exec>("compileNativeUsageTest") {
                dependsOn(library.linkTaskProvider)
                inputs.file(source)
                outputs.file(executable)
                doFirst {
                    val dir = library.outputDirectory.absolutePath
                    executable
                        .get()
                        .asFile.parentFile
                        .mkdirs()
                    commandLine(
                        listOf("cc", "-Wall", "-Werror", "-I", dir, "-o", executable.get().asFile.absolutePath) +
                            listOf(source.asFile.absolutePath, "-L", dir, "-l$libraryName", "-Wl,-rpath,$dir"),
                    )
                }
            }
        val z3Library = if (target.name == "linuxX64") "libz3.so" else "libz3.dylib"
        val run =
            tasks.register<Exec>("nativeUsageTest") {
                group = "verification"
                description = "Runs the C usage test against the shared library of the host's native target"
                dependsOn(compile, ":solve-z3:extractZ3Native")
                environment(
                    "KT_CONSTRAINTS_Z3_LIBRARY",
                    project(":solve-z3")
                        .layout.buildDirectory
                        .file("z3-native/${target.name}/$z3Library")
                        .get()
                        .asFile.absolutePath,
                )
                commandLine(executable.get().asFile.absolutePath)
            }
        tasks.named("check") { dependsOn(run) }
    }
