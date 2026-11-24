# One model, every platform

Build a four-queen model in common Kotlin, then run the same test on JVM, JavaScript under Node, and
Kotlin/Native. You will place one queen in each column of a four-by-four board so no two attack each other.

Complete [Your first model in Kotlin](kotlin-first-model.md) first. Use a Kotlin Multiplatform Gradle project
with JVM, JS/Node and a Native target supported by your host. The commands below use Linux x64.

## 1. Prepare the source sets

Use the authenticated Maven repository from the first tutorial. Enable the targets in your project's
`kotlin` block:

```kotlin
jvm(); js(IR) { nodejs() }
linuxX64()
```

Add `io.github.gciatto:kt-constraints-dsl:<version>` and
`io.github.gciatto:kt-constraints-solve-auto:<version>` to `commonMain.dependencies`, using the same
published version for both. Put the model and solving helpers below in `src/commonMain/kotlin/NQueens.kt`.
They use common APIs throughout.

JVM uses bundled solver libraries. Node uses Z3 Wasm through the `z3-solver` npm dependency. Native requires a
Z3 shared library: set `KT_CONSTRAINTS_Z3_LIBRARY` to its absolute path, or make it available on the loader
path. See [Run on Kotlin/Native with Z3](../how-to/native-and-z3.md).

## 2. Describe the board

Copy the imports from the tested example. The later how-to helpers use the enumeration and pairwise imports;
you can omit those imports if you copy only the tutorial helpers.

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/tutorials/NQueens.kt:imports"
```

Create one variable per column. Its value is the queen's row, numbered from zero:

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/tutorials/NQueens.kt:board"
```

A list with one variable per column already guarantees one queen per column. The `allDifferent` constraint
guarantees different rows.

## 3. Exclude diagonal attacks

Queens share a diagonal when either `row + column` or `row - column` is equal. Add a helper that requires
both sets of expressions to be distinct:

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/tutorials/NQueens.kt:diagonal-globals"
```

## 4. Solve from common code

Add this suspending helper:

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/tutorials/NQueens.kt:solve"
```

The returned list gives rows in column order. Either `[1, 3, 0, 2]` or `[2, 0, 3, 1]` is valid. Do not rely on a
particular order or board being returned by different solvers.

For this model, the default ranking prefers these routes when their runtimes are available:

| Target | Preferred route | Runtime |
|---|---|---|
| JVM | `cp-sat/direct` | OR-Tools native libraries bundled with its Maven dependency |
| JS under Node | `z3/wasm` | `z3-solver` npm dependency |
| Kotlin/Native | `z3/native` | Separately installed Z3 shared library |

On JVM, the portfolio also registers Choco, Z3, SCIP and HiGHS. JVM and Node can register MiniZinc routes when
`MINIZINC_EXECUTABLE` is set. `solveAuto` may fall back if a candidate fails or returns `UNKNOWN`, so read the
printed `result.descriptor.id` to find the actual answering route. The pure-Kotlin backend is not part of this
default registry. See [Automatic solver selection](../explanation/automatic-solver-selection.md).

## 5. Run the same test on each target

Apply the `io.kotest` plugin (6.2.7) and `com.google.devtools.ksp` plugin (2.3.14). In
`commonTest.dependencies`, add `io.kotest:kotest-framework-engine:6.2.7` and
`io.kotest:kotest-assertions-core:6.2.7`; in `jvmTest.dependencies`, add
`io.kotest:kotest-runner-junit5:6.2.7`. Configure JVM test tasks with
`tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach { useJUnitPlatform() }`.
These are the versions used by this repository. Add these imports to a common test file:

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
```

Copy this test class into `src/commonTest/kotlin/NQueensSpec.kt`, in the same package as your helpers:

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/tutorials/NQueensSpec.kt:platform-test"
```

Kotest's test body is suspending, so it can call `solveFourQueens()` directly. Run `./gradlew jvmTest`,
`./gradlew jsNodeTest`, then `./gradlew linuxX64Test` in your project. Node is provisioned by Kotlin's Gradle
plugin; Native needs the host toolchain and the Z3 library described above.

To run the checked-in examples from this repository's root instead:

```shell
./gradlew :solve-auto:jvmTest :solve-auto:jsNodeTest --continue
KT_CONSTRAINTS_Z3_LIBRARY=/absolute/path/to/libz3.so ./gradlew :solve-auto:linuxX64Test
```

The repository keeps these helpers in `commonTest` so the documentation examples compile and execute on all
three targets; your application keeps reusable helpers in `commonMain`.

Next, [model N-Queens variants and count its solutions](../how-to/problems/n-queens.md), or check
[platform availability](../reference/platform-availability.md) before choosing another deployment target.
