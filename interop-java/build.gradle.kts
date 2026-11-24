// JVM-only Java facade: Java number conversions, re-exporting the blocking and CompletableFuture API of :solve-blocking.
dependencies {
    api(project(":model"))
    api(project(":dsl"))
    api(project(":solve"))
    api(project(":solve-spi"))
    api(project(":solve-blocking"))
    testImplementation(project(":solve-choco"))
    testImplementation(project(":solve-cpsat"))
    testImplementation(project(":solve-kotlin"))
    testImplementation(project(":solve-mip"))
    testImplementation(project(":solve-z3"))
    testImplementation(project(":solve-auto"))
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
}
