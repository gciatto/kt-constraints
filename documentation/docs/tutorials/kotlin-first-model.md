# Your first model in Kotlin

Solve the alphametic **SEND + MORE = MONEY**. Each letter stands for a different decimal digit; S and M cannot
be zero. You will build a model, ask the platform's default portfolio to solve it, then add a constraint that
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
implementation("io.github.gciatto:kt-constraints-solve-portfolio:<version>")
```

The DSL supplies modelling operators. The portfolio supplies `solveAuto` and the platform's default routes.
On JVM, the direct solvers bring their runtime dependencies; this example needs no MiniZinc installation.
For Gradle property credentials instead of environment variables, see [Install](../how-to/install.md).

## 2. Declare the letters

Create `SendMoreMoney.kt` under `src/main/kotlin`. Add these imports:

```kotlin
--8<-- "solve-portfolio/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/portfolio/examples/tutorials/SendMoreMoney.kt:imports"
```

Open a suspending function:

```kotlin
--8<-- "solve-portfolio/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/portfolio/examples/tutorials/SendMoreMoney.kt:entry"
```

Inside it, create the scope and eight digit variables:

```kotlin
--8<-- "solve-portfolio/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/portfolio/examples/tutorials/SendMoreMoney.kt:variables"
```

A `ConstraintScope` collects your declarations and constraints. Keep these variable objects: you will use them
to read the result. The two leading letters have domain `1..9`; the others may be zero.

## 3. Write the equation

Continue inside the function:

```kotlin
--8<-- "solve-portfolio/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/portfolio/examples/tutorials/SendMoreMoney.kt:model"
```

`allDifferent` prevents any two letters from sharing a digit. `+` and `*` construct symbolic expressions;
`eq` constructs a symbolic equality. Kotlin's `==` compares objects and does not add a model constraint.
`build()` captures an immutable model ready to solve.

## 4. Solve and read the answer

Append:

```kotlin
--8<-- "solve-portfolio/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/portfolio/examples/tutorials/SendMoreMoney.kt:solve"
```

`solveAuto` is suspending. It ranks compatible, available routes and tries them in order. The descriptor tells
you which route answered. The status check makes an unexpected `UNKNOWN` or `INFEASIBLE` visible before you
read the assignment.

The digits are `[9, 5, 6, 7, 1, 0, 8, 2]`: **9567 + 1085 = 10652**. `intValueOf` is safe here because every
domain is a decimal digit. For larger integers, `valueOf` returns the exact integer; bounded conversions throw
if the value does not fit. See [Results and statuses](../reference/results-and-statuses.md).

## 5. Add a twist

Require D to be 6 and solve a new snapshot:

```kotlin
--8<-- "solve-portfolio/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/portfolio/examples/tutorials/SendMoreMoney.kt:twist"
```

The new result is `INFEASIBLE`: no assignment satisfies the puzzle with that extra constraint. The earlier
`model` and result still describe the original puzzle. Return the digits and final status so a test can check
them, then close the function:

```kotlin
--8<-- "solve-portfolio/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/portfolio/examples/tutorials/SendMoreMoney.kt:end"
```

Add `suspend fun main() { sendMoreMoney() }` to the same file and run its main function from your IDE. You should
see the feasible solution first and `INFEASIBLE` last. The checked-in `SendMoreMoneySpec` runs this same path
and asserts both outcomes.

## Where next

Share a model across targets in [One model, every platform](kotlin-multiplatform.md), or learn to
[optimize an objective](../how-to/optimize.md) and [enumerate solutions](../how-to/enumerate-solutions.md).
For the design behind the scope and immutable model, read [Architecture](../explanation/architecture.md).
The full Kotlin API is at [API reference](/kt-constraints/api/index.html).
