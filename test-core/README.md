# :test-core

Unpublished reusable Kotest factories for representation tests. Add this module only to test dependencies.

* `integerDomainContract(create)` compares finite-domain operations and cardinality with small enumerated sets.
* `exactAlgebraContract(evaluate)` checks exact arithmetic laws with arbitrary `Long` inputs, including overflow cases.
* `modelScopeContract(create)` checks variable renaming, scope isolation and detached snapshots.

All generated cases use fixed seeds and fresh identity-bearing fixtures. `:core` and `:model` execute these
contracts on their test targets. No solver or serialization format is assumed.
