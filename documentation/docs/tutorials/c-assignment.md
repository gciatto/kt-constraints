# Assign workers to tasks from C

You will assign three workers to three tasks at minimum total cost. Each worker gets exactly one
task, and each task gets exactly one worker. You need a C compiler and the Kt-Constraints shared
library for your platform. This walkthrough uses Linux x64 and `cc`.

## 1. Get the shared library

Download `kt-constraints-c-<version>-linuxX64.zip` from a
[GitHub release](https://github.com/gciatto/kt-constraints/releases) and extract it into `lib`:

```sh
unzip kt-constraints-c-<version>-linuxX64.zip -d lib
```

Replace `<version>` with the downloaded release version. The archive contains
`libktconstraints.so` and `libktconstraints_api.h`. The other release targets are `macosArm64`
and `mingwX64`; use a matching archive for those platforms.

Install a Z3 shared library and set `KT_CONSTRAINTS_Z3_LIBRARY` to its absolute path, for example:

```sh
export KT_CONSTRAINTS_Z3_LIBRARY=/absolute/path/to/libz3.so
```

The C facade loads Z3 at runtime. See [Native and Z3](../how-to/native-and-z3.md) for runtime setup.

## 2. Create the assignment program

Save this as `assignment.c`:

```c
--8<-- "interop-c/src/nativeUsageTest/c/assignment.c:program"
```

Read the `costs` array as workers by tasks. Each `assigned[w][t]` is an integer in 0..1: selecting it
adds that worker/task cost to the objective. Row sums equal to one assign each worker once;
column sums equal to one fill each task once. `minimize` selects the least expensive assignment.

`libktconstraints_symbols()` provides the generated function table. `KC` abbreviates its facade
namespace. Kotlin objects cross this boundary as handles with a `pinned` pointer; a null pointer
signals failed construction. Builder operations return `false` on invalid input. This fixed example
checks construction with C assertions, so compile it with assertions enabled.

Notice the cleanup inside the construction loops as well as at the end. Composite expressions and
lists retain the expressions they need; temporary C handles can be disposed after being added.
Even the list's fluent `add` returns another handle, which `append` immediately disposes.

## 3. Compile and run

With `assignment.c` beside the `lib` directory, run:

```sh
cc -Wall -Werror -I lib assignment.c -L lib -lktconstraints -Wl,-rpath,"$PWD/lib" -o assignment
./assignment
```

The result is:

```text
OPTIMAL: cost = 9
worker 0 -> task 1
worker 1 -> task 0
worker 2 -> task 2
```

These choices cost 2 + 6 + 1. The program checks the optimum, the actual summed cost and all row
and column assignments; it returns nonzero if any check fails.

## 4. Follow the result's lifetime

`solve(model, 10000)` blocks with a 10-second solver time limit. The program requires `OPTIMAL`
before accepting the expected answer. An `ERROR` outcome can carry a diagnostic `message`; other
statuses must also be checked before relying on a solution.

Integer values and the objective arrive as exact decimal strings. Every returned non-null string
is released with `DisposeString`; every object handle is released with `DisposeStablePointer`.
The final cleanup includes the outcome, immutable model, builder, variables, constants and lists.
Do not use disposed handles or strings.

Consult the [C API reference](../reference/c-api.md) for the exported surface and
[results and statuses](../reference/results-and-statuses.md) for interpreting other outcomes.
