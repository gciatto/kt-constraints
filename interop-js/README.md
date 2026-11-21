# :interop-js

An ES module with generated TypeScript declarations (`kt-constraints-interop-js.d.mts`), built by
`./gradlew :interop-js:jsNodeProductionLibraryDistribution` into `build/dist/js/productionLibrary`.

- **Modelling:** `new ModelBuilder()`, `intVar(name, min, max)` with `bigint` bounds, `boolVar(name)`, method-based
  expressions (`x.plus(y).eq(constant(10n))`, `a.and(b)`, `p.implies(q)`), and `sum`, `allDifferent`, `element`,
  `min`, `max`, `atMost`, `atLeast`, `exactly`. `build()` returns an immutable `Model`.
- **Solving:** `await loadZ3()` starts the z3-solver Wasm route (Node, or cross-origin-isolated browsers).
  `backend.create()` gives a `Solver`:
  - `solve(model, timeoutMillis?, solutionLimit?, signal?)` returns a `Promise<Outcome>`; aborting the `AbortSignal`
    cancels the search and rejects the promise;
  - `solutions(model, timeoutMillis?, solutionLimit?)` is an `AsyncIterable` of events, each carrying a `solution` or,
    last, the completion `outcome`; leaving `for await` early closes the search.
- **Reals** are exact: `builder.realVar(name)`, constants `real("0.1")` or `real("1/3")`, `toReal(intExpression)`,
  `scale("0.5")` for constant factors, and `solution.realValueOf(r)` returning exact `p/q` text.
- **Sessions:** `solver.openSession(model)` (routes with `INCREMENTAL_ASSERTIONS` in `backend.capabilities`) offers
  `add`, `push`/`popTo` checkpoints and `check(assumptions?, timeoutMillis?, signal?)`, whose `unsatCore` contains
  the caller's own assumption objects.
- **MiniZinc:** `loadMiniZincNode(executable?)` drives an installed CLI with Gecode; `loadMiniZincBrowser(workerURL,
  wasmURL, dataURL)` runs Chuffed from MiniZinc's browser Wasm assets (absolute URLs). One MiniZinc backend at a time.
- **Integers** are `bigint` both ways, so no value is rounded through `number`. `Outcome.status` and
  `Outcome.termination` are the Kotlin enum names as strings.

```ts
import { ModelBuilder, allDifferent, constant, loadZ3 } from "kt-constraints-interop-js";

const builder = new ModelBuilder();
const x = builder.intVar("x", 1n, 10n);
const y = builder.intVar("y", 1n, 10n);
builder.add(x.plus(y).eq(constant(10n))).add(allDifferent([x, y]));
const backend = await loadZ3();
const solver = backend.create();
const outcome = await solver.solve(builder.build());
console.log(outcome.status, outcome.solution?.valueOf(x));
backend.close();
```

In browsers, Z3 needs cross-origin isolation (`Cross-Origin-Opener-Policy: same-origin`,
`Cross-Origin-Embedder-Policy: require-corp`), `z3-built.js` loaded as a classic script before the bundle, with
`z3-built.wasm` beside it, and the bundler mapping Node's `global` to `globalThis` (esbuild: `--define:global=globalThis`).

`src/test/typescript` is the executable reference. `checks.mts` holds backend-independent checks, which both
entries run:
- `node.mts` runs them on Z3, plus the MiniZinc CLI when `MINIZINC_EXECUTABLE` is set. `jsTest` installs the
  packaged library as an npm consumer would, type-checks with `tsc` in strict mode and runs this entry.
- `browser.mts` runs them in Chromium on Z3 and Chuffed. Run it with
  `INTEROP_BROWSER_TEST=true CHROME_BIN=... ./gradlew :interop-js:typescriptBrowserUsageTest`.

Constraint stores: `backend.openStore()` returns a `Store` (Z3 Wasm only; other routes refuse) with `intVar`,
`constrain`, `mark`/`undoTo`, `entails`, `explain` (returning the caller's own expressions), `domain` and `fork`.

Not yet exposed: bit-vector expressions and store residual projection. The declarations also list kt-math's own exported number API, which this
facade does not need.
