# Kt-Constraints

**One typed constraint model for Kotlin, Java and TypeScript, solved by Z3, CP-SAT, Choco, SCIP, HiGHS,
MiniZinc or a pure-Kotlin engine on the JVM, Node, the browser and native targets, with exact arithmetic throughout.**

[![CI](https://github.com/gciatto/kt-constraints/actions/workflows/dispatcher.yml/badge.svg)](https://github.com/gciatto/kt-constraints/actions/workflows/dispatcher.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF.svg)
![Status](https://img.shields.io/badge/status-pre--release-orange.svg)

Kt-Constraints is a Kotlin Multiplatform library for constraint programming and SMT. You describe a problem once,
as an immutable, solver-independent model. The library then hands it to whichever solver suits the problem and the
platform, and reports which solver answered and what it actually proved.

```kotlin
import io.github.gciatto.ktconstraints.dsl.allDifferent
import io.github.gciatto.ktconstraints.dsl.eq
import io.github.gciatto.ktconstraints.dsl.plus
import io.github.gciatto.ktconstraints.dsl.times
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.portfolio.solveAuto

suspend fun main() {
    val scope = ConstraintScope.empty()
    val x = scope.intVar("x", 1..9)
    val y = scope.intVar("y", 1..9)
    val z = scope.intVar("z", 1..9)
    scope.add(allDifferent(x, y, z))
    scope.add(x + y eq z * 2)
    scope.maximize(x * y)

    val result = solveAuto(scope.build()) // ranks the solvers bundled for this platform, falls back on failure
    println("${result.status} by ${result.descriptor.id}: ${result.objectiveValue}") // OPTIMAL by cp-sat/direct: 63 (on the JVM)
    println("x = ${result.assignment?.valueOf(x)}, y = ${result.assignment?.valueOf(y)}") // x = 9, y = 7
}
```

## Why Kt-Constraints

- **One model, many solvers.** Expressions, domains and global constraints are plain immutable values with no
  solver inside them. Global constraints such as `Circuit` and `Cumulative` stay global until a specific solver
  lowers them, so each solver can use its native propagator.
- **Exact by construction.** Integers are arbitrary-precision, reals are exact rationals (`0.1` really is `1/10`),
  and bit-vectors have a fixed width. Out-of-range values are never silently truncated, real values are never
  approximated, and constraints are never dropped.
- **Honest answers.** A result says whether it is `OPTIMAL`, `FEASIBLE`, `INFEASIBLE` or `UNKNOWN`, and which solver
  produced it. A timeout keeps the best solution found so far, but never calls it optimal. A solver that cannot
  represent your model rejects it up front and says why.
- **Runs where your code runs.** It works on the JVM, Node, browsers (through WebAssembly) and Kotlin/Native
  desktop targets, with idiomatic APIs for Kotlin, Java and TypeScript.
- **More than one-shot solving.** There are streams of solutions, SMT-style incremental sessions with checkpoints
  and unsat cores, and constraint stores for logic-programming engines.

## What you can model today

| | |
|---|---|
| **Sorts** | Booleans, bounded and unbounded integers, exact reals, fixed-width bit-vectors |
| **Expressions** | Boolean connectives, integer and real arithmetic, comparisons, `min`/`max`/`abs`, int↔real and bool↔int conversions, bit-vector arithmetic and comparisons |
| **Global constraints** | `allDifferent`, `element`, `table`, `regular` (automata), `circuit`, `subCircuit`, `inverse`, `cumulative`, `disjunctive`, `binPacking`, `noOverlap2D`, `count`, `among`, `globalCardinality`, `nValue`, `atMost`/`atLeast`/`exactly`, `increasing`/`decreasing`, `lexLess`/`lexLessOrEqual`, linear sums |
| **Objectives** | Satisfaction, integer minimization and maximization, weighted soft constraints (`minimizeViolations`) |
| **Models** | Named constraints, annotations, source locations, and output projection onto a subset of the variables |

## Solvers and where they run

Every ✅ means that CI runs the real engine there and fails if it is missing or misbehaves.

| Solver (route) | JVM | Node | Browser | Native | Good at |
|---|:---:|:---:|:---:|:---:|---|
| Z3 (`z3/direct`, `z3/wasm`, `z3/native`) | ✅ | ✅ | ✅ | ✅ | Unbounded integers, exact reals, bit-vectors, incremental sessions |
| OR-Tools CP-SAT (`cp-sat/direct`) | ✅ | | | | Combinatorial optimization with native globals |
| Choco (`choco/direct`) | ✅ | | | | Classic CP with reified globals; a propagating constraint store |
| SCIP, HiGHS via OR-Tools (`scip/or-tools`, `highs/or-tools`) | ✅ | | | | Linear integer models |
| MiniZinc: Gecode, Chuffed, CP-SAT, HiGHS, CBC (`*/minizinc`) | ✅ | ✅ Gecode | ✅ Chuffed | | Any solver with a MiniZinc configuration |
| Pure Kotlin (`kotlin/direct`), experimental | ✅ | ✅ | | ✅ | Zero dependencies: CDCL SAT, exact finite circuits, rational linear feasibility |

The JVM solvers are tested on Linux, macOS and Windows, and Z3 Native on Linux, macOS and Windows. The JVM solvers
need no separate installation, because their native libraries come with their Maven dependencies. The MiniZinc
routes need a MiniZinc installation. See [Backend availability](docs/AVAILABILITY.md) for the exact
platform-by-platform evidence.

## Three ways to solve

```kotlin
// 1. Batch: one result, or `solutions(model)`, a cold stream of solutions that ends with a completion event.
val result = Z3Backend().create().use { it.solve(model) }

// 2. Incremental, SMT style: assert, set a checkpoint, check under assumptions, roll back.
Z3Backend().create().use { solver ->
    solver.openSession(model).use { session ->
        val checkpoint = session.push()
        session.add(x eq 3)
        val check = session.check(assumptions = listOf(flag)) // SATISFIABLE, UNSATISFIABLE (+ unsatCore) or UNKNOWN
        session.popTo(checkpoint)
    }
}

// 3. Constraint stores, for logic engines: post constraints, propagate, ask about entailment, undo, fork.
ChocoStore().use { store ->
    val a = store.intVar("a", IntDomain.of(1..5))
    val b = store.intVar("b", IntDomain.of(1..5))
    store.constrain(a lt b)
    val mark = store.mark()
    store.constrain(b le 2)
    store.domain(a)                // propagated to {1}
    store.entails(a eq 1).value    // ENTAILED
    store.undoTo(mark)
}
```

Batch solving, incremental sessions and constraint stores are separate, capability-checked APIs. A solver only
offers what it really supports; for example, it never re-solves from scratch while pretending to be incremental.
`solveAuto` and `Portfolio` choose among the solvers available on the current platform. Ranking is extensible: a
`Portfolio` can try solvers one after another or race several in parallel. See
[Logic engines](docs/LOGIC-ENGINES.md) for the store API.

## From Java and TypeScript

Java code needs no Kotlin receivers, extension functions or coroutines (`:interop-java`):

```java
ConstraintScope scope = ConstraintScope.empty();
IntVar x = scope.intVar("x", 1, 10);
IntVar y = scope.intVar("y", 1, 10);
scope.add(Expressions.eq(Expressions.plus(x, y), 10));
scope.add(Expressions.allDifferent(x, y));
try (BlockingSolver solver = BlockingSolver.of(new ChocoBackend())) {
    SolveResult result = solver.solve(scope.build());      // also solveAsync (CompletableFuture) and solutions (Iterator)
    long vx = result.getAssignment().longValueOf(x);       // throws rather than truncates
}
```

TypeScript gets an ES module with generated declarations, `bigint` integers, `Promise`s, `AbortSignal` cancellation
and `for await` over solutions (`:interop-js`):

```ts
import { ModelBuilder, allDifferent, constant, loadZ3 } from "kt-constraints-interop-js";

const builder = new ModelBuilder();
const x = builder.intVar("x", 1n, 10n);
const y = builder.intVar("y", 1n, 10n);
builder.add(x.plus(y).eq(constant(10n))).add(allDifferent([x, y]));
const backend = await loadZ3();                       // Z3 as WebAssembly, in Node or the browser
const outcome = await backend.create().solve(builder.build());
console.log(outcome.status, outcome.solution?.valueOf(x));
backend.close();
```

Both facades are tested at their real boundary: Java sources compiled by `javac`, and the packaged npm module
type-checked by `tsc --strict` and run on Node and Chromium.

## Status: pre-release

Kt-Constraints is under active development and has **not been released yet**:

- Nothing is published on Maven Central or npm yet. Build from source as shown below.
- The public API may still change between commits.
- The pure-Kotlin engine passes the shared solver contracts but is experimental. It stays unpublished until its
  performance work is done.
- **Not available yet:** MiniZinc execution from Kotlin/Native, a direct HiGHS C API adapter, FlatZinc interchange,
  Gurobi, and propagating constraint stores beyond Choco.

## Getting it

You need JDK 17 or newer. To build and install every artifact into your local Maven repository:

```bash
./gradlew publishToMavenLocal
```

```kotlin
repositories { mavenLocal(); mavenCentral() }
dependencies {
    implementation("io.github.gciatto:kt-constraints-dsl:<version>")
    implementation("io.github.gciatto:kt-constraints-solve-portfolio:<version>") // solveAuto + the default solvers
}
```

The artifacts are `io.github.gciatto:kt-constraints-*`. The npm package is built by
`./gradlew :interop-js:jsNodeProductionLibraryDistribution`. [Installation](docs/INSTALLATION.md) lists every
artifact and the runtime each solver needs.

## Modules

| Module | Purpose |
|---|---|
| `:core`, `:model`, `:dsl` | Expressions, domains, global constraints, immutable models, Kotlin operators and the Java `Expressions` facade |
| `:solve`, `:solve-spi` | Solver, session, store and result APIs; the backend contract, capability checks and registry |
| `:solve-z3`, `:solve-cpsat`, `:solve-choco`, `:solve-mip`, `:solve-minizinc` | Solver adapters |
| `:solve-kotlin` | Pure-Kotlin solver (experimental, unpublished) |
| `:solve-portfolio` | `solveAuto`, default solvers per platform, fallback and race portfolios |
| `:interop-java`, `:interop-js` | Java blocking/`CompletableFuture` API; ES module with TypeScript declarations |
| `:test-core`, `:test-solve` | Shared contract suites that every backend must pass |

## Building and contributing

```bash
./gradlew check --parallel --continue          # all platforms available on this host
./gradlew jvmTest jsTest --parallel --continue # the quick loop
./gradlew :documentation:assembleSite          # docs site (needs `pip install mkdocs-material`)
```

Start with [Architecture](docs/ARCHITECTURE.md) and [Contributing](CONTRIBUTING.md). Kt-Constraints is independent
of [2p-Kt](https://github.com/tuProlog/2p-kt), although it follows that project's conventions.

## License

[Apache License 2.0](LICENSE).
