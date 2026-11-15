# M3 handoff — work stopped at the user's request

## Git and operating rules

- Completed implementation is on `develop` at `4cedcc4` and pushed to origin.
- This secondary branch snapshots unfinished documentation and this handoff. It is not a completed milestone.
- Continue implementation on `develop`; review/cherry-pick the documentation snapshot when useful.
- Never commit `PLAN.md`: it is deliberately untracked and is not transferred by this branch.
- Use TDD, separate test commits from implementation, small self-contained conventional commits, frequent pushes,
  no Codex signatures, and one central/top-level type per file.
- Develop using focused JVM tests except where an actual JS transport requires JS tests; broaden at milestone gates.
- Read AGENTS.md, docs/ARCHITECTURE.md, ROADMAP.md and docs/M3-DESIGN.md.
- Do not merge into master without fresh authorization. M2 was fast-forwarded into master before this work.
- No PR has been opened for M3 yet. Open a focused PR after finishing validation/documentation.

## Implemented and committed

- Explicit `MiniZincEngine` and JVM `MiniZincBackend`: Gecode, Chuffed, CP-SAT, HiGHS, CBC.
  The existing `MiniZincGecodeBackend` delegates and remains source-compatible.
- Selection reaches exact configuration discovery, startup probes, compilation bounds, process arguments and result identity.
- Checked symmetric ranges: Gecode/CP-SAT 2147483646; Chuffed 499999999; MIP 1000000.
  Chuffed reserves +/-500000000; actual boundary failures and succeeding adjacent values were demonstrated.
- MIP rejects variable-by-variable multiplication, requests zero absolute/relative objective gaps, and does not advertise
  exhaustive enumeration. A validated satisfaction witness plus exit 0 can establish feasibility without a wire status.
  Other completion cases remain strict. Numerical MIP proofs are not exact rational certificates.
- Shared contracts cover globals/optimization/projection against FiniteOracle. MIP explicitly disables only two enumeration cases each.
- JVM manual benchmark supports MINIZINC_BENCHMARK_ENGINE. Three self-checked raw samples for each of five engines are in docs/M3.md.
  No meaningful regression delta is claimed.
- Official npm minizinc 4.5.2, pinned in catalog/lockfile. Its browser package contains Chuffed (`org.minizinc.chuffed`), not Gecode.
- Closeable async `MiniZincJsBackend.loadBrowser(workerURL, wasmURL, dataURL)` owns the global official pool and active streams.
  Browser route is `chuffed/minizinc-wasm`; `loadNode(executable)` uses Gecode through native CLI, route `gecode/minizinc-js-cli`.
  One global runtime owner is allowed. Individual solver close leaves the runtime alive; backend close stops active streams and pool.
- Common decoder accepts an explicit execution descriptor. Assignments remain lossless output strings; JS statistics inherit upstream Number semantics.
- JS streams cover deadline, cancel, close, runtime reload and wrong-environment rejection. Deadline release was fixed to close/drain the channel
  with a backend error rather than manufacturing coroutine cancellation.
- Karma serves the official worker/Wasm/data from its own origin. Browser tests are enabled with MINIZINC_BROWSER_TEST=true.
  Browser target configuration is always declared so dependency locking is stable across enabled/disabled runs.
- Kotest can omit a setup-failed JS spec while Karma reports green. Runtime tasks now require the actual MiniZincJsSpec route-test report.
  Explicit runtime tests also disable up-to-date/build-cache reuse. Missing Node executable was demonstrated to fail.
- CI: checksum-pinned Linux MiniZinc 2.10.1 bundle, all five JVM engine suites, Node CLI suite and separate Chrome Wasm job.

## Evidence and remaining work

1. Five local JVM engines passed shared contracts and real signed-boundary tests.
2. Before the JS slice, all JVM and JS unit tests passed; static findings were fixed without adding suppressions.
3. Latest focused JS run passed Node AND browser (16 runtime leaves each), ktlintCheck and detektAll:
   `/tmp/kt-constraints-m3-js-final.log` says BUILD SUCCESSFUL in 21s. Do not rely on task success without the transport XML report.
4. macOS Native tests executed: an observed XML snapshot had 138 tests, zero failures/errors/skips.
5. The unmodified full check failed because CoreSimulator is missing, and also saw transient JS compile/style failures while JS was being developed.
   Those JS issues were corrected in focused checks. Xcode 27.2/macOS SDK works with scoped DEVELOPER_DIR; global xcode-select remains CommandLineTools.
6. IMPORTANT: the final supported full check on the committed implementation STILL FAILED. It excluded only seven iosSimulatorArm64Test tasks,
   but failed `:solve-minizinc:jsBrowserTest`: "The requested MiniZinc JS runtime contract did not execute".
   Its latest browser XML lacked MiniZincJsSpec and the Kotest Executor XML had empty stdout/stderr. The focused browser/Node/static run had passed.
   Investigate this broader/parallel-run difference; do NOT remove or weaken the report gate to get green.
7. CI run 43703944709 (older code head 5ce20c0) passed the added non-Gecode engines but failed Gecode availability.
   Inspected Linux bundle: fzn-gecode links Qt, Qt Core needs bundled ICU .73, and the IDE launcher sets LD_LIBRARY_PATH.
   Commit 4cedcc4 adds the bundle lib directory via GITHUB_ENV. This fix needs fresh CI verification; it is not yet confirmed remotely.
   The same commit adds Node/browser CI suites. Monitor the latest develop run and fix failures.
8. docs/M3.md, READMEs, ROADMAP, architecture and site index are UNFINISHED snapshots: reconcile them with final results.
   Documentation build, final complete static/JVM/JS gates, final publication POM validation and an M3 PR remain outstanding.
   No release gates or backend publication were enabled.
9. Further review concern found immediately before the user stopped work (NOT experimentally proven): the official Node SDK's cancel sends SIGINT,
   while shutdown SIGKILLs tracked CLI processes. Inspect descendant-process cleanup during backend close. Its `_run` implementation also has a
   pre-spawn-cancellation branch that emits exit without the normal `fs.rm` cleanup. Test cancellation during startup and check temp files/processes
   before claiming full Node resource safety. Do not silently ship a known lifecycle leak or paper over this with status changes.
10. The upstream event API has no backpressure; JS streams currently buffer events without a bound. This is documented with a shortcut comment.

## Commands and host details

Use JDK 21 to run Gradle; system Java 27 previously broke detekt's embedded version parser.
Old host JDK: /Users/gciatto/.gradle/jdks/eclipse_adoptium-21-aarch64-os_x.2/jdk-21.0.6+7/Contents/Home
Old host CLI: /tmp/kt-constraints-minizinc/MiniZincIDE.app/Contents/Resources/minizinc
Old host Chrome: /Applications/Google Chrome.app/Contents/MacOS/Google Chrome
Old host MkDocs venv: /tmp/kt-constraints-docs-venv/bin (a fresh --no-daemon Gradle process sees its PATH).

```sh
./gradlew ktlintFormat
./gradlew ktlintCheck detektAll --parallel --continue
MINIZINC_EXECUTABLE=/path/to/minizinc MINIZINC_ENGINES=gecode,chuffed,cp-sat,highs,cbc \
  ./gradlew jvmTest jsTest --parallel --continue
MINIZINC_BROWSER_TEST=true CHROME_BIN=/path/to/chrome \
  ./gradlew :solve-minizinc:jsBrowserTest
./gradlew check --parallel --continue
./gradlew :documentation:assembleSite --no-daemon
```

On the old Mac, scoped DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer is needed. CoreSimulator must be installed before iOS tests work.
The supported-check invocation used all real runtime variables plus `-x :<module>:iosSimulatorArm64Test` for core, model, dsl, solve, solve-spi,
test-solve and solve-minizinc. Its log is `/tmp/kt-constraints-m3-supported-check.log` (BUILD FAILED in 3m 5s due to omitted browser spec).
Scratch logs and runtime bundles are local only and will not exist on the receiving machine; the key evidence is summarized above.
