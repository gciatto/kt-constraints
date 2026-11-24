# Tutorials

Contents below follow navigation order. Choose a language and follow one complete example. Each tutorial builds a model, runs a solver and checks the
answer. You need basic familiarity with the language you choose; no solver experience is assumed.

| Tutorial | Who it is for | Prerequisites | What you build |
|---|---|---|---|
| [Your first model in Kotlin](kotlin-first-model.md) | Kotlin developers starting with the modelling DSL | JDK 17+, a Kotlin/JVM Gradle project, GitHub Packages credentials | SEND + MORE = MONEY, followed by an extra constraint |
| [One model, every platform](kotlin-multiplatform.md) | Kotlin developers sharing models across targets | The first Kotlin tutorial; a Kotlin Multiplatform project, Node and a Native toolchain; a Z3 shared library for Native | One N-Queens model tested on JVM, Node and Native |
| [Knapsack from Java](java-knapsack.md) | Java developers using the blocking facade | JDK 17+, Gradle or Maven, GitHub Packages credentials | A knapsack optimization model |
| [Sudoku from TypeScript](typescript-sudoku.md) | TypeScript developers using the ES module facade | Node 22+, npm, TypeScript and GitHub Packages credentials | A Sudoku solver |
| [Scheduling in the browser](browser-scheduling.md) | Web developers running a solver in the browser | TypeScript, a bundler and a local web server configured for the solver runtime | A browser scheduling model |
| [Assignment from C](c-assignment.md) | C developers using the shared library | A C compiler, the library/header release bundle and a Z3 shared library | An assignment model |

Use [Install](../how-to/install.md) for package authentication and runtime setup. If you already have a model
and want to perform a particular task, use the [how-to guides](../how-to/index.md).
