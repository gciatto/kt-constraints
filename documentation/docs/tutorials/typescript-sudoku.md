# Solve Sudoku from TypeScript

You will solve a small Sudoku on Node using Z3 WebAssembly. This 4×4 puzzle uses digits 1–4 and
2×2 boxes, so you can inspect every constraint and the result. You need Node 22 or later.

## 1. Install the package

Create an empty project directory and run `npm init -y`. In that directory create `.npmrc`:

```ini
@gciatto:registry=https://npm.pkg.github.com
//npm.pkg.github.com/:_authToken=${GITHUB_TOKEN}
```

Set `GITHUB_TOKEN` in your shell to a GitHub token with `read:packages`, then install:

```sh
npm install @gciatto/kt-constraints-interop-js
npm install --save-dev typescript
```

For a repeatable application build, choose a release version and commit your lockfile.
The package includes TypeScript declarations and depends on `z3-solver`; no Z3 executable is needed.
See [Install](../how-to/install.md) for other environments.

## 2. Build and solve the puzzle

Save the following as `sudoku.mts`. **Change its import from `kt-constraints-interop-js` to
`@gciatto/kt-constraints-interop-js`.** The unscoped name in the tested source is the repository's
local package name; GitHub Packages publishes the scoped name.

```typescript
--8<-- "interop-js/src/test/typescript/examples/sudoku.mts:program"
```

The zeroes in `clues` mark empty cells. `ModelBuilder.intVar` takes `bigint` bounds, and
`allDifferent` constrains each row, column and box. Each nonzero clue adds symbolic equality
with an exact `bigint` constant.

The exported example includes checks of all groups and clues. Its `finally` block releases both
the solver and the loaded backend, including when solving or validation fails.

## 3. Run it on Node

Append this line to `sudoku.mts`:

```typescript
await sudoku.run(console.log);
```

Compile and run:

```sh
npx tsc sudoku.mts --target es2022 --module nodenext --moduleResolution nodenext --strict --outDir out
node out/sudoku.mjs
```

You should see:

```text
1 2 3 4
3 4 1 2
2 1 4 3
4 3 2 1
```

`solution.valueOf(cell)` returns `bigint`, and `String` converts it for display. Keep values as
`bigint` when doing integer calculations; converting them through `number` can lose precision.
The satisfaction solve reports `FEASIBLE`; it does not prove that the puzzle has a unique solution.

Next, try [scheduling in the browser](browser-scheduling.md),
[enumerate solutions](../how-to/enumerate-solutions.md), or consult the
[TypeScript API reference](../reference/typescript-api.md).
