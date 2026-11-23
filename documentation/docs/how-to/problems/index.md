# Classic problems

Choose a problem to see a small Kotlin model, a solve call, and its expected result.
Each guide includes modelling choices and ways to adapt the instance.

| Problem | Goal | Techniques |
| --- | --- | --- |
| [N-Queens](n-queens.md) | Place queens without attacks | Finite-domain variables; `allDifferent` on columns and diagonal expressions |
| [Sudoku](sudoku.md) | Complete a grid | `allDifferent` on rows, columns and blocks; equality constraints for clues |
| [Graph coloring](graph-coloring.md) | Color adjacent vertices differently | Integer color variables; disequality constraints; symmetry breaking |
| [Knapsack](knapsack.md) | Choose items within capacity | Selection indicators; weighted sums; maximization |
| [Assignment](assignment.md) | Assign workers to tasks | Permutations; `allDifferent`; indexed costs; minimization |
| [Bin packing](bin-packing.md) | Pack items into bins | `binPacking`; load variables; capacity constraints |
| [Job-shop scheduling](job-shop.md) | Minimize completion time | Precedence; `disjunctive` / `cumulative`; makespan minimization |
| [Traveling salesman](traveling-salesman.md) | Find a shortest tour | `circuit`; zero-based `element`; sum of edge costs |
| [Nurse rostering](nurse-rostering.md) | Cover shifts with preferences | Counting; rest constraints; weighted `minimizeViolations` |
| [Pigeonhole](pigeonhole.md) | Prove that no placement exists | `allDifferent`; infeasibility; incremental assumptions and unsat cores |

Start with [your first Kotlin model](../../tutorials/kotlin-first-model.md) if you have not used
the DSL. For solver requirements, see [choose a solver](../choose-a-solver.md).
