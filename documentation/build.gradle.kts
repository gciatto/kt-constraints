// Same separate :documentation module / MkDocs site layout as kt-math, with a small initial task graph.
val siteDir = layout.buildDirectory.dir("assembledSite")

tasks.register<Exec>("assembleSite") {
    group = "documentation"
    description = "Build MkDocs Material documentation (pip install mkdocs-material)."
    dependsOn("syncDocs")
    inputs.file("mkdocs.yml")
    inputs.dir("docs")
    workingDir = projectDir
    commandLine("mkdocs", "build", "--strict", "--site-dir", siteDir.get().asFile.absolutePath)
}

tasks.register<Copy>("syncDocs") {
    from(rootProject.file("ROADMAP.md")) { rename { "roadmap.md" } }
    from(rootProject.file("docs/ARCHITECTURE.md")) { rename { "architecture.md" } }
    from(rootProject.file("docs/RELEASING.md")) { rename { "releasing.md" } }
    into(layout.projectDirectory.dir("docs"))
}
