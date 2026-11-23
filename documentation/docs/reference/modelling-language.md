# Modelling language

`:core` supplies solver-independent immutable expressions, `:model` supplies snapshots and `ConstraintScope`, and `:dsl` supplies the operators below in `io.github.gciatto.ktconstraints.dsl`. A construct's presence here does not imply support by every [solver route](solvers-and-routes.md).

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
| `BoolToInt` | `p.toInt()` | Integer 1 when true, 0 when false |

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
| `IntToReal` | `x.toReal()` | Exact embedding into real arithmetic |

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

Constant overloads accept `Rational`, `Long` or `Int`. There is no product of real variables, implicit integer embedding, floating-point overload or real objective.

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

`Automaton(initial, accepting, transitions)` uses non-negative integer states and exact integer symbols. Missing transitions reject; duplicate state/symbol transitions are invalid. The DSL `automaton(initial, accepting) { ... }` provides `transition(from, symbol, to)`.

## Models, metadata and objectives

| API | Effect |
|---|---|
| `ConstraintScope.empty()` | Fresh mutable builder with independent variable identities |
| `ConstraintModel.of { ... }` | Build a model using a fresh scope as a Kotlin receiver |
| `add(constraint, name, annotations, source)` | Assert a Boolean expression; optional unique name, string annotations and `SourceLocation` |
| `project(*variables)` | Ordered output subset; defaults to all variables; an explicit empty projection is valid |
| `build()` | Detached immutable snapshot |
| `minimize(expression)` / `Objective.Minimize` | Minimize one integer expression, replacing the objective |
| `maximize(expression)` / `Objective.Maximize` | Maximize one integer expression, replacing the objective |
| `satisfy()` / null objective | Clear optimization and select satisfaction |
| `minimizeViolations(*soft)` | Minimize the total non-negative `Long` weight of false constraints; arguments are `Pair<BoolExpr, Long>`; return the penalty expression |

Names must be non-blank and unique within a scope. Variable IDs encode identity; foreign variables are rejected in assertions, objectives and projections. Projection rejects duplicates. Soft constraints remain preferences unless also asserted with `add`. Built models are unaffected by later builder edits. Their `variables`, `entries`, `constraints` and `outputVariables` retain declaration, assertion and projection order. `ConstraintEntry` holds its expression and metadata. `SourceLocation(file, line = 1, column = 1)` uses a non-blank source identifier and positive, one-based positions; it does not access the filesystem.

See [Optimize](../how-to/optimize.md) and the [API reference](/kt-constraints/api/index.html) for overloads and declarations.
