// JVM-only route: the engine ships as a JVM library, so no JS or Native artifacts are published.
dependencies {
    api(project(":solve-spi"))
    api(project(":model"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.choco.solver)
    testImplementation(project(":test-solve"))
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
}

tasks.register<JavaExec>("storeBenchmark") {
    group = "benchmark"
    description = "Times CLP-style store operations (N-queens labeling) against batch re-solving."
    classpath(sourceSets["test"].runtimeClasspath)
    mainClass.set("io.github.gciatto.ktconstraints.solve.choco.ChocoStoreBenchmark")
}
