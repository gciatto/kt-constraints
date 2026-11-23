# Global constraints

A global constraint records a relationship among several expressions as one model node. `AllDifferent` says that all its operands differ; `Circuit` describes a tour through successor indices; `Cumulative` describes tasks sharing a bounded resource. Keeping these relationships explicit gives a compiler more choices than an early expansion into primitive comparisons.

## Structure carries useful information

Pairwise disequalities express the meaning of `AllDifferent`, but a native propagator can reason about a whole group of domains together. If more variables must choose from a set of values than that set can hold, the global relationship can expose inconsistency before a series of pairwise checks does.

The model therefore preserves globals until backend lowering. This permits native propagation, specialized engine encodings, MiniZinc's decompositions or an exact fallback formula. It also keeps the user's intended structure visible to feature extraction and diagnostics. A global node does not promise a native propagator on every route.

The representation includes dedicated nodes for all-different, element selection, allowed-tuple tables, Boolean cardinality, inverse mappings, circuits and subcircuits, cumulative scheduling, regular languages, bin packing and two-dimensional non-overlap. Integer min/max and weighted sums also preserve their operation in the expression tree. Other conveniences, including counting, lexicographic ordering, disjunctive scheduling and weighted soft constraints, are DSL helpers built from existing nodes. The [modelling reference](../reference/modelling-language.md) gives their signatures and semantics.

## Two kinds of exact decomposition

Structural globals implement `DecomposableConstraint`. Its `decomposition` is an equivalent formula that introduces no new variables. It is valid under negation, disjunction, implication and other Boolean contexts.

Its `decompose(auxiliaries)` method may instead introduce hidden variables from a fresh `VariableFactory`. Read existentially, this can be much smaller: circuit positions or automaton states can encode a relationship without expanding every possible path. The contract permits this form only at top level. Hidden variables are declared by the lowering layer and excluded from public solutions.

The restriction matters under negation. A global that means “there exists an auxiliary assignment satisfying this formula” cannot be negated by merely negating the formula while leaving the auxiliary variables existential. That would describe a different set of source-variable assignments. Translators therefore use the variable-free decomposition in nested Boolean contexts.

Decomposition preserves truth, not propagation strength or encoding size. A lossless fallback may still be much slower than a native global. Support checks can also reject a decomposition whose required arithmetic exceeds the route's bounds.

## What the adapters do

The following describes lowering choices, not an unconditional guarantee that every operand shape and numeric range is supported.

| Adapter | Lowering choices |
|---|---|
| Choco direct | Native all-different, tables, element, min/max and supported structural globals. All-different and tables can be reified natively. Structural globals use their variable-free decomposition when converted to nested Boolean literals; unsupported native edge cases also fall back. |
| CP-SAT direct | Native top-level all-different and tables; native inverse, circuit/subcircuit, cumulative, automaton and two-dimensional non-overlap where applicable. Bin packing uses its decomposition. Nested all-different becomes pairwise disequalities, tables become disjunctions of tuple matches, and structural globals use variable-free decompositions. |
| Z3, all routes | All-different maps to `distinct`, tables to disjunctions of tuple conjunctions, cardinalities to pseudo-Boolean constraints, and element/min/max to conditional expressions. Top-level structural globals can use hidden-variable decompositions; nested ones use variable-free formulas. |
| SCIP/HiGHS through OR-Tools | Shared integer-program lowering uses 0/1 variables, bounded big-M reification and exact decompositions of globals. Top-level structural globals can introduce hidden variables. Products must have a fixed factor, and generated arithmetic must pass bounded affine support checks. |
| MiniZinc routes | Emit MiniZinc globals at top level, leaving solver-specific flattening to MiniZinc. Structural globals below Boolean connectives use variable-free decompositions. Engine-specific support and bound checks still apply. |
| Pure Kotlin | Uses lossless encodings; implementation details belong in [the pure-Kotlin solver](pure-kotlin-solver.md). It does not expose a native global-propagation store. |

MiniZinc's HiGHS/CBC routes use the MiniZinc compiler path, rather than the project's OR-Tools MIP lowering. Sharing an engine name does not imply sharing a decomposition. See [engines and routes](engines-and-routes.md).

## Definedness is part of the semantics

`Element` uses zero-based indices and requires a non-empty operand array. Its index must be in range. Translators impose index definedness independently of the surrounding Boolean truth: putting an invalid selection under an otherwise true disjunction does not make that selection valid. Temporary assertions in an incremental session must keep their definedness obligations temporary as well.

Other constructors check structural conditions, such as table tuple arity, automaton determinism and positive fixed task durations or rectangle sizes. Those checks belong to the common meaning of the node; a backend may add representability checks but cannot weaken the common conditions.

When comparing solvers, translation and flattening time belong alongside search time. Preserved structure offers a choice of encodings, and measurements are needed to judge that choice for a workload. See [automatic selection](automatic-solver-selection.md) and the [benchmark reference](../reference/benchmarks.md).
