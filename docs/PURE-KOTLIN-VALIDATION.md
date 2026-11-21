# Pure Kotlin implementation: validation record

Recorded for the `feat/pure-kotlin-solver` delivery on **2026-11-21**.

## Result

Reapplied onto `develop` (81255aa) and executed on 2026-11-21. ktlint and detekt pass, and these suites pass:

| Suite | Tests |
|---|---|
| `:solve-kotlin:jvmTest` | 165 |
| `:solve-kotlin:jsNodeTest` | 166 |
| `:solve-kotlin:linuxX64Test` | 165 |
| `:interop-java:test` (includes `KotlinJavaUsageSpec`) | 28 |
| `:interop-js:jsNodeTest` (includes `KotlinFacadeSpec`) | 7 |
| Repository-wide `jvmTest jsTest` | all green |

Execution found and fixed: commit ordering (the translator preceded its integer circuits), a stream that polled
a coroutine captured by an earlier pull (every second `next()` from a new caller failed), the missing
`IncrementalSession.declare`, the globals added to `develop` after the original base, compiler warnings and one
detekt finding. macOS, Windows and Chromium cells await CI. The module stays outside `publishedModules`;
no benchmark timings are claimed.

### Original delivery

The original delivery could not download the pinned Gradle distribution, so none of the following was executed
there; it is kept as provenance.

## Repository provenance

| Item | Value |
|---|---|
| Upstream | `https://github.com/gciatto/kt-constraints.git` |
| Base branch | `develop` |
| Pinned base commit | `d24befeeeeec47d24171196f24f6098210893e9e` |
| Pinned base tree | `145c09025e75c9b4f6863e9adfb284becbd1d951` |
| Feature branch | `feat/pure-kotlin-solver` |
| External dependency/version changes | None |
| Gurobi/proprietary binary analysis | None |

Direct Git transport was unavailable, so the 350 upstream files were retrieved through authenticated GitHub
file access. Every file's Git blob ID, the reconstructed tree ID and the original head commit ID were checked
against upstream metadata. The repository therefore starts at the exact upstream commit, not an unrelated
import commit.

The ZIP contains a normal working tree and a self-contained `.git` directory. `develop` points at the pinned
base; the feature branch is checked out. All feature commits are included. The upstream base is shallow, so
earlier upstream ancestors are not included. They can be obtained with `git fetch --unshallow origin develop`.

## Executable validation attempted

The unchanged wrapper is Gradle **9.9.1**, with its upstream distribution checksum. The unchanged version
catalog pins Kotlin **2.4.21**. Java 21 is present; Gradle, Kotlin and test-library caches are absent.

Final formatting attempt:

```bash
./gradlew ktlintFormat --no-daemon
```

Final platform and boundary test attempt:

```bash
./gradlew :solve-kotlin:jvmTest :solve-kotlin:jsNodeTest \
  :solve-kotlin:linuxX64Test :interop-java:test :interop-js:jsNodeTest \
  --continue --no-daemon
```

Both returned exit code 1 during wrapper bootstrap, before project configuration:

```text
Fetching distribution.
Downloading https://services.gradle.org/distributions/gradle-9.9.1-bin.zip

Attempt 1/1 failed. Reason: Network is unreachable
Exception in thread "main" java.net.SocketException: Network is unreachable
```

Earlier component-specific SAT, arithmetic, compiler and facade attempts hit the same blocker. No alternate
compiler or dependency version was substituted. Consequently there is no passing or failing Kotlin test count
to report, and source review is not a substitute for compiler or runtime results.

## Source and repository checks completed

- `git diff --check` reported no whitespace errors.
- `git fsck --full` reported no repository object errors.
- All 72 added Kotlin files contain one top-level central type; none has a source line longer than 160 columns.
- New common solver sources contain no platform/native solver imports or banned test-framework imports.
- Test commits are separated from implementation, fix, performance, refactoring, build and documentation commits.
- Independent source reviews covered signed widths/products/shifts, strict rational elimination, integer
  presolve, theory-clause dependencies, scoped definedness, incremental lifetimes and objective bounds.
- Specific review findings were fixed with regression sources: mutable assumption snapshots, interruption
  recovery, allocation during integer embedding and discrete-only real-predicate lowering.

These are static checks. Compiler diagnostics, generated Java/JS signatures, target-specific behaviour,
runtime cancellation latency and measured resource use still require execution.

## Test-first development record

The history retains separate conventional commits for tests and implementation. Examples include:

| Behaviour specified first | Subsequent implementation |
|---|---|
| Incremental SAT truth tables and assumptions | Watched propagation and CDCL |
| Exact strict rational feasibility and witnesses | Fourier–Motzkin elimination |
| Domain restriction, rollback and independently enumerated affine models | Reversible domains and root presolve |
| Backend/shared contracts, mixed exact arithmetic and scoped definedness | Compiler, engine, sessions and streams |
| Exact integer/BV circuit truth tables and incorrect-output refutations | Signed arithmetic and fixed-width circuits |
| Caller mutation after the first cooperative suspension | Snapshot assumptions before suspension |
| Wide integer embeddings, cancellation and retry | Single-pass coefficient accumulation |
| Rational comparisons over large discrete integers | Positive-LCM scaling and exact SAT inequalities |

Because the build could not start, the **execution of the TDD red/green cycle is incomplete**. Test-first source
ordering is preserved without presenting unexecuted specifications as successful tests.

## Reproduction after extraction

From the extracted repository:

```bash
git status --short --branch
git log --oneline develop..feat/pure-kotlin-solver
git diff --stat develop..feat/pure-kotlin-solver

./gradlew ktlintFormat
./gradlew ktlintCheck detektAll --parallel --continue
./gradlew :solve-kotlin:jvmTest :solve-kotlin:jsNodeTest \
  :interop-java:test :interop-js:jsNodeTest --continue
./gradlew :solve-kotlin:linuxX64Test
./gradlew :solve-kotlin:jvmBenchmark
```

Run `:solve-kotlin:macosArm64Test` and `:solve-kotlin:mingwX64Test` on their corresponding hosts. The existing
Native CI matrix now includes the module. JVM/JS common tests are unconditional; there is no solver-binary
availability flag that can silently skip this backend's contracts.

The JavaScript boundary tests use `solve-kotlin` only on the facade's test classpath. The published facade
factories remain unchanged, so this delivery does not claim a packaged TypeScript pure-backend loader.
The manual benchmark is outside routine `check`; it prints raw phase timings and checks witness/restoration
correctness, with no statistical or cross-solver performance claim.

The scope, resource limits and remaining algorithmic work are recorded in [the design](PURE-KOTLIN.md) and
[module README](../solve-kotlin/README.md).
