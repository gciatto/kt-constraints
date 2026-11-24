# Your first model in Kotlin

A **route** is an engine plus its integration path; `descriptor.id` names the route that answered.
See [engines and routes](../explanation/engines-and-routes.md) for the distinction.

Solve the alphametic **SEND + MORE = MONEY**. Each letter stands for a different decimal digit; S and M cannot
be zero. You will build a model, ask the platform's automatic backend to solve it, then add a constraint that
makes the puzzle impossible.

## 1. Prepare a Kotlin project

Start with a Kotlin/JVM Gradle project and JDK 17 or later. The repository examples use Kotlin 2.4.21.
Choose a published library version from the [releases](https://github.com/gciatto/kt-constraints/releases).
Use that version in both coordinates below, replacing `<version>`.

GitHub Packages requires authentication for public packages too. Create a personal access token with
`read:packages`, and set `GITHUB_ACTOR` to your GitHub login and `GITHUB_TOKEN` to the token in your local
environment. Keep the token out of your project files.

In your `repositories` block, add `mavenCentral()` and this Maven repository:

```kotlin
maven("https://maven.pkg.github.com/gciatto/kt-constraints") {
    credentials { username = System.getenv("GITHUB_ACTOR"); password = System.getenv("GITHUB_TOKEN") } }
```

In your `dependencies` block, add:

```kotlin
implementation("io.github.gciatto:kt-constraints-dsl:<version>")
implementation("io.github.gciatto:kt-constraints-solve-auto:<version>")
```

The DSL supplies modelling helpers such as `allDifferent`; the operators are members of the expressions. The `:solve-auto` module supplies `AutoBackend` and the platform's default routes.
On JVM, the direct solvers bring their runtime dependencies; this example needs no MiniZinc installation.
For Gradle property credentials instead of environment variables, see [Install](../how-to/install.md).

## 2. Declare the letters

Create `SendMoreMoney.kt` under `src/main/kotlin`. Add these imports:

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/tutorials/SendMoreMoney.kt:imports"
```

Define a model-building extension on `ConstraintScope` that returns the letters:

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/tutorials/SendMoreMoney.kt:model"
```

The scope collects declarations and constraints. Leading letters have domain `1..9`; the others may be zero.
`allDifferent` prevents letters from sharing a digit. `+` and `*` construct symbolic expressions, and `eq`
constructs symbolic equality. Kotlin's `==` compares objects and does not add a constraint.

## 3. Solve the puzzle

Call the extension inside `ConstraintScope.empty { }`: the block runs on a fresh scope, so the returned letters
remain available when reading the assignment. `buildModel()` captures an immutable model. Load `AutoBackend` with
`use`, so its runtime is released, and run the solve inside `withSolver`, which creates a solver and closes it
afterwards:

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/tutorials/SendMoreMoney.kt:solve"
```

The suspending `solve` ranks compatible, available routes and tries them in order. The result descriptor
identifies the answering route. Check the status before reading an assignment.

The helper returns each letter's digit, read with `intValueOf`: `{S=9, E=5, N=6, D=7, M=1, O=0, R=8, Y=2}`,
that is, **9567 + 1085 = 10652**. `intValueOf` is safe because the domains contain decimal digits. For larger
integers, `valueOf` returns the exact integer; bounded conversions throw if the value does not fit. See
[Results and statuses](../reference/results-and-statuses.md).

## 4. Add a constraint to a built model

A built model never changes. To try a variation, `extend` it: the block receives a scope seeded with the
model's variables and constraints, and returns a new model with the additions. The variables keep their
identity, so the handles returned by the extension still refer to them:

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/tutorials/SendMoreMoney.kt:twist"
```

`solveSendMoreMoneyWithD(6)` returns `INFEASIBLE`, because D is 7 in the only solution. The original `puzzle`
model is unchanged and still has that solution. `SendMoreMoneySpec` asserts both outcomes.

Add `suspend fun main() { solveSendMoreMoney(); println(solveSendMoreMoneyWithD(6)) }` to the same file and run
it from your IDE. The output shows the answering route, the digits, then `INFEASIBLE`.

## Where next

Share a model across targets in [One model, every platform](kotlin-multiplatform.md), or learn to
[optimize an objective](../how-to/optimize.md) and [enumerate solutions](../how-to/enumerate-solutions.md).
For the design behind the scope and immutable model, read [Architecture](../explanation/architecture.md).
The full Kotlin API is at [API reference](/kt-constraints/api/index.html).
