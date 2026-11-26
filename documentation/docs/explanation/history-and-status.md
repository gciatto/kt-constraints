# History and status

Kt-Constraints grew from an independent modelling library into a set of solver integrations with shared execution contracts. Its conventions follow the Kotlin Multiplatform organization of 2p-Kt and kt-math, but it has no dependency on 2p-Kt. A future logic-engine integration belongs in a separate adapter.

This history follows the implemented features. Early planning documents also describe APIs and platforms that were later changed or remain unimplemented; their intended scope is broader than the current library.

## Representation before engines

The initial work established the module structure, multiplatform build, testing conventions and CI. The first modelling implementation then replaced starter machine-integer representations with exact arbitrary-precision integer literals, domain bounds and assignment values.

Finite domains became canonical unions of intervals, including sparse and empty domains. Typed Boolean and integer expressions gained structural equality, traversal and a ground evaluator. Variable identity belonged to a scope, so a reused display name could not merge unrelated declarations.

The frozen model acquired assertion metadata, source locations, integer objectives and output projection. Globals such as all-different, element and allowed-tuple tables stayed abstract. This foundation made it possible to compare translations against a common meaning instead of letting each solver define a different modelling language.

## A complete execution path through MiniZinc

The next work made solving operational: exact assignments, engine/route descriptors, whole-model support reports, explicit registration, closeable streams and separate search and termination statuses. A small finite oracle and reusable solver contracts provided independent checks for backend behavior.

Gecode through the JVM MiniZinc CLI was the first real end-to-end route. Its compiler checked integer bounds, its decoder validated full assignments before projection, and its stream owned processes and temporary files. Cancellation, timeouts and cleanup became part of correctness rather than incidental runtime details.

The MiniZinc catalogue then expanded to Chuffed, CP-SAT, HiGHS and CBC, with explicit configuration discovery and startup probes. The MIP configurations introduced a bounded affine subset and numerical proof evidence without promising exhaustive enumeration. JavaScript execution added Gecode through the Node CLI and Chuffed through browser Wasm. Work on shared runtime ownership and worker shutdown addressed the different lifetimes of a loaded backend, a solver and a running stream.

## Direct SMT and CP/MIP integrations

Direct Z3 integration added mathematical integer reasoning and SMT-style incremental sessions. Checkpoints, temporary assumptions and assumption cores received shared lifecycle and ownership rules. Common translation and execution logic was extended through ports for JavaScript Wasm and Kotlin/Native's runtime-loaded C API.

The modelling language expanded with exact rational linear reals and fixed-width bit-vectors. Keeping these sorts distinct allowed routes lacking their semantics to reject them explicitly. Integer objectives remained the optimization interface.

Direct Choco and CP-SAT adapters brought JVM CP integration, while SCIP and HiGHS through OR-Tools shared a common bounded integer-program lowering. These routes had separate numeric and cancellation policies. The distinction between an engine and its route became practical: CP-SAT through MiniZinc and CP-SAT direct could differ in supported bounds, flattening cost and global encoding.

## Language boundaries and reversible stores

Java gained a blocking solver facade, futures, iterator-based solutions and wrappers for sessions and stores. TypeScript gained an exported ES module with declarations, exact `bigint` values, promises, cancellation and asynchronous iteration. Boundary tests compiled and exercised consumers in those languages.

Constraint stores extended the library beyond fixed-model search. `SatisfiabilityStore` reused incremental checks, while `ChocoStore` exposed native propagation, narrowed domains and reversible posting. Four-valued entailment distinguished a proven undecided relationship from an engine that could not tell. Marks, late declarations, forks, residual projection and explanations supplied the operations an external logic runtime needs while leaving its unification and choice points outside the library.

## Selection and a richer modelling language

Feature extraction and explained weighted ranking made route preference inspectable. Platform bundles, `AutoBackend`, sequential fallback and bounded races built on the same eligibility checks. Portfolios retained a backend's own proof status and reported failed attempts as diagnostics.

Structural globals expanded to routing, automata, scheduling and packing. Native forms and exact decompositions supported the same abstract nodes across routes. The distinction between variable-free nested formulas and top-level auxiliary-variable encodings preserved meaning under Boolean connectives. Typed backend options added route-specific tuning without putting solver settings into the model itself.

## A common Kotlin engine and broader measurements

The pure-Kotlin engine added a route implemented in common code, reusing the representation and solver contracts. It is published for every platform and registered by default at its benchmarked position in each platform's route order. Its design and current limitations are covered in [the pure-Kotlin solver](pure-kotlin-solver.md).

Benchmarks grew from separate construction and translation observations into seeded problem families, cross-route comparisons and store-operation measurements. Measurements of the Kotlin route across JVM, Node and Native widened the comparison. The recorded runs exposed startup and flattening costs, differences between global decompositions and gaps in the default ranking policy. The experimental timing-evidence hook remains separate from the default heuristics.

## C integration and distribution

A C shared-library facade exposed a smaller modelling and solving surface through Kotlin/Native handles, with exact integer results returned as strings. An actual C consumer exercises this boundary against the Native Z3 route.

Release configuration adopted GitHub Packages for Maven and npm artifacts and release archives for the C library. Maven Central and npmjs remain separately gated distribution targets. Reserved backend modules are excluded from the published module set.

## Current status

“Implemented” here describes source functionality and its existing tests, not universal support on every route or a guarantee of API stability. Availability and numeric restrictions remain route-specific.

| Area | Status | Scope and remaining limits |
|---|---|---|
| Immutable modelling | Implemented | Typed Boolean, integer, linear real and bit-vector expressions; finite exact integer domains; metadata and projection |
| Global constraints and DSL helpers | Implemented | Routing, automata, scheduling, packing, counting and ordering constructs; native lowering or exact decomposition subject to route support |
| Batch results and streams | Implemented | Satisfaction, integer optimization, projected enumeration on capable routes, cancellation and explicit completion evidence |
| Z3 routes | Implemented | JVM, JavaScript Wasm and desktop Native; incremental checks, assumptions and cores |
| Direct JVM CP | Implemented | Choco and CP-SAT, with checked bounded integer subsets |
| Linear and mixed-integer programs | Implemented | Real objectives; exact optima from the pure-Kotlin simplex and branch and bound (checked certificates, floating-point start) and from Z3; SCIP and HiGHS in floating point on an explicit numeric tolerance; benchmark families with known optima |
| MIP through OR-Tools | Implemented, restricted | SCIP and HiGHS; bounded affine integer lowering, numerical proof statuses, no enumeration; HiGHS requires a backend time limit |
| MiniZinc execution | Partial | Five engine configurations on the JVM and on Linux and macOS Native, Node Gecode CLI and browser Chuffed Wasm; no Native Windows command transport |
| Constraint stores | Implemented, restricted | Choco native propagation on JVM and a generic satisfiability store over incremental solvers; sound non-exact residuals and non-minimal explanations |
| Java and TypeScript facades | Implemented | Real language-boundary tests; facade surfaces differ from the full Kotlin API |
| C facade | Implemented, restricted | Shared library for desktop targets; integer/Boolean modelling and batch solving through the Native defaults |
| Automatic selection (`AutoBackend`) | Implemented | Explicit platform bundles, eligibility checks, explained heuristics, fallback and races; no automatic inclusion of Kotlin or browser MiniZinc |
| Benchmark-informed ranking | Partial | Recorded measurements and an experimental evidence interface; no timing dataset in the default policy |
| Pure-Kotlin route | Implemented | Published for JVM, JS and Native with shared-contract coverage; in every default registry, ordered by benchmarks |
| Platform reach | Partial | JVM, Kotlin/JS and desktop Native integrations; iOS compilation does not provision Z3; Kotlin/Wasm targets await dependencies and binding work |
| Distribution | Partial | Release configuration for GitHub Packages and C release archives; Maven Central and npmjs publication remain planned |
| Portable model serialization | Planned | No versioned interchange format, identity remapping implementation or portable model fingerprint |
| Additional adapters | Partial | Direct HiGHS on Kotlin/Native (C API) and JavaScript (Wasm) implemented; FlatZinc interchange and Gurobi planned |
| Broader theories and optimization | Planned | Arrays, strings, sets, uninterpreted symbols, quantifiers and richer bit-vector operations are outside the current model API; nonlinear objectives, such as quadratic ones, are not supported |
| External logic-engine adapters | Planned outside this repository | Store primitives exist; no bundled logic runtime or 2p-Kt adapter |

For current route constraints, consult [solvers and routes](../reference/solvers-and-routes.md) and [platform availability](../reference/platform-availability.md). The [benchmark reference](../reference/benchmarks.md) describes recorded performance evidence; [automatic selection](backends/selection.md) distinguishes those observations from the shipped policy.
