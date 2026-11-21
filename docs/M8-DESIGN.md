# M8 design: automatic selection, portfolios, benchmarks and release

Status: **accepted**. Decisions settled on 2026-11-21:
- feature-based, extensible ranking that accounts for platform and capabilities;
- an internal hook for benchmark-driven ranking later;
- fallback and race portfolios;
- prepare the release, then ask before merging or publishing;
- publish to Maven Central and npm.

## Automatic selection (`:solve-spi`)

Selection runs in three stages:

1. **Eligibility filtering** (exists today in `SolverRegistry.select`), with rejection diagnostics. A backend is
   rejected if it:
   - lacks a capability the model needs;
   - is unavailable at runtime;
   - reports part of the model as unsupported.
2. **Ranking.** A `RankingPolicy` scores each eligible backend and sorts by total score. Ties keep registration
   order, so the result is deterministic.
3. **Explanation.** The result is a `Selection`:
   - the ranked candidates, each with its per-heuristic contributions;
   - the rejected backends, with their reasons.

   `select` still returns the top candidate, for callers that need nothing more.

### Inputs

- **`ModelFeatures`** is computed once per model by a single expression walk:
  - sorts used (Boolean, integer, real, bit-vector);
  - operations: non-linear products, element, table, allDifferent, cardinality, reification depth;
  - objective kind;
  - counts of variables, constraints and nodes;
  - the largest integer magnitude.

  It also derives the `ProblemClass` used to key benchmark evidence, for example `LINEAR_INTEGER`, `CP_GLOBALS`,
  `SMT_REAL`, `SMT_BITVECTOR` or `PURE_BOOLEAN`.
- **`Platform`**: runtime (`JVM`, `NODE`, `BROWSER`, `NATIVE`) and operating system. It is detected through
  `expect`/`actual` declarations and can be overridden in tests.
- **`SolverDescriptor`**: the engine, the route and the declared capabilities.

### Heuristics

`fun interface RankingHeuristic { fun score(features, descriptor, platform): Score? }`
- A `Score` is a number plus a one-line reason. Null means the heuristic has no opinion.
- A `RankingPolicy` is an ordered list of weighted heuristics. `RankingPolicy.DEFAULT` combines the built-ins
  below. Callers can append, reweight or replace heuristics without touching the library.

Built-in heuristics:
- **Capability fit.** Prefers backends whose extra capabilities the model does not need, a cheap proxy for
  "specialised for this model".
- **Feature affinity.** A small table:
  - reals and bit-vectors favour SMT routes;
  - linear integer models with a linear objective favour MIP routes;
  - globals and reification favour CP routes;
  - pure Boolean models favour SAT-style CP (CP-SAT).
- **Route overhead.** Prefers in-process direct routes to compiler or subprocess routes (MiniZinc). On the
  platform where a Wasm route and a native route compete, prefers the native one.
- **Evidence** (the benchmark hook). Asks a `PerformanceEvidence` source for the expected cost of
  `(ProblemClass, route id, model size)` and converts it into a score. `PerformanceEvidence.NONE`, the default,
  always answers null, so this heuristic changes nothing until benchmark data is supplied. The interface is
  `@ExperimentalKtConstraintsApi` until real data shapes it.

## Default registries (`:solve-portfolio`)

`DefaultBackends.forCurrentPlatform()` lists the routes bundled for each platform:
- JVM: Choco, CP-SAT, Z3, SCIP, HiGHS, and MiniZinc when an executable is configured;
- JS: Z3 Wasm and MiniZinc, loaded asynchronously;
- Native: Z3.

`Auto.select(model)` and `Auto.solve(model)` apply the default policy to that list.

## Portfolios (`:solve-portfolio`)

- **Fallback.** Tries the ranked candidates one at a time, moving on only when a result is `UNKNOWN`, the
  backend is unavailable, or it reports part of the model unsupported. The first definitive result wins. A
  timeout never counts as a proof.
- **Race.** Runs up to `parallelism` candidates concurrently under one deadline:
  - The first *proof* wins and cancels the rest: `INFEASIBLE`, `OPTIMAL`, or `FEASIBLE` for satisfaction.
  - Optimization keeps the best validated incumbent across backends. Optimality is claimed only from a
    backend's own proof.
  - A backend that fails or errors is reported in the result's metadata and never treated as evidence.
- Both modes produce an ordinary `SolveResult` whose metadata records which route answered and why the others
  were skipped.

## Benchmarks (manual, outside `check`)

- **Families:**
  - n-queens;
  - graph colouring;
  - pigeonhole (UNSAT);
  - knapsack;
  - assignment;
  - a small job shop;
  - bit-vector SMT;
  - integer linear programming.
- Each run separates model building, compilation or translation, cold start (first solve on a fresh backend)
  and warm solve.
- Each run also measures overhead against using the engine directly where that is possible, and checks
  correctness (status and objective against known answers).
- Output is CSV with one row per (family, size, route), the format future `PerformanceEvidence` will read.
- No regression thresholds until several reproducible baselines exist.

## Release preparation

- **Docs and notes:** API docs (Dokka), changelog, release notes, examples, and refreshed `AGENTS.md` and
  `ROADMAP.md`.
- **Maven:** validate the POM metadata and transitive dependencies, and run the publication dry run.
- **npm:** package `kt-constraints-interop-js` with correct `exports`, a license and a version from git-semver;
  publish through `NPM_TOKEN` in GitHub Actions only.
- **Handover:** open a release PR from `develop` to `master`, then stop. Merging, `ENABLE_RELEASE` and
  `ENABLE_PAGES` wait for explicit approval.
