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
