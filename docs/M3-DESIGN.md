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
