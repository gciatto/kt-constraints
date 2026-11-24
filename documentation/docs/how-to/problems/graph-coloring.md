# Minimize the colours of a graph

Assign colours to vertices so that adjacent vertices have different colours, using as few colours as possible. The tiny instance has a triangle on vertices 0, 1 and 2, plus an edge from 2 to vertex 3.

## 1. Build the model

Use `:dsl` for the operators. The examples solve with `AutoBackend` from `:solve-auto`, which picks a bundled solver for your platform. See [Install](../install.md) for the dependencies and [Choose a solver](../choose-a-solver.md) to pick one yourself.

Imports used by the example:

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/problems/GraphColoring.kt:imports"
```

Give every vertex a colour index, post one symbolic inequality per edge, and minimize the number of distinct indices.

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/problems/GraphColoring.kt:model"
```

`nValue(colors)` counts distinct values even when colour labels have gaps; it is a DSL expression built from Boolean comparisons and sums. Do not put all vertices in one `allDifferent`: that would forbid nonadjacent vertices from sharing a colour. For a known **clique**—a group of vertices all adjacent to one another— `allDifferent` over just that clique expresses the needed inequalities.

## 2. Solve and read the result

Require `OPTIMAL` before reporting the chromatic number. The triangle requires at least three colours, and three suffice; the leaf may reuse either colour different from vertex 2.

See [Optimize](../optimize.md) for limits and incumbent handling.

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/problems/GraphColoring.kt:solve"
```

Call the helper from a suspending caller with the sample data:

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/problems/GraphColoringSpec.kt:example"
```

Expected output:

```text
Colours: 3
```

The solve call suspends; run it in a coroutine. See [Results and statuses](../../reference/results-and-statuses.md) before treating a result as a proof.

## Variations

- A feasible colouring may use arbitrary numeric labels. Compare edge validity and the number of distinct labels, rather than expecting a particular vector.
- For a fixed palette of `k` colours, restrict domains to `0 until k` and omit the objective.
- If colour labels are interchangeable, fixing vertex 0 to colour 0 removes some symmetry.
- To balance colour-class sizes, use `globalCardinality` with a count variable per permitted label; restrict the colour domains to those labels as well.

See the [modelling language reference](../../reference/modelling-language.md), [global constraints explanation](../../explanation/global-constraints.md), and [API reference](/kt-constraints/api/index.html) for the operators and their semantics.
