// JVM-only Java facade: blocking and CompletionStage solving over any SolverBackend.
dependencies {
    api(project(":model"))
    api(project(":dsl"))
    api(project(":solve"))
    api(project(":solve-spi"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(project(":solve-choco"))
    testImplementation(project(":solve-cpsat"))
    testImplementation(project(":solve-kotlin"))
    testImplementation(project(":solve-mip"))
    testImplementation(project(":solve-z3"))
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
}
