# Solve a knapsack from Java

You will choose items with the greatest total value while keeping their combined weight at most 5.
The Java facade builds the model and runs Choco, first with a blocking call and then through a
`CompletionStage`. You need JDK 17 or later and a Java application project.

## 1. Add the dependencies

Choose a version from [GitHub Packages](https://github.com/gciatto?tab=packages&repo_name=kt-constraints).
Configure the Maven repository `https://maven.pkg.github.com/gciatto/kt-constraints` and Maven Central.
GitHub Packages requires your GitHub login and a token with `read:packages`; use your build tool's
external credentials configuration. In Gradle, give the GitHub repository a `credentials` block
whose `username` reads `System.getenv("GITHUB_ACTOR")` and whose `password` reads
`System.getenv("GITHUB_TOKEN")`, then set those environment variables before running the build.
In Maven, give the repository an ID such as `github`, and put a matching server entry with your
login and token in `~/.m2/settings.xml`. Follow [Install](../how-to/install.md) for the full setup.

In a Gradle project's `dependencies` block, add these coordinates, replacing `<version>` with the same
release for both:

```kotlin
implementation("io.github.gciatto:kt-constraints-interop-java:<version>")
implementation("io.github.gciatto:kt-constraints-solve-choco:<version>")
```

For Maven, add dependencies with group `io.github.gciatto`, artifact IDs
`kt-constraints-interop-java` and `kt-constraints-solve-choco`, and that version. These two artifacts are
JVM modules, so neither needs a `-jvm` suffix. The facade brings the modelling API transitively;
Choco needs no separate executable installation.

## 2. Create the program

Create `src/main/java/io/github/gciatto/ktconstraints/interop/examples/Knapsack.java` with this program:

```java
--8<-- "interop-java/src/test/java/io/github/gciatto/ktconstraints/interop/examples/Knapsack.java:program"
```

Each `take` variable is 0 or 1. The first weighted sum enforces capacity; the second is the value to
maximize. `eq` and the comparison methods such as `le` construct constraints; Java's `==` does not.

The result is read only after checking `OPTIMAL` and the assignment. `longValueOf` performs an exact
range-checked conversion. The small item values fit in a Java `long`.

## 3. Run both entry points

Run `io.github.gciatto.ktconstraints.interop.examples.Knapsack` from your IDE, or set it as your
Gradle application's main class and run `./gradlew run`. Both calls print:

```text
take0 = 1
take1 = 1
take2 = 0
OPTIMAL: value = 16
```

The chosen items weigh 2 + 3 and are worth 6 + 10. `solve` returns on the calling thread;
`solveAsync` returns a `CompletableFuture` usable as a `CompletionStage`. This program waits for it
so that both paths are easy to compare. Keep the solver open until the stage completes: leaving
try-with-resources closes it and cancels pending work.

Continue with [optimization](../how-to/optimize.md),
[limits and cancellation](../how-to/limits-and-cancellation.md), or the
[Java API reference](../reference/java-api.md).
