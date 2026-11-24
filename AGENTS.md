# Kt-Constraints — repository agent instructions

## Purpose and scope

This is an independent Kotlin Multiplatform constraint programming, SMT and future CLP project. It intentionally
**does not depend on 2p-Kt**. Follow comparable source layout, factory conventions, annotations and CI practices from:
- https://github.com/tuProlog/2p-kt (`develop`)
- https://github.com/gciatto/kt-math (`master`)

Read `documentation/docs/explanation/architecture.md`, `documentation/docs/explanation/principles.md` and the status
table in `documentation/docs/explanation/history-and-status.md` before introducing public types, backend
implementation or build changes.

## Architecture invariants

1. Core `Expr`, `Sort`, `Domain`, `Variable` and `ConstraintModel` are solver-independent immutable representations.
2. `ConstraintScope` is the only short-lived mutable model builder; variable IDs, not human names, encode identity.
3. Preserve global constraints as abstract nodes and defer decomposition until backend lowering.
4. Never silently truncate integers, approximate reals, drop constraints or claim feasibility/optimality without evidence.
5. A backend engine and its execution route are distinct; CP-SAT direct and CP-SAT via MiniZinc are separate routes.
6. Batch solving, incremental assertions, and CLP propagation/backtracking expose separate capability-specific APIs.
7. Every backend must have capability/availability declarations and a shared conformance test suite.
8. Keep Kotlin commonMain free of java.io, ProcessBuilder, JNI, Kotlin/Native C types, DOM APIs and node modules.
9. JS and Java interop facades belong in dedicated modules. No common API should depend on these facades.
10. No dependency on 2p-Kt; an eventual integration is a separate, optional project or adapter.

## Conventions (modeled on 2p-Kt)

- Kotlin package prefix: `io.github.gciatto.ktconstraints`.
- One central type per file; descriptive package names; KDoc on all public declarations.
- Immutable interface-based public APIs; `of(...)`, `empty(...)`, `DEFAULT` companion factories where appropriate.
- Distinguish object equality (`==`) from symbolic constraint equality (`eq`).
- Add `@JvmStatic`, `@JsName` and deliberate overloads as language boundary issues arise; do not export internals.
- Preserve platform-specific math semantics. Kotlin `Long` is a starter finite-range representation, not an unbounded Int sort.
- Version catalog, kt-mpp `multiProjectHelper`, detekt, ktlint, Kover, git-semver, Maven Central + npm roadmap.

## Testing

- Kotlin tests: Kotest 6 `FunSpec` and Kotest assertions only; no `kotlin.test.*`, `org.junit.*` or nested `runTest`.
- Name test classes `<Subject>Spec`; use small independent leaves and typed parameterized data cases for variants.
- Build fixtures inside each test, especially identity-bearing variable scopes, sessions and solvers.
- Every backend adapter must reuse shared contracts from `:test-solve`, disabling only explicitly unsupported capability cases.
- Verify JVM, JS and available Native targets; include differential tests for any model supported by multiple backends.
- Test the actual Java and exported JS/TS API at their boundary, not only Kotlin interfaces.
- Benchmark separately: model construction, compilation, backend setup, solve, projection and checkpoint restoration.

## Tasks and build

- Wrapper: Gradle 9.9.1, synchronized with kt-math. Keep the standard wrapper scripts and JAR committed,
  and preserve the distribution checksum when upgrading.
- `./gradlew ktlintFormat` before `./gradlew ktlintCheck detektAll --parallel --continue` (avoid formatting/checking races)
- `./gradlew jvmTest jsTest --parallel --continue`
- `./gradlew check --parallel --continue`
- `./gradlew :documentation:assembleSite` (requires `pip install mkdocs-material`; builds Dokka into `api/`)
- Documentation follows `documentation/docs/`: tutorials, how-to guides, reference, explanation. Code shown on a
  page is quoted with pymdownx.snippets from tested sources (`solve-auto` tests `…portfolio.examples`,
  `interop-java` tests `…interop.examples`, `interop-js/src/test/typescript/examples`, `interop-c/src/nativeUsageTest/c`).
  Never mention roadmap milestone labels in documentation.
- `./gradlew publishAllPublicationsToProjectLocalRepository` (after publication configuration is verified)
- `./gradlew zipMavenCentralPortalPublication --dry-run` (publication metadata dry run)
- `allWarningsAsErrors=true`; do not suppress warnings merely to get CI green.

## GitHub Actions and publishing

CI is split into `dispatcher.yml`, `build-and-deploy.yml`, `test-extensively.yml`, `docs.yml`, following the
organization of the reference repositories. Release is opt-in via `vars.ENABLE_RELEASE`, only on `master`.
Do not put tokens in Gradle properties: use GitHub Actions secrets / Gradle environment properties.
Ensure group `io.github.gciatto`, prefixed artifacts `kt-constraints-*`, and correct POM metadata before release.
Do not claim modules with README-only placeholders as fully implemented solvers.

## Work order

Pick work from the status table in `documentation/docs/explanation/history-and-status.md`; cross-cutting solver
capabilities and tests precede backend code.
Reserve stable public API only after compiling Kotlin, Java and TypeScript usage examples. Keep backend wrappers
small and prefer established MiniZinc solver configurations over duplicated algorithms when semantics permit.
