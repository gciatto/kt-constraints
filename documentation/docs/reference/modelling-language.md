# Modelling language

An **expression** is a symbolic operation or value; its **sort** is its value type, such as Boolean
or integer. **Linear** expressions combine variables with constant weights; **affine** expressions
may also add a constant. **AST** means abstract syntax tree: the expression tree stored by the model.

`:core` supplies solver-independent immutable expressions, whose interfaces `BoolExpr`, `IntExpr` and `RealExpr` carry the arithmetic, comparison, Boolean and conversion operators below as members. `:model` supplies snapshots and `ConstraintScope`, and `:dsl` supplies the remaining helpers below, such as `sum`, `min` and the global constraints, in `io.github.gciatto.ktconstraints.dsl`. A construct's presence here does not imply support by every [solver route](solvers-and-routes.md).

## Sorts, variables and constants

| Sort | Declaration on a scope | Constant | Semantics |
|---|---|---|---|
| `Sort.Bool` | `boolVar(name)` | `BoolLiteral(value)` | Two-valued Boolean |
| `Sort.Integer` | `intVar(name, domain)`, `intVar(name, range)`, `intVar(name, min, max)` | `IntLiteral(value)` | Mathematical integers, represented by kt-math `BigInteger`; current variables have finite domains |
| `Sort.Real` | `realVar(name)` | `RealLiteral(rational)` | Unbounded exact real variables; rational linear expressions |
| `Sort.BitVector(width)` | `bitVecVar(name, width)` | `BitVecLiteral(value, width)` | Positive width; unsigned values from zero through `2^width - 1` |

Integer literals accept `Int`, `Long` and `BigInteger`. Machine integer inputs do not narrow the mathematical sort. There is no unbounded integer-domain factory; `IntDomain.of(BigInteger, BigInteger)` accepts arbitrarily large finite bounds.

`IntDomain` is a canonical union of sorted, disjoint, non-adjacent inclusive `IntInterval`s. Factories: `of(min, max)`, `of(IntRange)`, `values(vararg Long)`, `ofIntervals(...)` and `empty()`. `union` and `intersect` preserve exact sets; `cardinality` is a `BigInteger`. An empty domain contains no possible value.

`Rational.of(...)` and `Rational.parse(...)` represent normalized fractions. Parsing accepts integer, fraction and decimal text; `Rational.parse("0.1")` denotes exactly `1/10`.

## Boolean expressions

| Node | Kotlin spelling | Meaning |
|---|---|---|
| `BoolVar`, `BoolLiteral` | Scope factory, literal constructor | Variable or truth value |
| `BoolAnd` | `p and q` | Conjunction |
| `BoolOr` | `p or q` | Disjunction |
| `BoolNot` | `!p` | Negation |
| `BoolImplies` | `p implies q` | Material implication |
| `BoolEquality` | `p eq q` | Equivalence |
| Negated equivalence | `p neq q` | Different truth values |
| `BoolToInt` | `p.asIntExpr()` | Integer 1 when true, 0 when false |

`BoolAnd.of(collection)` constructs a conjunction; the empty conjunction is true. Kotlin `==` tests object equality. Use `eq` for symbolic equality. Boolean operators build expressions without short-circuiting construction.

## Integer expressions and relations

| Node | Kotlin spelling | Meaning |
|---|---|---|
| `IntVar`, `IntLiteral` | Scope factory, literal constructor | Variable or exact integer |
| `IntSum` | `x + y` | Addition |
| `IntDifference` | `x - y` | Subtraction |
| `IntProduct` | `x * y` | Multiplication, including variable products |
| `IntNegation` | `-x` | Negation |
| `LinearSum` | `sum(*terms)`; `LinearSum(terms, coefficients, constant)` | Weighted sum plus constant; empty `sum()` is zero |
| `IntMin` | `min(first, *rest)` | Minimum of a non-empty sequence |
| `IntMax` | `max(first, *rest)` | Maximum of a non-empty sequence |
| `IntMax` with negation | `abs(x)` | Absolute value |
| `IntEquality` | `x eq y` | Equality |
| Negated equality | `x neq y` | Disequality |
| `IntLessThan` | `x lt y`, `x gt y` | Strict comparison; `gt` reverses operands |
| `IntLessOrEqual` | `x le y`, `x ge y` | Non-strict comparison; `ge` reverses operands |
| `IntToReal` | `x.asRealExpr()` | Exact embedding into real arithmetic |

Arithmetic and comparison overloads accept `Int`, `Long` and `BigInteger` on the right. Division and remainder are not expression nodes.

## Real expressions and relations

| Node | Kotlin spelling | Meaning |
|---|---|---|
| `RealVar`, `RealLiteral` | Scope factory, literal constructor | Variable or exact rational |
| `RealSum` | `r + s` | Addition |
| `RealDifference` | `r - s` | Subtraction |
| `RealNegation` | `-r` | Negation |
| `RealScale` | `r * factor` | Multiplication by a constant rational |
| `RealEquality` | `r eq s`; `r neq s` negates it | Exact equality/disequality |
| `RealLessThan` | `r lt s`, `r gt s` | Strict comparison |
| `RealLessOrEqual` | `r le s`, `r ge s` | Non-strict comparison |

Constant overloads accept `Rational`, `Long` or `Int`. There is no product of real variables, implicit integer embedding or floating-point overload. `minimize` and `maximize` also accept a real expression; such a real objective reports exact rational values (`realObjectiveValue`, `realBestBound`), and routes that cannot optimize it reject the model with `Unsupported real objective`.

## Bit-vector expressions and relations

There are no bit-vector extension operators in `:dsl`. Construct the `:core` nodes directly. Binary and comparison operands must have the same width.

| Node / enum value | Construction spelling | Semantics |
|---|---|---|
| `BitVecVar`, `BitVecLiteral` | Scope factory, literal constructor | Fixed-width variable or in-range unsigned constant |
| `BitVecNot` | `BitVecNot(x)` | Bitwise complement |
| `BitVecNegation` | `BitVecNegation(x)` | Wrapping negation |
| `BitVecBinary` / `ADD`, `SUBTRACT`, `MULTIPLY` | `BitVecBinary(operator, x, y)` | Arithmetic modulo `2^width` |
| `BitVecBinary` / `AND`, `OR`, `XOR` | Same constructor | Bitwise Boolean operations |
| `BitVecBinary` / `SHIFT_LEFT` | Same constructor | Left shift; zero at amounts at least the width |
| `BitVecBinary` / `LOGICAL_SHIFT_RIGHT` | Same constructor | Zero-filled right shift; zero at amounts at least the width |
| `BitVecBinary` / `ARITHMETIC_SHIFT_RIGHT` | Same constructor | Sign-filled right shift; all sign bits at amounts at least the width |
| `BitVecComparison` / `EQUAL` | `BitVecComparison(relation, x, y)` | Equality |
| `BitVecComparison` / `UNSIGNED_LESS_THAN`, `UNSIGNED_LESS_OR_EQUAL` | Same constructor | Unsigned ordering |
| `BitVecComparison` / `SIGNED_LESS_THAN`, `SIGNED_LESS_OR_EQUAL` | Same constructor | Two's-complement signed ordering |
| `BitVecToInt` | `BitVecToInt(x, signed)` | Exact signed or unsigned integer interpretation |

Shift amounts are unsigned bit-vectors. Bit-vector arithmetic wraps; ordinary integer arithmetic does not. Out-of-range bit-vector literals are rejected.

## Globals and derived helpers

`element` returns an integer expression. Counting helpers also return integers; the other entries below return Boolean expressions. Derived helpers need not have distinct AST nodes.

| Node/helper | Kotlin spelling | Semantics and requirements |
|---|---|---|
| `AllDifferent` | `allDifferent(*operands)` | Pairwise distinct integer values |
| `Element` | `element(index, *values)` | Zero-based selection from a non-empty array; index must be in range |
| `Table` | `Table(operands, tuples)` | Allowed tuples of matching arity; no rows means false. No lowercase `table` DSL factory |
| `BoolCardinality` | `atMost(k, *conditions)`, `atLeast(k, *conditions)`, `exactly(k, *conditions)` | True operand count compared with non-negative `k`; repetitions count repeatedly |
| Count | `count(value, *operands)` | Number equal to the integer expression `value` |
| Among | `among(values, *operands)` | Number taking a value in the supplied `Collection<BigInteger>` |
| Global cardinality | `globalCardinality(operands, values, counts)` | Each listed value occurs its corresponding count; other values remain permitted |
| Distinct count | `nValue(*operands)` | Number of distinct values; zero for an empty sequence |
| Ordering | `increasing(*operands)`, `decreasing(*operands)` | Non-strict adjacent ordering |
| Lexicographic ordering | `lexLess(left, right)`, `lexLessOrEqual(left, right)` | Equal-length sequences; first different position decides |
| `Inverse` | `inverse(direct, inverse)` | Equal-length zero-based inverse permutations |
| `Circuit` | `circuit(*successors)` | One cycle through every node; zero-based successors |
| `SubCircuit` | `subCircuit(*successors)` | Self-pointing nodes excluded; remaining nodes form one cycle, if any |
| `Regular` | `regular(automaton, sequence)` or `regular(automaton, *sequence)` | Word accepted by the deterministic automaton |
| `Cumulative` | `cumulative(starts, durations, demands, capacity)` | Active demands never exceed capacity; constant durations positive, demands and capacity non-negative |
| Disjunctive | `disjunctive(starts, durations)` | Unit-demand, unit-capacity cumulative: tasks cannot overlap |
| `BinPacking` | `binPacking(bins, sizes, loads)` | Item `i` goes in zero-based bin `bins[i]`; loads equal sums of non-negative constant sizes |
| `NoOverlap2D` | `noOverlap2D(xs, ys, widths, heights)` | Positive-sized rectangles do not overlap; touching is allowed |

Scheduling intervals are half-open: an end may equal another start. Parallel lists must have matching lengths as required by each constructor. Globals remain abstract nodes until lowering; see [Global constraints](../explanation/global-constraints.md).

`Automaton(initial, accepting, transitions)` uses non-negative integer states and exact integer symbols. Missing transitions reject; duplicate state/symbol transitions are invalid. The DSL `automaton(initial, accepting) { ... }` provides `transition(from, on, to)`.

## Models, metadata and objectives

| API | Effect |
|---|---|
| `ConstraintScope.empty()` | Fresh mutable builder with independent variable identities |
| `ConstraintScope.empty { ... }` | Run the block on a fresh scope as receiver and return the block's result, such as variable handles or a solve outcome |
| `ConstraintScope.of(model)`, `of(scope)` | Fresh scope seeded with a model's, or another scope's current, variables, constraints, projection and objective; the variables keep their identity. Both accept a trailing receiver block, like `empty { ... }` |
| `ConstraintModel.of { ... }` | Build a model using a fresh scope as a Kotlin receiver |
| `ConstraintModel.empty()` | Model with no variables, constraints or objective |
| `model.extend { ... }` | New model holding `model` plus what the block adds; `model` is unchanged. An explicit projection is kept; otherwise new variables become outputs too |
| `add(constraint, name, annotations, source)` | Assert a Boolean expression; optional unique name, string annotations and `SourceLocation` |
| `project(*variables)` | Ordered output subset; defaults to all variables; an explicit empty projection is valid |
| `buildModel()` | Detached immutable snapshot |
| `minimize(expression)` / `Objective.Minimize` | Minimize one integer expression, replacing the objective |
| `maximize(expression)` / `Objective.Maximize` | Maximize one integer expression, replacing the objective |
| `satisfy()` / null objective | Clear optimization and select satisfaction |
| `minimizeViolations(*soft)` | Minimize the total non-negative `Long` weight of false constraints; arguments are `Pair<BoolExpr, Long>`; return the penalty expression |

Names must be non-blank and unique within a scope. Variable IDs encode identity; foreign variables are rejected in assertions, objectives and projections. Projection rejects duplicates. Soft constraints remain preferences unless also asserted with `add`. Built models are unaffected by later builder edits. Their `variables`, `entries`, `constraints` and `outputVariables` retain declaration, assertion and projection order. `ConstraintEntry` holds its expression and metadata. `SourceLocation(file, line = 1, column = 1)` uses a non-blank source identifier and positive, one-based positions; it does not access the filesystem.

See [Optimize](../how-to/optimize.md) and the [API reference](/kt-constraints/api/index.html) for overloads and declarations.

## Expression interfaces and identity

An **expression** is an immutable calculation or condition. Its **sort** identifies the kind of value it produces. `Expr.sort`, `children` and `variables` expose the value kind, ordered immediate operands and free variables in first-occurrence order. Extension nodes must expose their operands through `children` so traversal and ownership checks see them.

| Interface | Value kind and important members |
|---|---|
| `BoolExpr` | Boolean expression; `sort` is `Sort.Bool` |
| `IntExpr` | Mathematical integer expression; `sort` is `Sort.Integer` |
| `RealExpr` | Exact real expression; `sort` is `Sort.Real`; built-in arithmetic is linear and ground values remain rational |
| `BitVecExpr` | Fixed-width expression; positive `width` determines `Sort.BitVector(width)` |
| `Variable` | Expression with semantic `id` and display `name`; its free-variable set contains itself |
| `VariableId` | Identity tied to an owner plus declaration `index`; the same index from another owner is a different identity |
| `Domain` | Allowed value space; `isFinite` reports finiteness |

`VariableFactory.empty()` creates an independent identity owner. Its `boolVar`, `intVar(name, domain)`, `realVar` and `bitVecVar` create fresh variables for tools such as incremental sessions. For ordinary models, use `ConstraintScope` so declarations, assertions and projections are validated together. Factory-created variables do not automatically belong to a model scope.

`ConstraintModel.objective` is null for satisfaction or an `Objective.Minimize`/`Objective.Maximize` carrying `expression`. Its `variables`, `entries`, `constraints` and `outputVariables` are detached lists. `ConstraintEntry.expression`, `name`, `annotations` and `source` preserve assertion metadata; annotation keys are sorted. `ConstraintScope.add`, objective setters, `satisfy` and `project` return the builder for chaining.

## Numeric and global helper details

| API | Members and semantics |
|---|---|
| `IntDomain` | `intervals`, nullable `min`/`max`, `isEmpty`, exact `cardinality`, `isFinite = true`, membership via `contains`, `union` and `intersect` |
| `IntInterval(min, max)` | Non-empty inclusive interval; rejects reversed bounds |
| `Rational` | Normalized `numerator` and positive `denominator`, `isInteger`, exact `plus`, `minus`, `times`, `unaryMinus` and comparison; `ZERO` and `ONE` constants. Text output is an integer or `p/q`; parsing rejects exponent notation |
| `unaryMinus`, `negate` | Kotlin `-expression`, or `negate()` from Java, builds `IntNegation` or `RealNegation`; it does not solve or mutate the operand |
| `BitVecOperator` | Operation selector for `BitVecBinary`: arithmetic, bitwise and shift values listed above |
| `BitVecRelation` | Relation selector for `BitVecComparison`: equality and signed/unsigned ordering listed above |
| `CardinalityRelation` | `AT_MOST`, `EXACTLY`, `AT_LEAST` compare `BoolCardinality`'s number of true operands with its `count` |
| `Transition(from, symbol, to)` | An automaton edge: read exact integer `symbol` in source state `from`, then enter state `to` |
| `AutomatonBuilder` | Receiver of `automaton(initial, accepting) { ... }`; `transition(from, on, to)` accepts a `Long` or `BigInteger` symbol |
| `Automaton` | Immutable `initial`, `accepting` and `transitions`; `states` is the largest mentioned state plus one; `next(state, symbol)` returns the next state or null for a missing edge |
| `DecomposableConstraint.decomposition` | Equivalent Boolean formula over the same operands, without introducing variables |
| `DecomposableConstraint.decompose(auxiliaries)` | Top-level equivalent formula that may introduce hidden variables from a `VariableFactory`; those variables mean “there exist values”. Do not substitute this form under arbitrary Boolean negation |

### Worked shift-sequence rule

An **automaton** reads symbols one at a time and follows transitions between states. Here symbol
`0` means rest and `1` means work. State `0` means the previous day was rest (or no day has been
read); state `1` means it was work. Both states are **accepting states**, where a sequence may end.
There is no transition from state `1` on symbol `1`, so consecutive working days are rejected.
`regular` connects this rule to the integer sequence variables.

The Kotlin examples are complete helper functions. Call a `suspend` helper from a coroutine or another
suspending function. The shown imports also cover later Kotlin helpers on this page.

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/howto/ModelStyles.kt:imports"

--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/howto/ModelStyles.kt:regular"
```

The tested `1, 0, 1` sequence is feasible; `1, 1, 0` is infeasible. The `automaton` receiver is an
`AutomatonBuilder`; `transition(from, on, to)` declares one deterministic step. Missing transitions
reject the sequence. See [nurse rostering](../how-to/problems/nurse-rostering.md) for coverage and preferences.

## Evaluation and traversal

A **valuation** binds variables to concrete values. `Valuation.valueOf` overloads return Boolean, exact integer, rational or unsigned bit-vector values, with null for absent bindings; the real and bit-vector overloads default to null. `Assignment` implements this interface for solver answers; see [Kotlin solver API](solver-api.md#assignments).

`GroundEvaluator.evaluate(expression)` calculates built-in constant expressions without search. The overload with a `Valuation` evaluates expressions with variables and requires every referenced binding; integer bindings must lie in their domains and bit-vector bindings must fit their widths. It returns `Boolean`, `BigInteger` or `Rational` according to the expression interface. It rejects unknown extension nodes and missing/invalid bindings. An out-of-range `Element` index throws `UndefinedExpressionException`, an `IllegalArgumentException` subtype: no value is defined for that input.

The extension `Expr.fold(combine)` visits operands before their parent and supplies the ordered child results to `combine`. It visits shared subtrees once per occurrence, using an explicit stack rather than recursive calls. Use it for structural analyses or translations; it does not search for assignments.
