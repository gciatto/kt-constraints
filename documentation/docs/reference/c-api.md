# C API

`:interop-c` generates a Kotlin/Native shared library named `libktconstraints` and `libktconstraints_api.h`. Release ZIPs contain the library and header for `linuxX64`, `macosArm64` and `mingwX64`. This is a C facade over Boolean, integer and exact real modelling plus blocking solving through Native automatic defaults; it does not expose the entire Kotlin API.

## Names and calls

`libktconstraints_symbols()` returns a `libktconstraints_ExportedSymbols *`. Functions live under its `kotlin.root.io.github.gciatto.ktconstraints.c` member. Class methods appear inside their class member, and take the instance handle as the first argument. Constructors repeat the class name, for example `ModelBuilder.ModelBuilder()`.

Kotlin object references have generated handle types such as `libktconstraints_kref_io_github_gciatto_ktconstraints_c_ModelBuilder`; their `pinned` member is the stable pointer. Getters have names such as `Outcome.get_status`. C keyword collisions receive a trailing underscore: Boolean `and_`, `or_`, `not_`. Use the generated header from the same library build as the binary; it is the declaration authority.

## Exported surface

| Type/function | Members and behavior |
|---|---|
| `ModelBuilder` | Constructor; `intVar(name, min, max)`, `boolVar(name)`, `realVar(name)`, `add`, `minimize`, `maximize`, `minimizeReal`, `maximizeReal`, `build` |
| `IntExpression` | `plus`, `minus`, `times`, `negate`, `eq`, `neq`, `lt`, `le`, `gt`, `ge`, `toReal` |
| `RealExpression` | `plus`, `minus`, `times`, `negate`, `eq`, `neq`, `lt`, `le`, `gt`, `ge` |
| `BoolExpression` | `and_`, `or_`, `implies`, `iff`, `not_` |
| `IntExpressionList` | Constructor; `add` appends an expression and returns a handle to the list; `get_size` |
| `constant(value)` | Exact constant from Kotlin `Long` (generated `libktconstraints_KLong`) |
| `constantOf(decimal)` | Arbitrary-precision decimal integer constant; nullable handle on invalid text |
| `realConstant(decimal)` | Exact rational constant parsed from integer, fraction or decimal text (e.g. `"-3"`, `"9/2"`, `"4.5"`); nullable handle on invalid text |
| `allDifferent(list)` | Boolean pairwise distinctness constraint |
| `sum(list)` | Exact integer sum; empty sum is zero |
| `Model` | Immutable snapshot; `get_variableCount`, `get_constraintCount` |
| `solve(model, timeoutMillis)` | Blocking solve using Native defaults, `kotlin/direct`, then `highs/native` and `z3/native`, followed by enabled MiniZinc routes; non-positive timeout means no limit |
| `Outcome` | `get_status`, `get_message`, `get_route`, `get_objective`, `get_realObjective`, `valueOf`, `valueOfReal`, `truthOf` |

Variables created through `intVar` have inclusive `Long` bounds; `realVar` declares an unbounded real variable.
Constants and intermediates can exceed 64 bits. `IntExpression.toReal()` embeds an integer expression exactly.
Integer objectives use `minimize` / `maximize`; real objectives use the distinct `minimizeReal` / `maximizeReal`
names. The [LP/MILP guide](../how-to/linear-programs.md#c) quotes a tested C program for both kinds of model.

There are no C facade bit-vector expressions, projection controls, sessions, streams, stores, soft-constraint
helpers or general solver tuning. `RealExpression.times` scales exactly when either operand is constant,
and returns a null handle for a product of two variable expressions, which the real sort cannot express.

## Results and invalid input

| Result member | Contract |
|---|---|
| `status` | `FEASIBLE`, `OPTIMAL`, `INFEASIBLE`, `UNBOUNDED`, `INFEASIBLE_OR_UNBOUNDED`, `UNKNOWN`; `ERROR` when solving failed |
| `message` | Error description for `ERROR`; otherwise null |
| `route` | Answering route, or null on error |
| `objective` | Exact decimal integer string when an incumbent integer objective exists; null for real objectives |
| `realObjective` | Exact rational text (`p/q`, or `p` for integers) when an incumbent real objective exists; null otherwise |
| `valueOf(integerVariable)` | Exact decimal string; null without a value or for a compound expression |
| `valueOfReal(realVariable)` | Exact rational text; null without a value or for a compound expression, including `toReal()` |
| `truthOf(booleanVariable)` | 1 or 0; -1 without a value or for a compound expression |

Check nullable handles through `.pinned == NULL`. Duplicate/invalid variable names, invalid intervals and malformed constants return null handles. `add`, `minimize`, `maximize`, `minimizeReal` and `maximizeReal` return false for invalid scope ownership. `solve` catches failures and returns an `ERROR` outcome instead of allowing a Kotlin exception to cross into C. This does not make invalid pointers, disposed handles or arbitrary ABI misuse safe.

The C outcome does not expose termination reasons, bounds or metadata. The general meanings of mathematical statuses are in [Results and statuses](results-and-statuses.md).

## Memory and runtime ownership

Every object handle returned across the boundary owns a stable reference. Release it with the symbol table's `DisposeStablePointer(handle.pinned)` when finished, including handles returned for models, intermediate expressions, lists and fluent methods. A retained Kotlin model holds its own references to its expressions; disposing the C handle releases that boundary reference. Do not reuse a disposed handle or dispose the same returned reference twice.

Every non-null string returned from Kotlin must be released with `DisposeString`. This includes status, route, message, integer/real objective and variable-value getters. Do not release those strings with C `free`. Input strings remain caller-owned. The static symbol table is not an object handle and is not disposed as one.

Native Z3 loads at runtime from `KT_CONSTRAINTS_Z3_LIBRARY` or the loader path. The library filename defaults to `libz3.so`, `libz3.dylib` or `libz3.dll`, depending on the host. Only one Z3 library can be loaded per process. See [Configuration](configuration.md).

The compiled C boundary check is `interop-c/src/nativeUsageTest/c/usage.c`; `:interop-c:nativeUsageTest` builds every `*.c` in that directory, including the LP/MILP documentation program, with `cc -Wall -Werror` and runs it. CI executes the C consumer on Linux and macOS; Windows release asset generation is configured separately. See [Assignment from C](../tutorials/c-assignment.md) and [Platform availability](platform-availability.md).


For exhaustive Kotlin facade declarations, see the [generated API](/kt-constraints/api/index.html); use the generated `libktconstraints_api.h` from the matching build for C signatures and ABI names.
