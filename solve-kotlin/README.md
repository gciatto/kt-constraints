# Pure Kotlin constraint solver

`:solve-kotlin` implements the `kotlin/direct` route in `commonMain`. Its current scope is the union of the
**built-in modelling constructs already exposed by this repository**, with exact arithmetic and shared execution
contracts. It runs on the JVM, JS (Node and browsers) and Kotlin/Native without any external solver runtime.
See [validation](https://gciatto.github.io/kt-constraints/explanation/pure-kotlin-solver/#how-correctness-is-checked) for the actual verification status.

## Installation

```kotlin
dependencies {
    implementation("io.github.gciatto:kt-constraints-solve-kotlin:<version>")
}
```

The following example belongs in a suspending caller:

```kotlin
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntProduct
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.kotlin.KotlinBackend

val scope = ConstraintScope.empty()
val x = scope.intVar("x", 0..10)
val y = scope.intVar("y", 0..10)
scope.add(IntEquality(IntSum(x, y), IntLiteral(10)))
scope.maximize(IntProduct(x, y))

val result = KotlinBackend().createSolver().use { solver ->
    solver.solve(scope.buildModel())
}
// Mathematical optimum: x = y = 5, objective = 25.
```

`createSolver()` returns an `IncrementalConstraintSolver`. Use `solutions(model, options)` for distinct projected
solutions or improving integer incumbents; use `openSession(model)` for assertion frames, `push`, `popTo`, and
temporary `check(assumptions, timeoutMillis)`. Sessions answer satisfiability queries; model objectives are used
by batch solving and optimisation streams.

Java can construct `new KotlinBackend()` and pass it to `BlockingSolver.of(...)`. Kotlin/JS uses the common
API. TypeScript loads it with `loadKotlin()` from `kt-constraints-interop-js`.

## Implemented surface

| Area | Representation and behaviour |
|---|---|
| Booleans | All current Boolean nodes, arbitrary nesting, implication, equality and negation |
| Finite integers | Exact arbitrary-precision domains, holes, signed sums/differences/products, negation, weighted sums and comparisons |
| Globals | Fully reified `AllDifferent`, zero-based `Element`, allowed-tuple `Table`, and all three Boolean cardinalities |
| Reals | Unbounded real variables with exact rational linear expressions, equality, strict/non-strict comparisons, disequality through negation, and integer embeddings |
| Bit-vectors | Every current fixed-width operator and relation, wrapping arithmetic, saturating shifts, signed comparisons and signed/unsigned integer conversion |
| Search | Satisfaction, finite discrete enumeration, exact projection blocking, minimisation/maximisation of integer expressions |
| Incrementality | Persistent learned clauses, user assertion frames, owner-checked checkpoints, temporary assumptions and sufficient assumption cores |
| Lifecycle | Independent searches, cooperative coroutine cancellation, close, timeouts, deterministic work budgets and compiler limits |

All globals are losslessly decomposed into circuits. This route does not advertise native global propagation,
domain-reflection sessions or entailment. Its internal reversible domain store supports root integer presolve;
it is not yet a public CLP store.

`Element` definedness is asserted independently of Boolean truth. Even an element below a true disjunction
must have a valid index when that enclosing assertion is active. Definedness from a temporary assertion is
temporary too. Objective expressions also require definedness.

Integer domains are finite, although their bounds and intermediate values are arbitrary precision. The
current core has integer objectives, linear real expressions and real objectives, which the exact simplex
optimizes over continuous linear programs. Scheduling globals, set/graph/string/array theories, real objectives
over integer variables, division/remainder, general nonlinear real optimisation and the full feature sets
of external solvers require future IR and engine work.

## Engine

1. **Root presolve:** exact affine bound propagation, singleton-factor products and assigned-value
   `AllDifferent` filtering narrow finite domains. Only unconditional root facts are used.
2. **Compilation:** Boolean circuits represent finite integers and bit-vectors. Integer variables use a
   compact offset from their lower bound; holes are constrained explicitly. Intermediate widths are derived
   from exact conservative bounds, preserving mathematical integer semantics.
3. **SAT:** two-watched-literal propagation, first-UIP conflict learning, activity-based branching and
   temporary assumptions share one persistent clause database per engine.
4. **Real feasibility:** remaining real atoms become exact rational inequalities for Fourier–Motzkin
   elimination and witness reconstruction. An inconsistent candidate produces a sound clause containing
   the relevant atom polarities and embedded-bit assignments.
5. **Results:** original assertions are evaluated against complete assignments before projection. Optimisation
   bisects exact integer objective bounds and reports optimality only after its upper/lower bound is proved.

Rational predicates containing only discrete expressions are scaled by an exact positive common denominator
and compiled directly into SAT inequalities. They do not enumerate candidate integers through the real solver.
Real witnesses use rational arithmetic throughout; `Double` is used only by SAT branching heuristics.

Full architecture, invariants and the next implementation stages are in [the design](https://gciatto.github.io/kt-constraints/explanation/pure-kotlin-solver/).

## Limits and diagnostics

`KotlinConfiguration` defaults:

| Setting | Default | Scope |
|---|---:|---|
| `maxBitWidth` | 16,384 | Width of an individual arithmetic circuit |
| `maxVariables` | 1,000,000 | SAT variables, including compiler auxiliaries |
| `maxClauses` | 4,000,000 | Compiler/theory-added clauses; SAT-learned clauses are separate |
| `workLimit` | Unlimited | Deterministic work units across compilation and search |

`SolveOptions` supplies the wall-clock timeout and solution limit. A reached limit returns `UNKNOWN` without
an incumbent or `FEASIBLE` with one, with the matching termination reason. It never proves infeasibility or
optimality. Coroutine cancellation propagates through the existing managed-stream contract.

Where available, completion events and completed session checks expose work, circuit size, decisions,
propagations, SAT/theory conflicts and presolve-reduction statistics. Interrupted or uninitialised operations
may omit statistics. The existing managed satisfaction shortcut returns its first solution and
does not carry stream completion statistics; consume the stream to obtain them.

Projected real solution sets may be infinite. Enumeration returns distinct exact rational witnesses and
terminates when the projection is exhausted or a requested limit is reached. Empty projections produce one
existential witness.

Resource limits are not a total-memory guarantee: learned clauses and Fourier–Motzkin rows are not yet capped
independently. This baseline has no restarts, clause-database reduction, specialised global propagators or
simplex. Fourier–Motzkin elimination and coarse mixed-theory learning can be expensive; the design records
these as priorities for measured improvements.

## Dependencies and platforms

No external dependency or version is added. The module uses `:solve-spi`, the existing transitive kt-math
integer/rational infrastructure, and the repository's existing coroutines dependency. Tests reuse `:test-solve`
and the configured Kotest stack.

All solver production code is in `commonMain`; it uses no native solver, subprocess, JNI, C binding or Wasm
solver binary. Existing build configuration supplies JVM, JavaScript, Linux/Windows Native, macOS arm64 and
representative iOS targets. Shared contracts pass on the JVM, Node and Linux x64 Native; other targets rely on CI. Kotlin/Wasm
is not added as a target.

## Verification and manual measurements

```bash
./gradlew :solve-kotlin:jvmTest :solve-kotlin:jsNodeTest
./gradlew :solve-kotlin:linuxX64Test
./gradlew :interop-java:test --tests '*KotlinJavaUsageSpec'
./gradlew :interop-js:jsNodeTest
./gradlew :solve-kotlin:jvmBenchmark
```

On their hosts, use `macosArm64Test` and `mingwX64Test`; the Native CI matrix includes this backend explicitly.
The test sources reuse shared solver, differential and incremental contracts and add exhaustive small SAT/BV
cases, exact arithmetic cases, interruption/retry tests, scoped-theory regressions and language-boundary tests.

`jvmBenchmark` is a manual task outside routine `check`. It emits raw CSV phase measurements for Boolean,
finite scheduling and mixed-real workloads, plus runtime metadata and witness/restoration checks. Timings
separate construction, setup, presolve/compilation, first check, projection and incremental operations.
No measurements or comparative performance claims are included in this delivery.
