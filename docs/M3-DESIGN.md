# M3 design: MiniZinc engine selection

The existing Gecode entry point remains source-compatible. A common `MiniZincEngine`
selects an explicit engine/configuration ID and its checked integer subset; a JVM
`MiniZincBackend` carries that selection through discovery, startup probes, compilation,
execution and result descriptors. No engine is substituted when discovery or startup fails.

The first slice covers Gecode, Chuffed and CP-SAT. Each must pass the existing shared
solver contracts and differential tests before being advertised. Discovery uses exact
configuration IDs from `--solvers-json`, followed by a real startup probe. Configuration
presence alone does not establish availability. The catalogue exposes explicit routes;
registration in a solver registry remains opt-in.

Numeric lowering checks every intermediate against the selected engine's conservative
supported range. This is a supported subset, not a claim to expose the engine's full
numeric capacity. Compiler output and independent exact assignment validation stay
shared; result identity follows the selected engine.

HiGHS/CBC require separate MIP support and numerical policies before inclusion. Browser
Wasm execution requires the official worker/wasm/data runtime plus an actual browser
smoke test. No FlatZinc parser is needed by the CLI route: MiniZinc owns flattening and
global decomposition, so an unused format implementation will not be added.

## MIP subset

HiGHS and CBC accept bounded integer/Boolean models with affine integer arithmetic;
variable-by-variable multiplication is rejected before launch. Literals, coefficients,
domains and intermediate bounds are conservatively restricted to +/-1,000,000.
MiniZinc lowers supported globals. Returned integers are parsed and validated exactly;
there is no rounding of fractional assignments. Optimality/infeasibility relies on the
MIP engine's numerical proof, with zero relative and absolute objective gap requested.
These routes do not advertise enumeration: satisfaction streams contain a witness,
and optimization streams contain improving incumbents. A validated satisfaction witness
plus clean process exit establishes feasibility even when MiniZinc emits no status;
optimization and no-witness completion still require an explicit terminal status.

## Reference evidence

- [MiniZinc CLI and solver configuration](https://docs.minizinc.dev/en/stable/command_line.html)
- [MiniZinc JSON stream](https://docs.minizinc.dev/en/stable/json-stream.html)
- [Chuffed integer sentinels](https://github.com/chuffed/chuffed/blob/develop/chuffed/vars/int-var.h)
- [Official JS runtime: browser Wasm versus Node CLI](https://github.com/MiniZinc/minizinc-js)

Direct boundary runs against bundled Chuffed 0.14.0 failed at both +/-500,000,000
with `Cannot initialise vals in unbounded IntVar`; +/-499,999,999 succeeded.
Regression tests reject the former and solve both signs of the supported boundary.

## JS transport ownership

The JS slice uses the official `minizinc` npm runtime. A closeable, asynchronously loaded
`MiniZincJsBackend` owns the package's global worker/process pool and its active streams;
only one instance may own that global runtime at once. Individual solvers own their
streams but do not shut down another solver's shared runtime. Backend close cancels all
active streams and shuts down the official runtime. Browser loading accepts explicit
worker/wasm/data URLs; Node loading accepts an executable. Initial JS execution supports
Gecode only, after discovery and a real startup solve.

Browser and Node descriptors use `gecode/minizinc-wasm` and
`gecode/minizinc-js-cli`. Both reuse the checked common compiler, exact assignment
validation and stream contracts. The Node route must never be labelled Wasm. JS messages
arrive as parsed objects from the upstream API; assignment values remain inside our
lossless output string, while statistics have upstream JavaScript number semantics.
