# Install Kt-Constraints

Choose a version from the [GitHub releases](https://github.com/gciatto/kt-constraints/releases).
Replace `<version>` below with that version. The release configuration publishes Maven and npm
packages to GitHub Packages, and C archives to GitHub releases. Maven Central and npmjs
publication is opt-in through `ENABLE_MAVEN_CENTRAL` and `ENABLE_NPMJS`; the default
release destinations are GitHub Packages and GitHub release assets.

## 1. Configure package access

For Maven or npm packages, use a GitHub personal access token with `read:packages`.
Supply credentials through your environment or your user-level package-manager configuration;
keep tokens out of project files.

## 2. Add your dependencies

### Gradle: Kotlin or Java

Add `mavenCentral()` for transitive dependencies and the Maven repository
`https://maven.pkg.github.com/gciatto/kt-constraints`. Set that repository's credentials
from `GITHUB_ACTOR` (your GitHub username) and `GITHUB_TOKEN`, or user-level Gradle properties.

For Kotlin, add these dependencies to the source set that uses the API:

- `implementation("io.github.gciatto:kt-constraints-dsl:<version>")`
- `implementation("io.github.gciatto:kt-constraints-solve-auto:<version>")`

A **backend** creates solvers; a **route** identifies how a particular engine is reached.
`AutoBackend` chooses among the routes bundled for your platform; see [engines and routes](../explanation/engines-and-routes.md).

Gradle selects JVM, JS or Native variants from module metadata. `:solve-auto` supplies
`AutoBackend` and platform-specific default routes. To use only an explicit backend, add its
artifact instead, such as `kt-constraints-solve-z3` or the JVM-only `kt-constraints-solve-choco`.

Java projects add `kt-constraints-interop-java` and their backend artifact. Kotlin code on the JVM or Native that
cannot suspend adds `kt-constraints-solve-blocking` for blocking solver wrappers. See the
[artifact reference](../reference/modules-and-artifacts.md) for the full list.
The pure-Kotlin backend, `kt-constraints-solve-kotlin`, runs on every platform without an external runtime. `:solve-auto` brings it in and places it in each platform's route order; add it explicitly to use it directly.

### Maven: JVM

Add the same GitHub Maven repository to your POM. Give it a repository ID and put a matching
`server` entry with your username and token in your user `settings.xml`. Use group
`io.github.gciatto` and explicitly select JVM variants: for example,
`kt-constraints-dsl-jvm`, `kt-constraints-interop-java` and `kt-constraints-solve-z3-jvm`,
all at `<version>`. The Java facade is already a JVM artifact and has no `-jvm` suffix.

### npm: JavaScript or TypeScript

Use Node 22 or later. Put the following two lines in your project's `.npmrc`; the token
reference is expanded from the environment:

```ini
@gciatto:registry=https://npm.pkg.github.com
//npm.pkg.github.com/:_authToken=${GITHUB_TOKEN}
```

Then run:

```sh
npm install @gciatto/kt-constraints-interop-js
```

The package is an ES module with TypeScript declarations. Import from
`@gciatto/kt-constraints-interop-js`. Executable examples quoted on this site use
`kt-constraints-interop-js`, the local package name used by the repository's boundary tests.

### C: shared library archive

Download `kt-constraints-c-<version>-<target>.zip` from the release assets, choosing
`linuxX64`, `macosArm64` or `mingwX64`. Extract the generated header and shared library.
Configure your C compiler's include path and link against `ktconstraints`; make the library
visible to your operating system's dynamic loader when you run the program.
Windows archives also include a `.def` file.

Install a compatible Z3 shared library and set `KT_CONSTRAINTS_Z3_LIBRARY` to its absolute
path. See the [C tutorial](../tutorials/c-assignment.md) for a complete consumer and
[the C API](../reference/c-api.md) for handle and string ownership.

## 3. Prepare the solver runtime

| Route | Runtime setup |
| --- | --- |
| Choco on JVM | Included as a Java dependency. |
| CP-SAT, SCIP and HiGHS through OR-Tools on JVM | Native libraries come with the OR-Tools dependency for supported desktop platforms. |
| Z3 on JVM | Native libraries come with `z3-turnkey`. |
| Z3 on Node | `z3-solver` is an npm dependency. |
| Z3 in a browser | Serve `z3-built.js` and `z3-built.wasm`; configure cross-origin isolation. See [browser setup](../tutorials/browser-scheduling.md). |
| Z3 on Native or through C | Install a shared library; see [Native setup](native-and-z3.md). |
| MiniZinc | Install the CLI and solver configuration, or serve the browser runtime assets; see [MiniZinc setup](use-minizinc.md). |

Before solving, check [availability and whole-model support](choose-a-solver.md).

## Build from source instead

Use JDK 17 or later and the committed Gradle wrapper:

```sh
./gradlew publishToMavenLocal
```

Read the git-derived version from the build output and use `mavenLocal()` in the consuming
Gradle project, or Maven's local repository. Local publishing skips signing when no signing
key is configured.

Build the npm distribution with:

```sh
./gradlew :interop-js:jsNodeProductionLibraryDistribution
npm install --install-links ./interop-js/build/dist/js/productionLibrary
```

Build the C archives supported by your host with:

```sh
./gradlew :interop-c:releaseAssets
```

The archives appear under `interop-c/build/distributions`. See
[tests and benchmarks](contributing/run-tests-and-benchmarks.md) to verify a source checkout.
