# :test-solve

Unpublished solver test utilities. Add this module only to test dependencies.

`solverContract(create)` supplies fresh-fixture SAT/UNSAT, exact min/max, independent assignment validation,
enumeration/projection, timeout/limit, cancellation/close and unsupported-model cases. Each adapter must reuse it.

`FiniteOracle` enumerates tiny Boolean/integer models exactly, with hard limits of 10,000 candidates and
64 variables. It supports the built-in integer/Boolean/global IR, rejects unsupported nodes before search,
and never registers itself as a production backend. It emits deduplicated satisfaction projections or strictly
improving optimization incumbents, and proves optimality only after exhaustive completion.
