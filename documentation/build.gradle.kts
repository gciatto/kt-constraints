// MkDocs Material site with the aggregated Dokka API reference copied under api/, as in 2p-kt.
plugins {
    id("org.jetbrains.dokka")
}

val documentedModules =
    listOf(
        "core", "model", "dsl", "solve", "solve-spi", "solve-kotlin", "solve-z3", "solve-choco", "solve-cpsat",
        "solve-mip", "solve-minizinc", "solve-auto", "solve-blocking", "interop-java",
        "interop-js",
    )

dependencies {
    documentedModules.forEach { dokka(project(":$it")) }
}

dokka {
    moduleName.set("Kt-Constraints")
}

val siteDir = layout.buildDirectory.dir("assembledSite")

tasks.register<Exec>("assembleSite") {
    group = "documentation"
    description = "Build the MkDocs Material site (pip install mkdocs-material) with the Dokka API reference under api/."
    dependsOn("dokkaGenerate")
    inputs.file("mkdocs.yml")
    inputs.dir("docs")
    workingDir = projectDir
    commandLine("mkdocs", "build", "--strict", "--site-dir", siteDir.get().asFile.absolutePath)
    doLast {
        copy {
            from(layout.buildDirectory.dir("dokka/html"))
            into(siteDir.map { it.dir("api") })
        }
    }
}
