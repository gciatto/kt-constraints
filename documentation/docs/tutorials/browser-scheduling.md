# Schedule two jobs in the browser

You will schedule two jobs on two machines, minimize their finish time and display the result in a
web page. The model runs through Z3 WebAssembly. Start with Node 22 or later and the npm project and
`.npmrc` from [Sudoku from TypeScript](typescript-sudoku.md#1-install-the-package).

## 1. Create the schedule model

Job A needs machine 0 for 3 time units, then machine 1 for 2. Job B needs machine 1 for 2 time units,
then machine 0 for 1. A machine can process only one operation at a time.

Save this as `job-shop.mts`. Change the import's package name to
`@gciatto/kt-constraints-interop-js`, the published name; the tested source uses the repository's
local package name.

```typescript
--8<-- "interop-js/src/test/typescript/examples/job-shop.mts:program"
```

The two precedence constraints keep each job's operations in order. Each `disjunctive` constraint
prevents overlap on one machine. `finish` bounds both final operations, and minimizing it minimizes
the time at which both jobs are complete. All times use exact `bigint` integers.

## 2. Add a browser entry

Create `browser.mts`:

```typescript
import { jobShop } from "./job-shop.mjs";
await jobShop.run(line => document.body.append(document.createTextNode(line + "\n")));
```

Create a `site` directory and an `index.html` in it. Put `<meta charset="utf-8">` in its head and
these two script tags at the end of its body, in this order:

```html
<script src="z3-built.js"></script>
<script type="module" src="browser.js"></script>
```

To preserve the display's line breaks, add `body { white-space: pre-wrap; }` to a stylesheet or
`style` element. The first script must be a classic script: Z3's browser loader expects its global
Emscripten entry point before the application starts.

## 3. Bundle and copy the runtime assets

Install esbuild and bundle the entry as an ES module:

```sh
npm install --save-dev esbuild
npx esbuild browser.mts --bundle --format=esm --platform=browser --target=es2022 --define:global=globalThis --outfile=site/browser.js
cp node_modules/z3-solver/build/z3-built.js node_modules/z3-solver/build/z3-built.wasm site/
```

The `global` mapping is required by the browser dependency bundle. Keep `z3-built.wasm` beside
`z3-built.js`, and use both from the same installed `z3-solver` version.

## 4. Serve and inspect the result

Serve `site` through a local HTTP server that sets these response headers:

```http
Cross-Origin-Opener-Policy: same-origin
Cross-Origin-Embedder-Policy: require-corp
```

Use `http://localhost` during development or HTTPS when deployed. Serve JavaScript as JavaScript
and `.wasm` as `application/wasm`. Open the page through the server. In the browser console,
`crossOriginIsolated` must be `true`; loading the HTML directly from disk does not provide the
required environment. The repository's browser test server uses these same isolation headers.

The page prints start times for both jobs and `Finish: 5`. One optimal schedule starts A on
machine 0 and B on machine 1 at time 0; at time 3, A starts machine 1 and B starts machine 0.
The example checks non-overlap, precedence and the proven optimum before it returns.

The model and its result checks also run on Node in the repository's TypeScript usage tests. The
browser adds the bundler, loader scripts and isolation requirements. For another browser route,
see [Use MiniZinc](../how-to/use-minizinc.md); for the available scheduling constraints, see
[the modelling reference](../reference/modelling-language.md) and
[global constraints](../explanation/global-constraints.md).
