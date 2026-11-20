import java.time.Duration

// JS-only exported facade: an ES module with TypeScript declarations, Promise solving and async iteration.
kotlin {
    sourceSets {
        jsMain {
            dependencies {
                api(project(":model"))
                api(project(":solve"))
                api(project(":solve-spi"))
                implementation(project(":dsl"))
                implementation(project(":solve-z3"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}

kotlin.js {
    binaries.library()
    useEsModules()
    generateTypeScriptDefinitions()
}

// Type-checks and runs src/test/typescript against the packaged library, as an npm consumer would.
val typescriptUsage = layout.buildDirectory.dir("typescript-usage")
val prepareTypescriptUsage =
    tasks.register<Sync>("prepareTypescriptUsage") {
        from("src/test/typescript")
        into(typescriptUsage)
        preserve { include("node_modules/**") }
    }
val npmExecutable = if (System.getProperty("os.name").lowercase().startsWith("windows")) "npm.cmd" else "npm"
val installTypescriptUsage =
    tasks.register<Exec>("installTypescriptUsage") {
        dependsOn(prepareTypescriptUsage, "jsNodeProductionLibraryDistribution")
        workingDir(typescriptUsage)
        // npm skips reinstalling an unchanged version, so drop the previous copy of the library first.
        doFirst { delete(typescriptUsage.map { it.dir("node_modules/kt-constraints-interop-js") }) }
        commandLine(npmExecutable, "install", "--install-links", "--no-package-lock", "--no-audit", "--no-fund")
    }
val typecheckTypescriptUsage =
    tasks.register<Exec>("typecheckTypescriptUsage") {
        dependsOn(installTypescriptUsage)
        workingDir(typescriptUsage)
        commandLine(npmExecutable, "exec", "--", "tsc", "-p", ".")
    }
val typescriptUsageTest =
    tasks.register<Exec>("typescriptUsageTest") {
        group = "verification"
        description = "Runs the type-checked TypeScript usage against the packaged ES module on Node."
        dependsOn(typecheckTypescriptUsage)
        workingDir(typescriptUsage)
        timeout.set(Duration.ofMinutes(10))
        commandLine("node", "out/usage.mjs")
    }
tasks.matching { it.name == "jsTest" }.configureEach { dependsOn(typescriptUsageTest) }
