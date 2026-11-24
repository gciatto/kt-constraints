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

The DSL supplies modelling operators. The `:solve-auto` module supplies `AutoBackend` and the platform's default routes.
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

## 3. Solve and try an extra constraint

Use the extension inside `with(ConstraintScope.empty())` so the variable references remain available when
reading assignments. `build()` captures an immutable model. Load `AutoBackend`, create its solver, and close
both with `use`:

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/tutorials/SendMoreMoney.kt:solve"
```

The suspending `solve` ranks compatible, available routes and tries them in order. The result descriptor
identifies the answering route. Check the status before reading an assignment.

The digits are `[9, 5, 6, 7, 1, 0, 8, 2]`: **9567 + 1085 = 10652**. `intValueOf` is safe because the domains
contain decimal digits. For larger integers, `valueOf` returns the exact integer; bounded conversions throw
if the value does not fit. See [Results and statuses](../reference/results-and-statuses.md).

The helper then adds `D eq 6` to the same scope and solves a new snapshot. The result is `INFEASIBLE`; the
earlier snapshot and result still describe the original puzzle. It returns the digits and final status so
`SendMoreMoneySpec` can assert both outcomes.

Add `suspend fun main() { solveSendMoreMoney() }` to the same file and run it from your IDE. The output shows
the feasible solution followed by `INFEASIBLE`.

## Where next

Share a model across targets in [One model, every platform](kotlin-multiplatform.md), or learn to
[optimize an objective](../how-to/optimize.md) and [enumerate solutions](../how-to/enumerate-solutions.md).
For the design behind the scope and immutable model, read [Architecture](../explanation/architecture.md).
The full Kotlin API is at [API reference](/kt-constraints/api/index.html).
