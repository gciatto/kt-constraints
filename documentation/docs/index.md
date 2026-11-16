# Kt-Constraints

Kotlin Multiplatform constraint modelling with backend-independent solving contracts.

The experimental Boolean/integer model and resource-safe solving contracts are implemented.
JVM MiniZinc routes execute Gecode, Chuffed, CP-SAT, HiGHS and CBC. HiGHS/CBC support a
bounded affine integer subset and do not enumerate all satisfying assignments.
Direct OR-Tools CP-SAT (`cp-sat/direct`) runs on the JVM within a conservative int64 margin.
The official JS package provides Node/Gecode CLI and browser/Chuffed Wasm execution with
shared runtime contracts. Direct Z3 on the JVM, Wasm (Node and isolated browsers) and desktop Kotlin/Native adds unbounded exact integers, exact linear reals, bit-vectors and incremental sessions
with checkpoints, assumptions and unsat cores. Other direct adapters and Native execution remain planned.

Read the [roadmap](roadmap.md) for milestone definitions, and [architecture](architecture.md) for the execution model.
