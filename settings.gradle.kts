@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

plugins {
    id("com.gradle.develocity") version "4.6.1"
    id("org.danilopianini.gradle-pre-commit-git-hooks") version "2.1.30"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

gitHooks {
    commitMsg { conventionalCommits() }
    createHooks(true)
}

rootProject.name = "kt-constraints"

include(":core")
include(":model")
include(":dsl")
include(":solve")
include(":solve-spi")
include(":solve-minizinc")
include(":solve-flatzinc")
include(":solve-z3")
include(":solve-choco")
include(":solve-cpsat")
include(":solve-highs")
include(":solve-mip")
include(":solve-auto")
include(":solve-kotlin")
include(":interop-java")
include(":interop-js")
include(":interop-c")
include(":test-core")
include(":test-solve")
include(":documentation")
