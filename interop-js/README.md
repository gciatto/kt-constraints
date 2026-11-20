# :interop-js

An ES module with generated TypeScript declarations (`kt-constraints-interop-js.d.mts`), built by
`./gradlew :interop-js:jsNodeProductionLibraryDistribution` into `build/dist/js/productionLibrary`.

- **Modelling:** `new ModelBuilder()`, `intVar(name, min, max)` with `bigint` bounds, `boolVar(name)`, method-based
  expressions (`x.plus(y).eq(constant(10n))`, `a.and(b)`, `p.implies(q)`), and `sum`, `allDifferent`, `element`,
  `atMost`, `atLeast`, `exactly`. `build()` returns an immutable `Model`.
- **Solving:** `await loadZ3()` starts the z3-solver Wasm route (Node, or cross-origin-isolated browsers).
  `backend.create()` gives a `Solver`:
  - `solve(model, timeoutMillis?, solutionLimit?, signal?)` returns a `Promise<Outcome>`; aborting the `AbortSignal`
    cancels the search and rejects the promise;
  - `solutions(model, timeoutMillis?, solutionLimit?)` is an `AsyncIterable` of events, each carrying a `solution` or,
    last, the completion `outcome`; leaving `for await` early closes the search.
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

`src/test/typescript/usage.mts` is the executable reference: `jsTest` installs the packaged library as an npm
consumer would, type-checks the file with `tsc` in strict mode and runs it on Node.

Not yet exposed: real and bit-vector expressions, incremental sessions, and the MiniZinc JS routes. The declarations
also list kt-math's own exported number API, which this facade does not need.
