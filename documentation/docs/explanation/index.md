# Explanation

Kt-Constraints separates the meaning of a constraint problem from the machinery that solves it. These pages explain the design choices behind that separation and the trade-offs they create.

- [Principles](principles.md): immutable models, variable identity, exact semantics and evidence for answers.
- [Architecture](architecture.md): module boundaries, dependency direction and the path from expressions to results.
- [Engines and routes](engines-and-routes.md): why reaching the same engine through different adapters matters.
- [Solving modes](solving-modes.md): batch search, incremental checks and reversible stores, including ownership by an external logic engine.
- [Global constraints](global-constraints.md): preserving structure, native translations and exact decompositions.
- [Automatic solver selection](automatic-solver-selection.md): eligibility, ranking, fallback, races and benchmark evidence.
- [The pure-Kotlin solver](pure-kotlin-solver.md): the experimental engine's design and limitations.
- [History and status](history-and-status.md): how the project grew and which parts remain incomplete.

For a working introduction, start with [your first model in Kotlin](../tutorials/kotlin-first-model.md). For a specific task, use the [how-to guides](../how-to/index.md). The [reference](../reference/index.md) and [API documentation](/kt-constraints/api/index.html) describe the available declarations.
