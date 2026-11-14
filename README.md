# Kt-Constraints

**Solver-independent constraint modelling for Kotlin Multiplatform, with planned CP, SMT and CLP support.**

[Architecture](docs/ARCHITECTURE.md) · [Roadmap](ROADMAP.md) · [Contributing](CONTRIBUTING.md) · [Releasing](docs/RELEASING.md)

[Bootstrap verification](docs/BOOTSTRAP.md) records build, wrapper, publication and platform evidence.
[M1 validation](docs/M1.md) records the exact finite-domain modelling foundation and its API changes.

## Project status

Early **repository bootstrap / P0**, not a released constraint solver. The `:core`, `:model`, and `:dsl` modules
contain an exact Boolean/integer algebra, finite domains, global nodes, a ground evaluator and a model builder,
with shared Kotest property contracts. The backend
modules are intentionally empty integration slots; **Z3, MiniZinc, Choco, CP-SAT, HiGHS and FlatZinc do not yet run**.

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
| Compiler routes | `:solve-minizinc`, `:solve-flatzinc` | Placeholder modules |
| Direct solver adapters | `:solve-z3`, `:solve-choco`, `:solve-cpsat`, `:solve-highs` | Placeholder modules |
| Orchestration | `:solve-portfolio` | Placeholder module |
| Language interoperability | `:interop-java`, `:interop-js` | Placeholder modules |
| Testing and documentation | `:test-core`, `:test-solve`, `:documentation` | Shared representation contracts / solver-contract slot / MkDocs |

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

Solving is **not yet implemented**. See [ROADMAP.md](ROADMAP.md) for exact milestones and acceptance criteria.

Integer constants and finite-domain bounds use multiplatform kt-math `BigInteger`; `Int` and `Long` are
convenience inputs. Domains support empty sets, sparse values, unions and intersections without enumerating
intervals. The core retains `AllDifferent`, zero-based `Element`, allowed-tuple `Table`, `LinearSum` and
Boolean cardinality nodes for later backend lowering. `GroundEvaluator` checks constant expressions only;
it is not a solver. Models support named assertions, annotations, source locations and output projection.

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
