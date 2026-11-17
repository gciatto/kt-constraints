# Kt-Constraints

**Solver-independent constraint modelling for Kotlin Multiplatform, with planned CP, SMT and CLP support.**

[Architecture](docs/ARCHITECTURE.md) · [Roadmap](ROADMAP.md) · [Contributing](CONTRIBUTING.md) · [Releasing](docs/RELEASING.md)

[Bootstrap verification](docs/BOOTSTRAP.md) records build, wrapper, publication and platform evidence.
[M1 validation](docs/M1.md) records the exact finite-domain modelling foundation and its API changes.
[M2 validation](docs/M2.md) records resource-safe solving, real Gecode execution and measured phases.
[M3 validation](docs/M3.md) covers the engine catalogue, Node CLI and browser Wasm transports.
[M4 design](docs/M4-DESIGN.md) covers direct Z3 and incremental sessions (in progress).

## Project status

Experimental **P0 / M5 implementation (in progress)**, not a stable release. The `:core`, `:model`, and `:dsl` modules
contain an exact Boolean/integer algebra, finite domains, global nodes, a ground evaluator and a model builder,
with shared Kotest property contracts. JVM MiniZinc routes execute Gecode, Chuffed, CP-SAT, HiGHS and CBC with shared
conformance tests. MIP routes expose a bounded affine integer subset and do not enumerate all solutions.
The official JS package runs Gecode through Node's CLI and Chuffed through browser Wasm.
Direct Z3 (`z3/direct` on the JVM, `z3/wasm` on Node and isolated browsers, `z3/native` through the C API on desktop Kotlin/Native) solves the same IR with unbounded exact integers, exact linear reals and fixed-width bit-vectors and offers
incremental sessions with checkpoints, assumptions and unsat cores.
Direct OR-Tools CP-SAT (`cp-sat/direct`, JVM) solves the Boolean/integer IR within a conservative int64 margin
with native globals, callback enumeration and proven optima.
Direct Choco (`choco/direct`, JVM) solves it within `±10^7` with natively reified globals and lazy enumeration.
SCIP and HiGHS through OR-Tools (`scip/or-tools`, `highs/or-tools`, JVM) solve it within `±10^6` through an exact
linear lowering; HiGHS solves are always bounded by a backend limit, since OR-Tools cannot interrupt them.
**A direct HiGHS C API adapter, Gurobi, Choco propagating sessions, FlatZinc interchange and Native MiniZinc execution remain planned**.

## Design

* Independent from [2p-Kt](https://github.com/tuProlog/2p-kt): no direct dependency, no Prolog-specific semantics.
* Follows its modular Kotlin multiplatform design, scope factories, explicit solver interfaces and test contracts.
* Central immutable, typed expression and model representation, distinct from backend-specific encodings.
* MiniZinc/FlatZinc compilation route + direct solvers; support for the same engine through multiple routes.
* CP batch solving, SMT-style incremental sessions, and eventual propagation/backtracking CLP sessions are distinct capabilities.
* Kotlin-native modelling DSL, plus dedicated Java and JavaScript/TypeScript facades.
* Capability-checked rather than pretending every backend supports all sorts or operational modes.

## Modules

| Category | Modules | Status |
|---|---|---|
| Representation | `:core`, `:model`, `:dsl` | Exact integer/Boolean IR, finite domains, globals and immutable models |
| Solver contracts | `:solve`, `:solve-spi` | Exact results/assignments and explicit backend eligibility; execution in progress |
| Compiler/runtime route | `:solve-minizinc` | Portable checked compiler and five JVM engines, Node CLI and browser Chuffed Wasm |
| FlatZinc interchange | `:solve-flatzinc` | Placeholder module |
| Direct solver adapters | `:solve-z3` | JVM, Wasm (Node, isolated browsers) and desktop Native Z3: batch and incremental |
| Direct solver adapters | `:solve-cpsat` | JVM OR-Tools CP-SAT: batch satisfaction, enumeration and optimization |
| Direct solver adapters | `:solve-choco` | JVM Choco: batch satisfaction, enumeration and optimization |
| Direct solver adapters | `:solve-mip` | JVM SCIP and HiGHS through OR-Tools: batch satisfaction and optimization |
| Direct solver adapters | `:solve-highs` | Placeholder module for the direct HiGHS C API |
| Orchestration | `:solve-portfolio` | Placeholder module |
| Language interoperability | `:interop-java`, `:interop-js` | Placeholder modules |
| Testing and documentation | `:test-core`, `:test-solve`, `:documentation` | Shared contracts, capped finite test oracle and MkDocs |

## Minimal Kotlin modelling example (P0)

```kotlin
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.dsl.eq
import io.github.gciatto.ktconstraints.dsl.plus

val model = ConstraintModel.of {
    val x = intVar("x", 1..10)
    val y = intVar("y", 1..10)
    add(x + y eq 10)
    maximize(x)
}

println(model.variables.size)   // 2
println(model.constraints.size) // 1
```

JVM solving uses `MiniZincBackend(MiniZincEngine.CHUFFED, executable)` for explicit engine selection;
`MiniZincGecodeBackend(executable)` remains available. Install a matching MiniZinc solver configuration. The adapter is
experimental and remains unpublished while packaging is finalized. See [ROADMAP.md](ROADMAP.md).

Integer constants and finite-domain bounds use multiplatform kt-math `BigInteger`; `Int` and `Long` are
convenience inputs. Domains support empty sets, sparse values, unions and intersections without enumerating
intervals. The core retains `AllDifferent`, zero-based `Element`, allowed-tuple `Table`, `LinearSum` and
Boolean cardinality nodes for later backend lowering. `GroundEvaluator` checks constants or complete typed bindings;
it is not a solver. Models support named assertions, annotations, source locations and output projection.

Streams distinguish solutions/incumbents from one definitive completion event. Close and cancellation release
their processes and temporary files; timeout/limit outcomes retain incumbents without inventing optimality.
The finite oracle in `:test-solve` is only an unpublished test utility, capped at 10,000 candidates and 64 variables.

```bash
# Real JVM integration tests; normal tests explicitly skip these when the variable is absent.
MINIZINC_EXECUTABLE=/path/to/minizinc MINIZINC_ENGINES=gecode,chuffed,cp-sat,highs,cbc \
  ./gradlew :solve-minizinc:jvmTest
# Actual browser Wasm contracts (Chrome required); Node CLI is tested by jsNodeTest with the executable variable.
MINIZINC_BROWSER_TEST=true ./gradlew :solve-minizinc:jsBrowserTest
# Manual raw phase benchmark, excluded from routine check.
MINIZINC_EXECUTABLE=/path/to/minizinc ./gradlew :solve-minizinc:jvmBenchmark
```

## Build

Prerequisites: JDK 21+ to run Gradle 9.9.1, Python 3 with MkDocs Material for docs, Node.js for JS tests.
Apple Native tests also require full Xcode; Command Line Tools alone cannot link the test executables.

```bash
./gradlew jvmTest jsTest --parallel --continue
./gradlew check --parallel --continue
./gradlew :documentation:assembleSite
```

The repository includes the standard Gradle 9.9.1 wrapper scripts and JAR. The wrapper verifies the Gradle
distribution against the SHA-256 checksum in `gradle-wrapper.properties`. Backend placeholders remain
deliberately unpublished until functional and conformance-tested.

## Publishing coordinates

* GitHub: `https://github.com/gciatto/kt-constraints`
* Maven group: `io.github.gciatto`
* Maven artifact family: `kt-constraints-core`, `kt-constraints-model`, `kt-constraints-solve`, etc.
* Kotlin package root: `io.github.gciatto.ktconstraints`
* The coordinates do not overlap with `it.unibo.tuprolog:*` (2p-Kt) or `io.github.gciatto:kt-math`.

Releases and GitHub Pages deployment are disabled until explicitly enabled by repository variables
`ENABLE_RELEASE=true` and `ENABLE_PAGES=true`, respectively.
See [docs/RELEASING.md](docs/RELEASING.md).

## License

Apache License, Version 2.0. See [LICENSE](LICENSE).
