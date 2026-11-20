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
        commandLine("node", "out/node.mjs")
    }

// Bundles the browser entry and serves it, with the Z3 and MiniZinc Wasm assets, to Chromium (CHROME_BIN).
val browserSite = typescriptUsage.map { it.dir("site") }
val bundleTypescriptBrowserUsage =
    tasks.register<Exec>("bundleTypescriptBrowserUsage") {
        dependsOn(typecheckTypescriptUsage)
        workingDir(typescriptUsage)
        commandLine(
            npmExecutable, "exec", "--", "esbuild", "out/browser.mjs", "--bundle", "--format=esm", "--platform=browser",
            "--target=es2022", "--outfile=site/browser.js",
        )
    }
val prepareTypescriptBrowserUsage =
    tasks.register<Copy>("prepareTypescriptBrowserUsage") {
        dependsOn(bundleTypescriptBrowserUsage)
        from(typescriptUsage.map { it.file("index.html") })
        from(typescriptUsage.map { it.dir("node_modules/z3-solver/build") }) { include("z3-built.*") }
        from(typescriptUsage.map { it.dir("node_modules/minizinc/dist") }) {
            include("minizinc-worker.js", "minizinc.wasm", "minizinc.data")
        }
        into(browserSite)
    }
tasks.register<Exec>("typescriptBrowserUsageTest") {
    group = "verification"
    description = "Runs the type-checked TypeScript usage in Chromium, cross-origin isolated (INTEROP_BROWSER_TEST)."
    dependsOn(prepareTypescriptBrowserUsage)
    onlyIf("Browser runtime verification is explicitly enabled") {
        providers.environmentVariable("INTEROP_BROWSER_TEST").isPresent
    }
    timeout.set(Duration.ofMinutes(10))
    workingDir(typescriptUsage)
    commandLine("node", "browser-run.mjs", "site")
}
tasks.matching { it.name == "jsTest" }.configureEach { dependsOn(typescriptUsageTest) }
