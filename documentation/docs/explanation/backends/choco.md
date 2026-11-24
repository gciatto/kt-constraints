# Choco

## What it is

Choco is a finite-domain constraint programming engine. It combines search with **propagation**: removing values that cannot satisfy posted constraints. This family exposes both fixed-model batch search and a separate store for applications that manage reversible state themselves.

## Routes and classes

`ChocoBackend` and `ChocoStore` belong to `:solve-choco`, artifact `kt-constraints-solve-choco`. Both use `choco/direct`, with different capability sets. JVM automatic defaults include the batch backend; automatic solving does not provide the store API.

## Platforms and runtime requirements

This is a JVM adapter using Choco's Java implementation. There is no separate solver executable to install. It is not a Node, browser or Kotlin/Native route. See [platform availability](../../reference/platform-availability.md) for test coverage.

## Capabilities

| API | Operations | Evidence |
|---|---|---|
| `ChocoBackend` | Batch satisfaction, integer optimization, exhaustive projected satisfaction enumeration | Batch and stream statuses |
| `ChocoStore` | Incremental posting, backtracking, domain propagation, entailment | `PROPAGATION` store evidence |

The batch solver does not expose incremental assertion sessions, assumptions or unsatisfiable assumption cores. Store marks and rollback are a separate interface, rather than SMT-style sessions. Store explanations are not promises of minimal cores.

`ChocoStore` can expose narrowed domains and domain changes. Its residual projection is sound but need not describe the exact projected relation. A **residual** describes constraints remaining on selected variables after posting. See [solving modes](../solving-modes.md) and [constraint stores](../../how-to/constraint-stores.md) for ownership, forks, marks and explanations.

## Supported modelling features and numeric limits

The Boolean/integer subset includes finite domains, arithmetic products and weighted sums, min/max, element, tables, cardinalities and built-in structural globals. Choco can reify all-different and table constraints natively: **reification** makes a constraint's truth value usable inside a Boolean formula. Structural globals nested inside formulas can still use exact decompositions.

The [support implementation](https://github.com/gciatto/kt-constraints/blob/develop/solve-choco/src/main/kotlin/io/github/gciatto/ktconstraints/solve/choco/translation/ChocoSupport.kt) uses a conservative magnitude margin of 10^7 for the backend and store. Checks include intermediates and accumulated magnitudes to keep translated relations within Choco's integer bounds. Reals, bit-vectors, unsupported expression classes and out-of-range expressions are rejected. A large domain cannot be made acceptable merely by giving it a small display name or output projection.

## Options type

`ChocoOptions(search = null)` selects an optional `ChocoSearch` branching strategy. Null keeps the engine default. Strategies include domain/weighted-degree, activity, conflict history, failure measures, minimum-domain and input-order variants. The exact enum values are listed in [configuration](../../reference/configuration.md); use `SolveOptions` for ordinary execution limits.

## Performance

Choco is often the fastest JVM route on the catalogue's easy satisfaction, assignment and job-shop models. It times out on harder measured single-machine scheduling cases where CP-SAT remains effective. The separate store benchmark measures posting, propagation and checkpoint restoration; batch timings do not predict those costs. See [benchmarks](../../reference/benchmarks.md) for the recorded numbers and qualifications.

## How to use it directly

[Choose a solver](../../how-to/choose-a-solver.md) quotes a tested Z3 registry example that you can adapt by registering this backend. [Constraint stores](../../how-to/constraint-stores.md) quotes tested `ChocoStore` usage, including reversible operations. Use [enumeration](../../how-to/enumerate-solutions.md) or [optimization](../../how-to/optimize.md) for batch workflows.
