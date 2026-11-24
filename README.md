# Kt-Constraints

**One typed constraint model for Kotlin, Java, TypeScript and C, solved by Z3, CP-SAT, Choco, SCIP, HiGHS,
MiniZinc or a pure-Kotlin engine on the JVM, Node, the browser and native targets, with exact arithmetic throughout.**

[![CI](https://github.com/gciatto/kt-constraints/actions/workflows/dispatcher.yml/badge.svg)](https://github.com/gciatto/kt-constraints/actions/workflows/dispatcher.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF.svg)
[![Release](https://img.shields.io/github/v/release/gciatto/kt-constraints)](https://github.com/gciatto/kt-constraints/releases)

Kt-Constraints is a Kotlin Multiplatform library for constraint programming and SMT. You describe a problem once,
as an immutable, solver-independent model. The library then hands it to whichever solver suits the problem and the
platform, and reports which solver answered and what it actually proved.

**[Documentation](https://gciatto.github.io/kt-constraints/)**: tutorials for each language, a cookbook of classic
problems, the full reference with the [API docs](https://gciatto.github.io/kt-constraints/api/), and how it all works inside.

```kotlin
import io.github.gciatto.ktconstraints.dsl.allDifferent
import io.github.gciatto.ktconstraints.dsl.eq
import io.github.gciatto.ktconstraints.dsl.gt
import io.github.gciatto.ktconstraints.dsl.plus
import io.github.gciatto.ktconstraints.dsl.times
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.auto.AutoBackend
import io.github.gciatto.ktconstraints.solve.spi.withSolver

suspend fun main() =
    ConstraintScope.empty {
        val x = intVar("x", 1..9)
        val y = intVar("y", 1..9)
        val z = intVar("z", 1..9)
        add(allDifferent(x, y, z))
        add(x + y eq z * 2)
        add(x gt y)
        maximize(x * y)

        // The automatic backend ranks the solvers bundled for this platform and falls back on failure.
        val result = AutoBackend.load().use { auto -> auto.withSolver { it.solve(buildModel()) } }
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
  desktop targets, with idiomatic APIs for Kotlin, Java and TypeScript, plus a C shared library.
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
| Pure Kotlin (`kotlin/direct`) | ✅ | ✅ | ✅ | ✅ | Zero dependencies: CDCL SAT, exact finite circuits, rational linear feasibility |

The JVM solvers are tested on Linux, macOS and Windows, and Z3 Native on Linux, macOS and Windows. The JVM solvers
need no separate installation, because their native libraries come with their Maven dependencies. The MiniZinc
routes need a MiniZinc installation. See [Backend availability](https://gciatto.github.io/kt-constraints/reference/platform-availability/) for the exact
platform-by-platform evidence.

## How fast is it?

Median milliseconds to solve a fresh instance, setup included, on one Linux x64 host. These are the medium-size
(scale 6) instances of the project's route benchmark, with 3 seeds and a 10 s timeout; every answer matched the
known optimum or proof.

| Family | JVM CP-SAT | JVM Choco | JVM Z3 | JVM SCIP | JVM HiGHS | JVM Kotlin | Node Z3 Wasm | Node Kotlin | Native Z3 | Native Kotlin |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 28-queens | 143 | 33 | 7419 | 6231 | 3856 | 21 | 4830 | 106 | 7492 | 50 |
| Pigeonhole (infeasible) | 3 | 0 | timeout | timeout | timeout | 1 | timeout | 2 | timeout | 2 |
| Knapsack | 5 | 15 | 17 | 6 | 8 | 7 | 81 | 22 | 23 | 7 |
| Assignment | 17 | 9 | 410 | 1735 | 2328 | 117 | 1743 | 209 | 433 | 153 |
| Job-shop | 9 | 2 | 10 | 9 | 18 | 4 | 41 | 9 | 15 | 3 |
| Single-machine scheduling | 31 | timeout | timeout | timeout | 10000* | 10000* | timeout | timeout | timeout | 10000* |

`*`: some seeds timed out.

- **JVM:** CP-SAT is the all-rounder, and the only route that stays flat on scheduling with reified disjunctions.
  Choco is the fastest on satisfaction and assignment. Z3 and the MIP solvers lag on counting arguments and large
  `allDifferent` constraints.
- **Node:** Z3 Wasm pays a fixed cost of about 40 ms per solve. The pure-Kotlin solver is 3–100× faster on every
  family it supports.
- **Native:** the pure-Kotlin solver beats or ties Z3's C API everywhere. Z3 remains the route for models it
  rejects, such as unbounded integer domains.
- `AutoBackend.load()` does not register the pure-Kotlin solver yet: pass `KotlinBackend()` explicitly to use it.
- **MiniZinc routes** add a fixed 240–290 ms on the JVM (process start and flattening) and about 130 ms through
  Node's CLI. They pay off on models that need a specific engine, or a decomposition that MiniZinc does better.

The methodology, all families and scales, the MiniZinc engines, and the selection rules drawn from these numbers
are in the [benchmarks reference](https://gciatto.github.io/kt-constraints/reference/benchmarks/).

## Three ways to solve

```kotlin
// 1. Batch: one result, or `solutions(model)`, a cold stream of solutions that ends with a completion event.
val result = Z3Backend().withSolver { it.solve(model) }

// 2. Incremental, SMT style: assert, set a checkpoint, check under assumptions, roll back.
Z3Backend().createSolver().use { solver ->
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
`AutoBackend` is a backend like the others that chooses among the solvers available on the current platform. Ranking
is extensible, and it can try solvers one after another or race several in parallel. See
[Logic engines](https://gciatto.github.io/kt-constraints/explanation/solving-modes/) for the store API.

## From Java, TypeScript and C

Java code needs no Kotlin receivers, extension functions or coroutines (`:interop-java`, which re-exports the blocking
API of `:solve-blocking`; Kotlin code on the JVM and Native can use that module directly):

```java
ConstraintScope scope = ConstraintScope.empty();
IntVar x = scope.intVar("x", 1, 10);
IntVar y = scope.intVar("y", 1, 10);
scope.add(x.plus(y).eq(10));
scope.add(Expressions.allDifferent(x, y));
try (ChocoBackend backend = new ChocoBackend();
     BlockingSolver solver = BlockingSolver.of(backend)) {
    SolveResult result = solver.solve(scope.buildModel()); // also solveAsync (CompletableFuture) and solutions (Iterator)
    long vx = result.getAssignment().longValueOf(x);       // throws rather than truncates
}
```

TypeScript gets an ES module with generated declarations, `bigint` integers, `Promise`s, `AbortSignal` cancellation
and `for await` over solutions (`:interop-js`):

```ts
import { ModelBuilder, allDifferent, constant, loadZ3 } from "@gciatto/kt-constraints-interop-js";

const builder = new ModelBuilder();
const x = builder.intVar("x", 1n, 10n);
const y = builder.intVar("y", 1n, 10n);
builder.add(x.plus(y).eq(constant(10n))).add(allDifferent([x, y]));
const backend = await loadZ3();                       // Z3 as WebAssembly, in Node or the browser
const outcome = await backend.createSolver().solve(builder.build());
console.log(outcome.status, outcome.solution?.intValueOf(x));
backend.close();
```

C programs link a shared library with a generated header (`:interop-c`): build a model, call `solve`, and read
results back as exact decimal strings. Solving uses Z3 through its C API. See [the C facade](interop-c/README.md).

Every facade is tested at its real boundary: Java sources compiled by `javac`, the packaged npm module
type-checked by `tsc --strict` and run on Node and Chromium, and a C program compiled by `cc` against the shared
library.

## Getting it

Releases are published on GitHub:

| For | Where |
|---|---|
| Kotlin (JVM, JS, Native) and Java | [GitHub Packages](https://github.com/gciatto?tab=packages&repo_name=kt-constraints) Maven registry, `io.github.gciatto:kt-constraints-*` |
| TypeScript / JavaScript | GitHub Packages npm registry, `@gciatto/kt-constraints-interop-js` |
| C | `kt-constraints-c-<version>-<target>.zip` for Linux x64, macOS arm64 and Windows x64, attached to each [release](https://github.com/gciatto/kt-constraints/releases) |

GitHub Packages asks for a token even for public packages: a personal access token with `read:packages`.

```kotlin
repositories {
    mavenCentral()
    maven("https://maven.pkg.github.com/gciatto/kt-constraints") {
        credentials {
            username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
dependencies {
    implementation("io.github.gciatto:kt-constraints-dsl:<version>")
    implementation("io.github.gciatto:kt-constraints-solve-auto:<version>") // AutoBackend + the default solvers
}
```

```bash
echo '@gciatto:registry=https://npm.pkg.github.com' >> .npmrc
echo '//npm.pkg.github.com/:_authToken=${GITHUB_TOKEN}' >> .npmrc
npm install @gciatto/kt-constraints-interop-js
```

[Installation](https://gciatto.github.io/kt-constraints/how-to/install/) covers Maven, the C library, building from source, and the runtime each solver
needs. Publishing on Maven Central and npmjs is planned.

**Good to know:** not available yet: MiniZinc execution from Kotlin/Native, a direct HiGHS C API adapter,
FlatZinc interchange, Gurobi, and propagating constraint stores beyond Choco.

## Modules

| Module | Purpose |
|---|---|
| `:core`, `:model`, `:dsl` | Expressions, domains, global constraints, immutable models, expression operators and the Java `Expressions` facade |
| `:solve`, `:solve-spi` | Solver, session, store and result APIs; the backend contract, capability checks and registry |
| `:solve-z3`, `:solve-cpsat`, `:solve-choco`, `:solve-mip`, `:solve-minizinc` | Solver adapters |
| `:solve-kotlin` | Pure-Kotlin solver, on every platform |
| `:solve-auto` | `AutoBackend`: default solvers per platform, fallback and race strategies |
| `:solve-blocking` | Blocking solver, stream, session and store wrappers for the JVM and Native; `CompletableFuture`s on the JVM |
| `:interop-java`, `:interop-js`, `:interop-c` | Java number conversions plus `:solve-blocking`; ES module with TypeScript declarations; C shared library |
| `:test-core`, `:test-solve` | Shared contract suites that every backend must pass |

## Building and contributing

```bash
./gradlew check --parallel --continue          # all platforms available on this host
./gradlew jvmTest jsTest --parallel --continue # the quick loop
./gradlew :documentation:assembleSite          # docs site (needs `pip install mkdocs-material`)
```

Start with [Architecture](https://gciatto.github.io/kt-constraints/explanation/architecture/) and [Contributing](CONTRIBUTING.md). Kt-Constraints is independent
of [2p-Kt](https://github.com/tuProlog/2p-kt), although it follows that project's conventions.

## License

[Apache License 2.0](LICENSE).
