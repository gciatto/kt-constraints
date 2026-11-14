// JVM-only public interop facade; actual bridge methods will accompany the first solver backend.
dependencies {
    api(project(":model"))
    api(project(":solve"))
}
