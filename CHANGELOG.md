## [3.0.0](https://github.com/gciatto/kt-constraints/compare/2.0.0...3.0.0) (2026-11-24)

### ⚠ BREAKING CHANGES

* Z3.loadBackend() is renamed Z3.load(), and Java's
AutoBackends.load(...) is renamed AutoBackends.loadAutoBackend(...).

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X2xs6ExEnwgHgap41DajQW
* **core:** BoolExpr.toInt() and IntExpr.toReal() are renamed
asIntExpr() and asRealExpr(); in TypeScript, toInt() and toReal() are
renamed asIntExpression() and asRealExpression().

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X2xs6ExEnwgHgap41DajQW
* **solve-blocking:** the Blocking* classes move from
io.github.gciatto.ktconstraints.interop to
io.github.gciatto.ktconstraints.solve.blocking.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X2xs6ExEnwgHgap41DajQW
* **core:** the operator extensions in io.github.gciatto.ktconstraints.dsl
(eq, neq, lt, le, gt, ge, plus, minus, times, unaryMinus, and, or, implies,
not, toInt, toReal) are now members of the core expression interfaces; drop
their explicit imports. Java's Expressions.plus/eq/... statics are removed
in favour of instance methods.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X2xs6ExEnwgHgap41DajQW
* **interop-java:** JavaNumbers.integer(java.math.BigInteger) and
JavaNumbers.toJava(BigInteger) are removed; use kt-math's
JvmUtils.toKotlin and JvmUtils.toJava.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X2xs6ExEnwgHgap41DajQW
* **solve:** SolverBackend.create() is renamed createSolver(); in
TypeScript, Backend.create(), Backend.openStore() and Solution.valueOf()
are renamed createSolver(), createStore() and intValueOf().

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X2xs6ExEnwgHgap41DajQW
* **model:** ConstraintScope.build() is renamed buildModel().

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X2xs6ExEnwgHgap41DajQW

### Features

* add a common backend loader interface and a blocking loader on Native ([6db9883](https://github.com/gciatto/kt-constraints/commit/6db9883ac5e39d7ef8b0b9d1be5ba3c56093e689))
* **core:** make expression operators members so Java can chain them ([76e9559](https://github.com/gciatto/kt-constraints/commit/76e95591e53dd14e75b1bef00d63612f702e41a6))
* **core:** name expression conversions asIntExpr/asRealExpr and add negate() ([85e67f3](https://github.com/gciatto/kt-constraints/commit/85e67f3ae03384b4d8f118f52998f79a08a2d152))
* **model:** name the model snapshot buildModel and add scope and model factories ([9092a19](https://github.com/gciatto/kt-constraints/commit/9092a195bd480c654b46fc23ecc1cdd09553224f))
* **solve-auto:** open incremental sessions on AutoBackend and add scoped helpers ([07236e3](https://github.com/gciatto/kt-constraints/commit/07236e33bd0a79bcdd9393639829480530f8317e))
* **solve-auto:** register the pure-Kotlin solver as every platform's last resort ([94005da](https://github.com/gciatto/kt-constraints/commit/94005dad4380cae49515af2a25bca4af23a7bcaa))
* **solve-blocking:** move the blocking API into a Kotlin module for JVM and Native ([2de287a](https://github.com/gciatto/kt-constraints/commit/2de287a6de9ec276ec473a7def41bf1180711a16))
* **solve-kotlin:** publish the pure-Kotlin solver and load it from TypeScript ([3dc8c94](https://github.com/gciatto/kt-constraints/commit/3dc8c9463c6a5dc2b2f59dbacbe0fbb5df1f9103))
* **solve-z3:** load the platform's Z3 route from common code ([3fcb1f2](https://github.com/gciatto/kt-constraints/commit/3fcb1f2a428d58ecfd5d407fe80c4e397e06e395))
* **solve:** name backend factories createSolver and add scoped solver helpers ([4d653dd](https://github.com/gciatto/kt-constraints/commit/4d653dd94b089d10dc20d6ddad0623a4d8704df5))

### Documentation

* bring the READMEs in line with the 2.0 API ([018740e](https://github.com/gciatto/kt-constraints/commit/018740e1d3f61c9d5f650d435dfa6806ab361285))
* rewrite the examples around named results and document the new factories ([d804053](https://github.com/gciatto/kt-constraints/commit/d804053fa6334077fdfd3cad98ba6a77fa8654dd))

### General maintenance

* merge the 2.0.0 release back into develop ([2eb7b72](https://github.com/gciatto/kt-constraints/commit/2eb7b72acefebca0194734d92c8125badbf4b3a9))

### Refactoring

* **interop-java:** convert big integers with kt-math instead of strings ([0f9f0ef](https://github.com/gciatto/kt-constraints/commit/0f9f0ef7d242a7d737d0c29075c2df66163be730))

## [2.0.0](https://github.com/gciatto/kt-constraints/compare/1.0.1...2.0.0) (2026-11-24)

### ⚠ BREAKING CHANGES

* **solve-auto:** `solveAuto(model, options)` is removed; use
`AutoBackend.load().use { auto -> auto.create().use { it.solve(model, options) } }`.
`Portfolio`, `DefaultBackends` and `LoadedBackends` are no longer public.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X2xs6ExEnwgHgap41DajQW
* **solve-auto:** depend on `io.github.gciatto:kt-constraints-solve-auto` and import
from `io.github.gciatto.ktconstraints.solve.auto` instead of `...solve.portfolio`.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X2xs6ExEnwgHgap41DajQW

### Features

* **solve-auto:** replace solveAuto with AutoBackend, a backend like any other ([d4ae5df](https://github.com/gciatto/kt-constraints/commit/d4ae5df4aeaf8038e661747c91ff04f6987b5578))

### Documentation

* add a backends section, a reading order and reference for every public API ([5662a20](https://github.com/gciatto/kt-constraints/commit/5662a206bfc62e9491925d7fd15c867faf380350))

### Tests

* build models through scope receivers in every module's tests ([5cb7718](https://github.com/gciatto/kt-constraints/commit/5cb771876765e7cb1ffe87c53b90567803961b7d))

### Build and continuous integration

* **docs:** dedent quoted snippet sections and link pages in navigation order ([70df451](https://github.com/gciatto/kt-constraints/commit/70df45170e57a174fe626f119e1785b9c330ec57))

### Refactoring

* **solve-auto:** rename the solve-portfolio module to solve-auto ([2c4fc2f](https://github.com/gciatto/kt-constraints/commit/2c4fc2f73a89dbcb7ffac299099b0cb0f3446212))

## [1.0.1](https://github.com/gciatto/kt-constraints/compare/1.0.0...1.0.1) (2026-11-23)

### Bug Fixes

* **release:** pass the npm token file as --userconfig so npx's npm_config_userconfig cannot shadow it ([e1eb9d8](https://github.com/gciatto/kt-constraints/commit/e1eb9d8f8d85172906d73900bbe0b42d978a2a60))

## 1.0.0 (2026-11-23)

### ⚠ BREAKING CHANGES

* split crowded main packages into cohesive subpackages
* **spi:** batch satisfaction results no longer carry end-of-search
metadata; use solutions() to enumerate.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X2xs6ExEnwgHgap41DajQW

### Features

* absolute value as max(x, -x) in the Kotlin/Java DSL and the TypeScript facade ([42aacce](https://github.com/gciatto/kt-constraints/commit/42aacce154a78b6c5d12517c11bfd0a1f7aa1c83))
* add exact linear real arithmetic and its Z3 translation on every route ([541ee62](https://github.com/gciatto/kt-constraints/commit/541ee62353d3fda4b50a181600c9bfb09d4528bc))
* add fixed-width bit-vectors and their Z3 translation on every route ([2267316](https://github.com/gciatto/kt-constraints/commit/2267316b14470982e9c8ce3e5583577da46aa217))
* add incremental sessions with owned checkpoints and the Z3 implementation ([12e1188](https://github.com/gciatto/kt-constraints/commit/12e1188007a9b58c6c4ac805950f9e43c2be1080))
* **choco:** ChocoStore, a propagating and backtracking constraint store ([7a80eb9](https://github.com/gciatto/kt-constraints/commit/7a80eb96ba4760ff163f4ce8ce7409a0196026ff))
* **choco:** direct Choco route on the JVM ([c7803d5](https://github.com/gciatto/kt-constraints/commit/c7803d5b1665321c3e2e25c9a02ec46e87bfed8d))
* **core:** add exact algebra traversal and ground evaluation ([0034c4f](https://github.com/gciatto/kt-constraints/commit/0034c4f30cff7c92826c07f3e09f34c886edbbda))
* **core:** evaluate typed bindings with explicit undefined values ([126d7e3](https://github.com/gciatto/kt-constraints/commit/126d7e38839fa7a0b65ae7bdfbea4d5043db9105))
* **core:** preserve finite-domain global constraint nodes ([524808d](https://github.com/gciatto/kt-constraints/commit/524808d13038a84fcd61bcc8bfa75e2d2660d52c))
* **core:** represent exact integers and canonical finite domains ([df8c59c](https://github.com/gciatto/kt-constraints/commit/df8c59c4a635af78f1bf1b6c496b664c05e8389f))
* **cpsat:** direct OR-Tools CP-SAT route on the JVM ([bdbe968](https://github.com/gciatto/kt-constraints/commit/bdbe9689bc3079d4f430b6f5b092e6c366ad392d))
* explain store failures and fork stores ([85f7211](https://github.com/gciatto/kt-constraints/commit/85f7211fcb359cf245299ded711c6c18b9bba046))
* **interop-c:** C shared library and header for the desktop native targets ([1010119](https://github.com/gciatto/kt-constraints/commit/101011960f3de2a8b7285282b86a4e6c12d7585c))
* **interop-java:** blocking and CompletableFuture solving with a Java Expressions facade ([2e5a382](https://github.com/gciatto/kt-constraints/commit/2e5a382212cf8f26355cc6f1b63e0687f7b26448))
* **interop-java:** blocking and future-based incremental sessions ([1f4ddf4](https://github.com/gciatto/kt-constraints/commit/1f4ddf4963f7f3eaa7f0ba095cb934c383911725))
* **interop-java:** BlockingStore, a blocking and future-based constraint store facade ([6b81f48](https://github.com/gciatto/kt-constraints/commit/6b81f48ba02279d238901d0611c5a7d4c313ffcd))
* **interop-js:** exact reals and incremental sessions ([313c513](https://github.com/gciatto/kt-constraints/commit/313c513529a868fae0e1767f114bed5ff6780443))
* **interop-js:** MiniZinc Node and browser loaders and declared capabilities ([ca9d78a](https://github.com/gciatto/kt-constraints/commit/ca9d78af4faec02f5da533e222f166a6172feff6))
* **interop-js:** Store, a Promise-based constraint store facade ([fb39021](https://github.com/gciatto/kt-constraints/commit/fb390219a4487a6def997f5f8d7d8e227b3a1363))
* **interop-js:** TypeScript facade with Promise solving, abort signals and async iteration ([b2d81f5](https://github.com/gciatto/kt-constraints/commit/b2d81f561b24b4f8da12826e1780eaa57db27c86))
* inverse, circuit, cumulative and 0/1 conversion on every route, with counting and ordering helpers ([2a93549](https://github.com/gciatto/kt-constraints/commit/2a9354924d6c6d5b15c4a4466cf6d15551d7ddae))
* machine-integer variable bounds, exact narrowing reads and Java number conversions ([172c017](https://github.com/gciatto/kt-constraints/commit/172c017c5cc85ee859145cdb9833b2b0a97772fd))
* min and max integer globals on every route ([4c878e7](https://github.com/gciatto/kt-constraints/commit/4c878e7c56f2de18236943cad7026fbb9733c409))
* **minizinc:** add bounded HiGHS and CBC execution routes ([92df66d](https://github.com/gciatto/kt-constraints/commit/92df66d1c932eff3cabcd1d6452c0c74b9713d4c))
* **minizinc:** add bounded HiGHS and CBC execution routes ([e72dcc3](https://github.com/gciatto/kt-constraints/commit/e72dcc312e38a6e4f174e55475a4851c36cba056))
* **minizinc:** add owned Node CLI and Chuffed browser Wasm routes ([3df5562](https://github.com/gciatto/kt-constraints/commit/3df55621a71073ce9e2c035b352a89bc6dbbccb6))
* **minizinc:** add owned Node CLI and Chuffed browser Wasm routes ([8156654](https://github.com/gciatto/kt-constraints/commit/8156654d68d38232b9c70273f5bf387a4b60b374))
* **minizinc:** compile exact finite models with checked Gecode ranges ([4d3b797](https://github.com/gciatto/kt-constraints/commit/4d3b7971493434a2e8e5b19f79ed9315f62b077d))
* **minizinc:** execute explicit Gecode Chuffed and CP-SAT routes ([8d2c80a](https://github.com/gciatto/kt-constraints/commit/8d2c80a1265ed4a304cfdf4474ce3c805f40823b))
* **minizinc:** execute explicit Gecode Chuffed and CP-SAT routes ([36a02fa](https://github.com/gciatto/kt-constraints/commit/36a02faa6246ca848461247cf7cb3db40b579a3a))
* **minizinc:** execute Gecode with cancellable JVM process ownership ([1ea61e0](https://github.com/gciatto/kt-constraints/commit/1ea61e0a640b91874358966eac42fe1956aeaaef))
* **minizinc:** validate JSON solutions and terminal evidence ([c6629d7](https://github.com/gciatto/kt-constraints/commit/c6629d7cd6f258526bb6c2a4a5bf496394144548))
* **mip:** SCIP and HiGHS routes through OR-Tools ([a80624e](https://github.com/gciatto/kt-constraints/commit/a80624e50fbbee92817c5dce75b00998cd787908))
* **model:** freeze labeled assertions and output projection ([6aa1e56](https://github.com/gciatto/kt-constraints/commit/6aa1e56bdb7aeee51035305d02708bdc23821d96))
* **portfolio:** bundled default routes per platform and solveAuto ([8219c76](https://github.com/gciatto/kt-constraints/commit/8219c76a9714733f795c70e14d62e7ee140d0ca0))
* **portfolio:** fallback and race portfolios over ranked backends ([44fca81](https://github.com/gciatto/kt-constraints/commit/44fca81d4e0a552a220d95b1f5751089fbdcc877))
* real-expression DSL and exact decimal rational parsing ([55db82e](https://github.com/gciatto/kt-constraints/commit/55db82e52c8633e4a7c8c43add0ca66f67659285))
* regular, subcircuit, bin packing and 2D non-overlap, hidden-variable decompositions, nValue and soft constraints ([5e3cf26](https://github.com/gciatto/kt-constraints/commit/5e3cf26e44fcd24955e73f51c271948d9cf5ffff))
* **solve-kotlin:** add reusable Boolean gates and cooperative budgets ([5580bbf](https://github.com/gciatto/kt-constraints/commit/5580bbf65cd908f36599b151a433dab67cb41e84))
* **solve-kotlin:** add reversible exact integer domain storage ([8429c09](https://github.com/gciatto/kt-constraints/commit/8429c09de8cbe8f5b9e3c343ba35d2a5ceade7db))
* **solve-kotlin:** add watched CDCL solving with temporary assumptions ([ec701c1](https://github.com/gciatto/kt-constraints/commit/ec701c1e9434de862955f219d6afcc91fbe525ea))
* **solve-kotlin:** combine discrete search with exact arithmetic conflicts ([e49435e](https://github.com/gciatto/kt-constraints/commit/e49435e1ac06026fa74ac08ad2d18c6fdda7501b))
* **solve-kotlin:** compare linear sums and linear objectives through native linear constraints ([8f59e9c](https://github.com/gciatto/kt-constraints/commit/8f59e9c09234e8542528a30fae7264d740ac28bb))
* **solve-kotlin:** compile exact integer and bit-vector arithmetic ([ea7146c](https://github.com/gciatto/kt-constraints/commit/ea7146c59d373d8e30030cf07068e532930943c6))
* **solve-kotlin:** declare variables in open sessions, forgotten when their frame is popped ([ad5a72b](https://github.com/gciatto/kt-constraints/commit/ad5a72b00defdc766ce79c675fd39761fee59ebe))
* **solve-kotlin:** expose an incremental portable backend with scoped assertions ([92a2e26](https://github.com/gciatto/kt-constraints/commit/92a2e2668b07bd7cd1e0f26a36d805faedca4555))
* **solve-kotlin:** lower the current model language with exact theory bridges ([c404ba2](https://github.com/gciatto/kt-constraints/commit/c404ba24b184c8758f1288d92503efe5ffd8fc35))
* **solve-kotlin:** min, max, Boolean-to-integer and structural globals through exact decompositions ([e8081da](https://github.com/gciatto/kt-constraints/commit/e8081da11782d00f180a409491be99d41d679b7c))
* **solve-kotlin:** native linear constraints in the SAT kernel with lazily explained propagation ([54f2878](https://github.com/gciatto/kt-constraints/commit/54f28781c40a703725f81232dd52f0b60c4f6016))
* **solve-kotlin:** order literals for inequalities between shifted medium-domain variables ([6385b83](https://github.com/gciatto/kt-constraints/commit/6385b83931088284ff14370c15a14542063043a9))
* **solve-kotlin:** propagate exact integer bounds before encoding ([50eb88d](https://github.com/gciatto/kt-constraints/commit/50eb88da867425094425f6ba6c3f078ff90cabc0))
* **solve-kotlin:** represent exact mixed arithmetic and definedness ([11e7df7](https://github.com/gciatto/kt-constraints/commit/11e7df73afc12d29e25e6f1fd496dea29d9c8769))
* **solve-kotlin:** solve exact linear rational systems ([bc9efd9](https://github.com/gciatto/kt-constraints/commit/bc9efd9c641659b65e711557cde501318d20bcc5))
* **solve-kotlin:** stream projected witnesses and prove integer optima ([dbaa70b](https://github.com/gciatto/kt-constraints/commit/dbaa70b1b3c581c13b80983993109e15803faf48))
* **solve-kotlin:** value literals for small integer domains, with native top-level all-different and disequalities ([d4e54b8](https://github.com/gciatto/kt-constraints/commit/d4e54b83e182e14e09be56ba6e0b042946f13a85))
* **solve-spi:** extensible feature-, capability- and platform-aware backend ranking ([5a9c01a](https://github.com/gciatto/kt-constraints/commit/5a9c01a5858b2c3ea74fa68312a49d4bf3213d30))
* **solve-spi:** extract model features and problem classes in one walk ([97c320c](https://github.com/gciatto/kt-constraints/commit/97c320cce5475da10428ad9baf8b18f826c7e067))
* **solve-spi:** SatisfiabilityStore over any incremental session ([b79439d](https://github.com/gciatto/kt-constraints/commit/b79439d4b45f8776276d1a4a2d8be527ebc80b8f))
* **solve:** ConstraintStore API for external logic engines ([57c90fc](https://github.com/gciatto/kt-constraints/commit/57c90fcee5f642059db9800b963089a1b135f8d5))
* **solve:** declare variables in an open incremental session ([3bcf227](https://github.com/gciatto/kt-constraints/commit/3bcf227bae8a0157087396d4ed1770633b1aad38))
* **solve:** preserve exact assignments and termination evidence ([d9f55e1](https://github.com/gciatto/kt-constraints/commit/d9f55e1729449dcaf4f8a49a95e2a9bbf1d058ab))
* **spi:** lazy batch satisfaction ([7371bfa](https://github.com/gciatto/kt-constraints/commit/7371bfaee578db0f4db62f59978338173fe66834))
* **spi:** manage cancellable solution streams and solver resources ([749d719](https://github.com/gciatto/kt-constraints/commit/749d7194d01c06363748b54f974ec965e116d123))
* **spi:** preserve explicit backend timeout and limit reasons ([ba27eb5](https://github.com/gciatto/kt-constraints/commit/ba27eb5e4f5e89cffed92cad2261bcc51f62be93))
* **spi:** select explicitly registered compatible backend routes ([f8d725e](https://github.com/gciatto/kt-constraints/commit/f8d725e1cf8320cf1b544c634e15be4ae1f7634a))
* **test-solve:** add capped exact finite model oracle ([1078f21](https://github.com/gciatto/kt-constraints/commit/1078f214021671c241bfa87b45ad570faee1d088))
* typed per-backend solve options for CP-SAT, Choco, MIP and MiniZinc ([fef8dec](https://github.com/gciatto/kt-constraints/commit/fef8dec0aafd8369a4e1290a2451eff74867c23e))
* **z3:** add the direct JVM Z3 route with exact integers and owned contexts ([672ab0b](https://github.com/gciatto/kt-constraints/commit/672ab0ba5d8031e4b2327dd93d9ebf0452ed9a8e))
* **z3:** add the Kotlin/Native route through the run-time loaded C API ([913703f](https://github.com/gciatto/kt-constraints/commit/913703f359288126f58a50f58bf3140a3f0ad653))
* **z3:** add the z3/wasm route through the official z3-solver module on Node ([2635856](https://github.com/gciatto/kt-constraints/commit/263585672f63545b69ab12c87352974c123cb107))
* **z3:** gallop and bisect objective bounds instead of improving one value at a time ([fff24c5](https://github.com/gciatto/kt-constraints/commit/fff24c59e2f741ce9dd7c635c740c95d6ebc4f51))

### Bug Fixes

* **build:** commonize the Z3 cinterop shim for shared native code ([6173244](https://github.com/gciatto/kt-constraints/commit/6173244dc421731485e0afa3c8521999fe9b8dec))
* **build:** restore standard wrapper UTF-8 defaults ([860c03b](https://github.com/gciatto/kt-constraints/commit/860c03b41fcd5389c0c19d1ef680fa55f0f5e4a8))
* **core:** fold and collect variables with an explicit stack ([035fb78](https://github.com/gciatto/kt-constraints/commit/035fb7814e3f6f0dea1455ab1b95c35fa671a7d0))
* **minizinc:** bind the official package's named exports ([c49138c](https://github.com/gciatto/kt-constraints/commit/c49138cf635c6c921c6d9774ec79a6c94feb2e1d))
* **minizinc:** never terminate loading browser pool workers ([411046a](https://github.com/gciatto/kt-constraints/commit/411046a59ba608bf4b639a829f8662b96d10aaf3))
* **minizinc:** never terminate loading browser pool workers ([ecf6af6](https://github.com/gciatto/kt-constraints/commit/ecf6af60cf19d80afa2e4f38d85ba8d84d003486))
* **minizinc:** probe the JS runtime version before listing solvers ([cf1a7b1](https://github.com/gciatto/kt-constraints/commit/cf1a7b167a62310dbc9d32ee344aa6007f06eb65))
* **minizinc:** probe the JS runtime version before listing solvers ([ecb69e5](https://github.com/gciatto/kt-constraints/commit/ecb69e595b749bfd57e26fa935457d5040bca3ff))
* **minizinc:** release the JS runtime only after cancelled runs exit ([cd66f7a](https://github.com/gciatto/kt-constraints/commit/cd66f7a0d1f2e8459c6b053e6ad4511127d8d422))
* **minizinc:** release the JS runtime only after cancelled runs exit ([203307e](https://github.com/gciatto/kt-constraints/commit/203307e7f21f3a70079d1d467cfa4fc1f35eeb10))
* **minizinc:** wait for killed JVM solver descendants before close returns ([9bdf736](https://github.com/gciatto/kt-constraints/commit/9bdf7360f834ffe1fdb5abadfd910ed01fa97488))
* **minizinc:** wait for killed JVM solver descendants before close returns ([d1e8033](https://github.com/gciatto/kt-constraints/commit/d1e803324140857886002739e01a196d0b6fb8e4))
* **model:** freeze snapshot collections at construction ([f809cac](https://github.com/gciatto/kt-constraints/commit/f809cacaa4e683a59ff01beb9fe12e5d3c1745b0))
* **solve-kotlin:** poll the current consumer's coroutine, not one captured by an earlier pull ([38e1f8c](https://github.com/gciatto/kt-constraints/commit/38e1f8c76e3f2a449a3ab0714086ef392f338c33))
* **solve-kotlin:** snapshot assumptions before cooperative suspension ([5822c74](https://github.com/gciatto/kt-constraints/commit/5822c74728300b3fa1e2fc092457e26ea6231547))
* **solve-portfolio:** count a sub-millisecond remainder of the shared deadline as spent ([2eb1111](https://github.com/gciatto/kt-constraints/commit/2eb1111f9b452906dae688b3918c5e7efccf0637))
* **solve:** reject contradictory optimal objective bounds ([1139574](https://github.com/gciatto/kt-constraints/commit/1139574a175533e8fa45a557cdd10c84cde160a7))
* **test-solve:** keep the benchmark environment lookup in test sources, since a shared nativeMain clashes with xmlutil's core KLIB ([03f09f1](https://github.com/gciatto/kt-constraints/commit/03f09f17c60ba38ab604d865dbcf5ea030c85028))
* **z3:** declare real arithmetic and bit-vector capabilities on every route ([b27c5a0](https://github.com/gciatto/kt-constraints/commit/b27c5a0b006b1220c7131933f7921a961d68b22d))
* **z3:** keep a Wasm context owned until an interrupted check stops ([ca6cfb8](https://github.com/gciatto/kt-constraints/commit/ca6cfb8acd67cc9ccc688a3e579ad4d2b7bdd494))
* **z3:** terminate z3-solver threads in browsers ([ae51940](https://github.com/gciatto/kt-constraints/commit/ae519409645f25fe79db387bb56cab114ddeb93a))

### Documentation

* automatic selection, portfolios, npm installation and first-release notes ([d9825d0](https://github.com/gciatto/kt-constraints/commit/d9825d01bab28bdea8d119ac21a22d15f868b3c2))
* backend availability by platform from CI evidence; document the JS reals, sessions and MiniZinc routes ([58da689](https://github.com/gciatto/kt-constraints/commit/58da6895dc284dd9e10d3cfb0a2d8c5f2a96ff2d))
* **choco:** correct the Choco licence ([d8b8a0c](https://github.com/gciatto/kt-constraints/commit/d8b8a0c6a8cd41ef0b1e978658d31bebdf423e2f))
* **choco:** design the direct Choco route ([0ebe5f1](https://github.com/gciatto/kt-constraints/commit/0ebe5f1f14cffe20d2fc95e5f1266b5843988b32))
* constraint stores, logic-engine mapping and M7 roadmap status ([9437f02](https://github.com/gciatto/kt-constraints/commit/9437f02ba848a1634950055b73a0898d21ca630a))
* **core:** describe public expression operands and model metadata ([3401cc9](https://github.com/gciatto/kt-constraints/commit/3401cc9bc4e01410af519492c91990841d12aa37))
* **cpsat:** design the direct CP-SAT route ([f3c9eb8](https://github.com/gciatto/kt-constraints/commit/f3c9eb81050605107d3a4c39450d3daf2796ef5a))
* define explicit MiniZinc engine selection for M3 ([74e127b](https://github.com/gciatto/kt-constraints/commit/74e127bb6bd92b6cecdb240428664559addc3c19))
* define explicit MiniZinc engine selection for M3 ([a2e895b](https://github.com/gciatto/kt-constraints/commit/a2e895ba1dbc60708246961f9a86b5261919622d))
* define owned browser Wasm and Node CLI runtime routes ([7e6b81a](https://github.com/gciatto/kt-constraints/commit/7e6b81acc1d19e39117c8388b1ff43030883f44a))
* define owned browser Wasm and Node CLI runtime routes ([4712f4e](https://github.com/gciatto/kt-constraints/commit/4712f4e9c14d9f136a4f5c73153ab0bdbe66d529))
* design the bit-vector slice ([144173d](https://github.com/gciatto/kt-constraints/commit/144173d97bb6d303897bda179e7637de879bedf1))
* design the exact linear real arithmetic slice ([81dd4b0](https://github.com/gciatto/kt-constraints/commit/81dd4b0c0ace350e75011d6551961957b76de255))
* document exact linear reals and their routes ([f32808b](https://github.com/gciatto/kt-constraints/commit/f32808b6c7394d1bb481be12070a46c25ad84767))
* document fixed-width bit-vectors ([a47ccd8](https://github.com/gciatto/kt-constraints/commit/a47ccd827164131500e04f65140bff786d0b8baa))
* document the direct Choco route ([62bcec6](https://github.com/gciatto/kt-constraints/commit/62bcec6ea54666d74d1d503dc0a6d3413732f181))
* document the direct CP-SAT route ([e35c618](https://github.com/gciatto/kt-constraints/commit/e35c618f65614ca50c90b85d0a8fa29f3affba6a))
* document the direct JVM Z3 route and incremental sessions ([9467c02](https://github.com/gciatto/kt-constraints/commit/9467c027706401de0a5b6b86d829bcaefd05358a))
* document the real-expression DSL and decimal parsing ([797ede9](https://github.com/gciatto/kt-constraints/commit/797ede9bc06ba5ae7cfd43c1de367d7aa570c26d))
* document the SCIP and HiGHS routes through OR-Tools ([382968b](https://github.com/gciatto/kt-constraints/commit/382968b62d788a4b3d10dfa35f95acdf6b8fba88))
* explain the design and implementation of the pure-Kotlin solver ([db4a0a9](https://github.com/gciatto/kt-constraints/commit/db4a0a999881dbd268be4b98d9278aac5767b73b))
* explanation pages and the project's history and status ([1984ad7](https://github.com/gciatto/kt-constraints/commit/1984ad70b8486f661bdab977213229f5ce406303))
* how-to guides with tested Kotlin, Java and TypeScript examples ([e72d747](https://github.com/gciatto/kt-constraints/commit/e72d747b5c7917e6a92731718e7e26ec50a60f12))
* identify the engine actually packaged in browser Wasm ([03f3ff0](https://github.com/gciatto/kt-constraints/commit/03f3ff0c3ea11462eeb9924ce184cf6100691dd6))
* identify the engine actually packaged in browser Wasm ([92e9471](https://github.com/gciatto/kt-constraints/commit/92e94710925b6f26a1777b07c6a5ed29f7610240))
* installation guide for artifacts, source builds and solver runtimes ([d24befe](https://github.com/gciatto/kt-constraints/commit/d24befeeeeec47d24171196f24f6098210893e9e))
* **interop-java:** document primitive and exact number conversions ([39a06a2](https://github.com/gciatto/kt-constraints/commit/39a06a2d11082f05a7bf81cba185696393567282))
* **interop-java:** document the Java facade ([9a5ed0c](https://github.com/gciatto/kt-constraints/commit/9a5ed0c4dbde243377c890357e65b5b306eb0ef0))
* **interop-js:** document the TypeScript facade ([eb82519](https://github.com/gciatto/kt-constraints/commit/eb82519698d195c26f012d12c84eede23d8b2020))
* Java, TypeScript, browser and C tutorials with tested examples ([ac3256d](https://github.com/gciatto/kt-constraints/commit/ac3256d9cc762d6be34b00366028df3f9ad4f688))
* job-shop, traveling salesman, nurse rostering and pigeonhole recipes ([79f59e6](https://github.com/gciatto/kt-constraints/commit/79f59e6f52c61c9a4d956f7db70d481471b3c2a8))
* Kotlin tutorials and the N-Queens recipe with tested examples ([6f1e580](https://github.com/gciatto/kt-constraints/commit/6f1e58085ab56b995ecc7546ce3f6a7cfefed046))
* lazy batch satisfaction ([f6fca7b](https://github.com/gciatto/kt-constraints/commit/f6fca7bf50b7958250efceee4141b8401dd91f6f))
* link the pure Kotlin design from the roadmap absolutely, since the site build copies it out of the repository root ([ebc797b](https://github.com/gciatto/kt-constraints/commit/ebc797b03bbdc55640a4fa6a024701b76f54efac))
* M7 constraint-store design draft for review ([c70f454](https://github.com/gciatto/kt-constraints/commit/c70f454955f5978792324e7b9ac20eb333643f99))
* M8 design for extensible auto selection, portfolios, benchmarks and release ([5d355a7](https://github.com/gciatto/kt-constraints/commit/5d355a7af664604a752fd8f18f257f94b8d9921e))
* **mip:** design the SCIP and HiGHS routes through OR-Tools ([1276ac3](https://github.com/gciatto/kt-constraints/commit/1276ac3dfea0567cb4788f2b1bb549a26fcca868))
* move all documentation to the site and summarize benchmarks in the README ([df980f8](https://github.com/gciatto/kt-constraints/commit/df980f849aeb3e79317a59f0e12ec10ee986ed41))
* point the README and installation guide to GitHub Packages and release assets ([8a9da05](https://github.com/gciatto/kt-constraints/commit/8a9da05c4da1e38f0c5939a73b5e10d011c3bfb3))
* record bootstrap checks and platform limitations ([f99e78a](https://github.com/gciatto/kt-constraints/commit/f99e78a939ec2d47df52707963f0b1eec91e7a53))
* record initial M2 solve contract progress ([a305ae0](https://github.com/gciatto/kt-constraints/commit/a305ae0bf60a7267e76189e752740eca5d420aeb))
* record M1 modelling APIs and cross-platform validation ([2ffa648](https://github.com/gciatto/kt-constraints/commit/2ffa64882e6faadaa1247f0c21d234809d7e2bb4))
* record M2 real solving contracts and validation ([cf2f738](https://github.com/gciatto/kt-constraints/commit/cf2f7382aff97a0733c7f064b01b1fe860f241bb))
* record passing M1 CI matrix and rebase merge ([778b444](https://github.com/gciatto/kt-constraints/commit/778b44448d550afdd1775d465ac42b5d0169e99d))
* record the intermittent Kotest browser report loss and drop info logs ([bbbcb4c](https://github.com/gciatto/kt-constraints/commit/bbbcb4c9a326f9cd795a937fddf252f1062958d9))
* record the intermittent Kotest browser report loss and drop info logs ([8d547fb](https://github.com/gciatto/kt-constraints/commit/8d547fb27ac6f5aaac534de2d74590c9a70cee32))
* record the M3 engine catalogue, JS transports and Node SDK review ([0c6f48e](https://github.com/gciatto/kt-constraints/commit/0c6f48ef5787addfaca8c27cc3b3ef4f2a54f60a))
* record the M3 engine catalogue, JS transports and Node SDK review ([625cbac](https://github.com/gciatto/kt-constraints/commit/625cbacbf4acb3680d8ba711a6ce7f634452d4d3))
* record the resolved browser worker-termination race ([183ee2b](https://github.com/gciatto/kt-constraints/commit/183ee2b528e5100a185825ee46ddcad39bd299a1))
* record the resolved browser worker-termination race ([0ffed73](https://github.com/gciatto/kt-constraints/commit/0ffed73cbf3e562264f6330badd0e5a48c342429))
* reference pages and contributor guides ([564b67b](https://github.com/gciatto/kt-constraints/commit/564b67b471ee08fa0b2c6136868ac63901c55e77))
* register the experimental portable backend in project status ([f48ecc7](https://github.com/gciatto/kt-constraints/commit/f48ecc725e71b8e29c9ccdc62aa9d6576d6ed915))
* resync the site copies of the roadmap and architecture ([92d3be0](https://github.com/gciatto/kt-constraints/commit/92d3be06f8654bf1f789272df10bca008da389b4))
* rewrite the landing README around what the project offers today ([792b338](https://github.com/gciatto/kt-constraints/commit/792b338d8925c7d65eb7bc3b9bb55d940f01f36a))
* **roadmap:** Kotlin/Wasm is blocked on a Wasm variant of kt-math ([9a267a5](https://github.com/gciatto/kt-constraints/commit/9a267a59acc41030dbf1e9cd581dc20490923593))
* scaffold the documentation site, its API reference and its tested examples ([0121507](https://github.com/gciatto/kt-constraints/commit/0121507afc383dbfd7c33e76e8bd3947776127ea))
* settle the M7 fork, entailment and facade decisions ([fad45f5](https://github.com/gciatto/kt-constraints/commit/fad45f5843571c305025e089771a92e461f69f29))
* **solve-kotlin:** describe exact semantics and staged engine improvements ([54f7f48](https://github.com/gciatto/kt-constraints/commit/54f7f48055c53f925aa16781a87a78425f12810b))
* **solve-kotlin:** record blocked execution and reproducible validation steps ([12e3a10](https://github.com/gciatto/kt-constraints/commit/12e3a1041cfc610d7ad22efbce29848ec254719d))
* **solve-kotlin:** record cross-platform measurements and how to take them ([923a307](https://github.com/gciatto/kt-constraints/commit/923a307fe58cad986b43d3a71f0a3979d745acf5))
* **solve-kotlin:** record native linear propagation and the updated measurements ([458581b](https://github.com/gciatto/kt-constraints/commit/458581b7f1ee1e632fff71c8aadfd0a6e0b3368c))
* **solve-kotlin:** record order literals, the multi-seed benchmark and its all-different finding ([33e41bd](https://github.com/gciatto/kt-constraints/commit/33e41bdc962f7e3b56dc912b0db195ea4eeb39ae))
* **solve-kotlin:** record small-domain value literals and the updated measurements ([049e038](https://github.com/gciatto/kt-constraints/commit/049e0385c9f7a7ca006b4a14ed7286bbd29cf052))
* **solve-kotlin:** record the executed suites and the fixes found by running them ([d2cab9c](https://github.com/gciatto/kt-constraints/commit/d2cab9c8492df70574e2b6e16efec09e6f943ee1))
* **solve-kotlin:** record the profiling-driven optimisations and the measured gaps ([2176cf5](https://github.com/gciatto/kt-constraints/commit/2176cf5ad3c2805c008bc25b73a28dce80ac19b7))
* **solve-portfolio:** record cross-route measurements and the selection rules they support ([3d90b1d](https://github.com/gciatto/kt-constraints/commit/3d90b1dac911749e2049bd7be4a9394ab83a4f1a))
* **solve-portfolio:** record MiniZinc route measurements and the rules they add ([78f2a58](https://github.com/gciatto/kt-constraints/commit/78f2a584e15afeb3be09d84dbb83f2a8bbcee7d4))
* specify bounded integer MIP route semantics ([4a790e3](https://github.com/gciatto/kt-constraints/commit/4a790e37a2780536a5e0b4cafc962aac60639f41))
* specify bounded integer MIP route semantics ([575f6a1](https://github.com/gciatto/kt-constraints/commit/575f6a130849f428e5619cb65f192a7199727dfb))
* store forks, explanations and facades ([b0a4f99](https://github.com/gciatto/kt-constraints/commit/b0a4f99c310a509e5a8d283ef901c79d0693ec28))
* Sudoku, graph colouring, knapsack, assignment and bin packing recipes ([8452a72](https://github.com/gciatto/kt-constraints/commit/8452a723721c39e4517936f813c53f9146deae58))
* **z3:** design incremental sessions, checkpoints and unsat cores ([177c9cc](https://github.com/gciatto/kt-constraints/commit/177c9cc3f784531d848bb7a752f88c1bb0f5858c))
* **z3:** design the direct Z3 JVM route and pin z3-turnkey ([7d625de](https://github.com/gciatto/kt-constraints/commit/7d625dec81f083268edd656c98dd64a21e7232eb))
* **z3:** design the Kotlin/Native C API route ([0dd6c70](https://github.com/gciatto/kt-constraints/commit/0dd6c706103e3055cbf75423a3a2a5864790813a))
* **z3:** document the Native C API route and refutation-bounded optimization ([dd22a8c](https://github.com/gciatto/kt-constraints/commit/dd22a8cc70ef663b086b168607987678f8e1c994))
* **z3:** document the Node Wasm route and its browser limitation ([6ea621b](https://github.com/gciatto/kt-constraints/commit/6ea621bb3ea26439c341dc5799779593f0205da4))
* **z3:** document verified browser Wasm execution and its isolation requirements ([557fa5e](https://github.com/gciatto/kt-constraints/commit/557fa5e777479960ac7b1faf1de0bcc098ee3606))
* **z3:** plan the shared-port JS/Wasm slice ([0feff2a](https://github.com/gciatto/kt-constraints/commit/0feff2a7181cf94ed264a3d5439985d4c77d1ded))

### Performance improvements

* **choco:** merge linear sums and cardinalities into one map ([7795f11](https://github.com/gciatto/kt-constraints/commit/7795f113ab9d56bb18e76093b0cd44a301942ef7))
* **minizinc:** measure independent execution phases ([76e4f51](https://github.com/gciatto/kt-constraints/commit/76e4f51d838000d8e15432afc05c2c56fa57884d))
* **minizinc:** measure phases for explicitly selected engines ([c5b2c03](https://github.com/gciatto/kt-constraints/commit/c5b2c03abd1d524f6f731af1467cde88c0278757))
* **minizinc:** measure phases for explicitly selected engines ([b992b06](https://github.com/gciatto/kt-constraints/commit/b992b067785e5504feca70a602259284e85e0a20))
* **mip:** merge affine sums into one map instead of copying at every addition ([d8e1f34](https://github.com/gciatto/kt-constraints/commit/d8e1f348efa7158a2386f1105f22c5f3283eeda2))
* **model:** track declared ids and constraint names incrementally ([fad2340](https://github.com/gciatto/kt-constraints/commit/fad2340c7f1ec714db7b105291b65c3ad684713c))
* **solve-kotlin:** accumulate integer embeddings in one pass ([17124e1](https://github.com/gciatto/kt-constraints/commit/17124e1e4fd097e01cfcf443bc5cf0d4339872b5))
* **solve-kotlin:** choose SAT decisions from an activity heap instead of a linear scan ([7c673b3](https://github.com/gciatto/kt-constraints/commit/7c673b3f06f8acedf96288b65b38e533f897ac26))
* **solve-kotlin:** compact arithmetic projections during elimination ([812a3a5](https://github.com/gciatto/kt-constraints/commit/812a3a54359d0378ab669ad62ecd32ea5b9c9dc0))
* **solve-kotlin:** compile discrete real predicates into exact SAT inequalities ([5d6cea6](https://github.com/gciatto/kt-constraints/commit/5d6cea6bc7d8dbf05f121b1dad5bb18ef44389b7))
* **solve-kotlin:** delete the worse half of learned clauses by block distance at restarts ([449491f](https://github.com/gciatto/kt-constraints/commit/449491f89399b8b2e63021d93ff71e58a1e6cb14))
* **solve-kotlin:** drop learned literals implied by the rest of the clause ([f0c250e](https://github.com/gciatto/kt-constraints/commit/f0c250e3c584c7a13eca898d41276e9f599f6ab7))
* **solve-kotlin:** explain linear propagations and conflicts with just enough largest-coefficient literals ([99c75e2](https://github.com/gciatto/kt-constraints/commit/99c75e21beb1e321a2f58d40abc2b08fd1a3ebfa))
* **solve-kotlin:** inspect shared expression nodes once during support checks ([5b7207f](https://github.com/gciatto/kt-constraints/commit/5b7207f8d4c0b95ab1473610b9ab575f86283905))
* **solve-kotlin:** keep Long arithmetic out of the SAT hot loop, which Kotlin/JS emulates in software ([fc426c2](https://github.com/gciatto/kt-constraints/commit/fc426c2451574f34f288c6f380a54b8b37d25308))
* **solve-kotlin:** keep SAT propagation, analysis and decisions free of suspension, pausing between trail literals ([e1a04e2](https://github.com/gciatto/kt-constraints/commit/e1a04e2402a366bd9a31ac032ca19726175ea7b5))
* **solve-kotlin:** refute all-different scopes with fewer values than operands during presolve ([92c84ef](https://github.com/gciatto/kt-constraints/commit/92c84efa49868a1ad41e0a887211809cef1e2211))
* **solve-kotlin:** require every value of a permutation all-different to be taken ([eb708cb](https://github.com/gciatto/kt-constraints/commit/eb708cb49bf41fbf52994be82537e613eb0b6c73))
* **solve-kotlin:** restart SAT search on the Luby schedule ([33e5190](https://github.com/gciatto/kt-constraints/commit/33e51903245fd7bb7edc6acce0fc0733086e1cb5))
* **solve-kotlin:** skip satisfied clauses through blocker literals in watch lists ([35ceadb](https://github.com/gciatto/kt-constraints/commit/35ceadb920d74f2383cee51c96e338c1a0b035bb))

### Tests

* absolute value in the Kotlin DSL and at the TypeScript boundary ([b8a1dc6](https://github.com/gciatto/kt-constraints/commit/b8a1dc6b14a6f7572ddb2122665147b9a61e2bbb))
* benchmark catalogue of eight families with independently known answers ([97f5fbb](https://github.com/gciatto/kt-constraints/commit/97f5fbb911c73e871349182a4050a084022e2f53))
* **choco:** 40,000-term sums must translate within five seconds ([5872353](https://github.com/gciatto/kt-constraints/commit/587235368c9b952127e13a149fd14d0139a4984a))
* **choco:** specify the direct Choco route ([8f9da32](https://github.com/gciatto/kt-constraints/commit/8f9da329c995c154673b7d09ec424abb6b2016c7))
* **choco:** the propagating store must pass the shared store contract ([53cb1df](https://github.com/gciatto/kt-constraints/commit/53cb1dfbfa4bb8e517dac8bda8e414cbf3ecd321))
* compute the assignment benchmark optimum by subset dynamic programming, not n! permutations ([e8797e2](https://github.com/gciatto/kt-constraints/commit/e8797e2a5a154e773e1bd52ea5517c858d2fec71))
* compute the integer-program benchmark optimum by capacity dynamic programming, not 5^n points ([7be4dba](https://github.com/gciatto/kt-constraints/commit/7be4dba5f706d153d0f99bf5b22a52b12b63ea9c))
* **core:** define exact algebra evaluation and traversal contracts ([d429134](https://github.com/gciatto/kt-constraints/commit/d42913450f9f24a59962af9e42537a2ea2bc3917))
* **core:** define typed variable evaluation and undefined values ([a82cbf8](https://github.com/gciatto/kt-constraints/commit/a82cbf87ce2351d74879f525b4024d04c0529c40))
* **core:** fold, variables and scope checks must survive 100,000-deep chains ([39e861e](https://github.com/gciatto/kt-constraints/commit/39e861e3aa4d43413ff8bfdd2277edf76324847c))
* **core:** share seeded contracts and verify Java consumers ([422a242](https://github.com/gciatto/kt-constraints/commit/422a2423be99755e6a820f321f6ac10f2b629643))
* **core:** specify exact integer and finite domain semantics ([42ae2c0](https://github.com/gciatto/kt-constraints/commit/42ae2c0e5bd387ae41de0bb2234a849f5f9f1042))
* **core:** specify preserved global constraint semantics ([b87b416](https://github.com/gciatto/kt-constraints/commit/b87b4167c6d9da7185f3357aacb6fd373543b7a1))
* **cpsat:** specify the direct CP-SAT route ([043860a](https://github.com/gciatto/kt-constraints/commit/043860a984968bc597000f53d546886a5172d586))
* explicit subcircuit ranges in MiniZinc text and formatting ([ed54c1a](https://github.com/gciatto/kt-constraints/commit/ed54c1a669114d4654f78fa4276f61c5baa0ceb6))
* format M2 solver contracts and fixtures ([8c5d4df](https://github.com/gciatto/kt-constraints/commit/8c5d4dfb5d4fa9735f83e1ee39fef392e8e210f2))
* format milestone contracts and align file names ([3ae3f82](https://github.com/gciatto/kt-constraints/commit/3ae3f82b7c4583b93bdd9f05b0e94c30e6747495))
* format solve and backend eligibility contracts ([e602292](https://github.com/gciatto/kt-constraints/commit/e602292a99975ada3c1fbc46621cd2716c72cd29))
* HiGHS factory limit and engine flags in option and benchmark tests ([e6f0902](https://github.com/gciatto/kt-constraints/commit/e6f09021983f9044866b0170341efd869a7f81ab))
* **interop-java:** constraint stores from javac-compiled code ([1cdd1e7](https://github.com/gciatto/kt-constraints/commit/1cdd1e77c9384b9595144f1c0601e7d2686dadfa))
* **interop-java:** exercise the portable backend through Java ([583493b](https://github.com/gciatto/kt-constraints/commit/583493bbd412bdf0ed149f0c8a28a14e50bd33fb))
* **interop-java:** incremental sessions from javac-compiled code ([c56dd0e](https://github.com/gciatto/kt-constraints/commit/c56dd0e9516cbe11029410a282886ff557f0dcb6))
* **interop-java:** make the cancellation example slow enough to cancel ([c855d73](https://github.com/gciatto/kt-constraints/commit/c855d7347af76b6f68f22b53fa1a2bb4033c42d6))
* **interop-java:** specify the Java modelling and solving boundary ([2d8467e](https://github.com/gciatto/kt-constraints/commit/2d8467ed07b86bba5545d5e7283a97b010878699))
* **interop-js:** absolute MiniZinc asset URLs and a global shim for the browser bundle ([140eb11](https://github.com/gciatto/kt-constraints/commit/140eb11240c6d6337015f61b6bdb018ac2203843))
* **interop-js:** constraint stores from type-checked TypeScript ([38cf8db](https://github.com/gciatto/kt-constraints/commit/38cf8db7d0866d3b97e27d1fea5eea0724f69cb6))
* **interop-js:** drop redundant string casts in the portable facade spec ([e3b9983](https://github.com/gciatto/kt-constraints/commit/e3b9983e510b07a001d2a254f74dfa8450db1daf))
* **interop-js:** exercise the portable solver through exported JavaScript ([0ce4c05](https://github.com/gciatto/kt-constraints/commit/0ce4c05a1cfd3558d70b1540fc445cc2f4bedf36))
* **interop-js:** run shared TypeScript checks on Z3 and MiniZinc in Node and Chromium ([00f0c5f](https://github.com/gciatto/kt-constraints/commit/00f0c5fd983ca2c610805c19bc252b9dfac38b22))
* **interop-js:** specify exact reals and incremental sessions in TypeScript ([50b0060](https://github.com/gciatto/kt-constraints/commit/50b0060e14e2b656ac182d2c97691be6f2e6c2a4))
* **interop-js:** specify the TypeScript boundary against the packaged ES module ([dcf7af8](https://github.com/gciatto/kt-constraints/commit/dcf7af8d14ff9ce7c165d7d4fe75e727f936ec9a))
* inverse, circuit, cumulative, 0/1 conversion and counting/ordering helpers ([a14c279](https://github.com/gciatto/kt-constraints/commit/a14c279eedb98ddfa6039a55367b0c69447462e4))
* **js:** min and max at the TypeScript boundary ([3cfdeb6](https://github.com/gciatto/kt-constraints/commit/3cfdeb67167d2daf22f66f90f6c5bfa16ae1534b))
* manual CLP-style store benchmarks for Choco and Z3 ([87ed675](https://github.com/gciatto/kt-constraints/commit/87ed675d1dc1b0bbb83befedcbb75c924ecfccfd))
* min and max integer globals across the IR, features and every backend ([32a614a](https://github.com/gciatto/kt-constraints/commit/32a614afa741fde1ed9b23bbafe013b45a81f204))
* **minizinc:** capture browser console output outside Kotest tests ([6d552d2](https://github.com/gciatto/kt-constraints/commit/6d552d2d3d316e0ebe4e1a95475fb9d6b5144e45))
* **minizinc:** capture browser console output outside Kotest tests ([058ac43](https://github.com/gciatto/kt-constraints/commit/058ac43465db2d531199c5a05acec2c859791c28))
* **minizinc:** compare all engine globals and verify actual integer boundaries ([d6a893c](https://github.com/gciatto/kt-constraints/commit/d6a893c04de13779de1955ddccfd634515d1b769))
* **minizinc:** compare all engine globals and verify actual integer boundaries ([f9c2ff7](https://github.com/gciatto/kt-constraints/commit/f9c2ff78f660cb03a4f63d0c15aecfa1443d1135))
* **minizinc:** cover JS runtime ownership and environment rejection ([942d4d2](https://github.com/gciatto/kt-constraints/commit/942d4d2c69a71d58b6687d016b9b7d61d66dedfa))
* **minizinc:** cover JS runtime ownership and environment rejection ([5411bbd](https://github.com/gciatto/kt-constraints/commit/5411bbdc122abf6d4931863042d9fe6d698cc9d4))
* **minizinc:** differential catalogue on chuffed, cp-sat, highs and cbc ([34affd0](https://github.com/gciatto/kt-constraints/commit/34affd080ef07231baa9c53913ddc5e88b49f82c))
* **minizinc:** isolate temp-file check from the reload probe ([7692aa6](https://github.com/gciatto/kt-constraints/commit/7692aa6c72c87b7570b11059aaf1b9be192564ff))
* **minizinc:** isolate temp-file check from the reload probe ([2301646](https://github.com/gciatto/kt-constraints/commit/2301646011034bd25d07b2f9db803d14f99f89ab))
* **minizinc:** preserve strict CP terminal status validation ([23ee54a](https://github.com/gciatto/kt-constraints/commit/23ee54a8912a79b60fd1b4c24f6682b75b8ffb9b))
* **minizinc:** preserve strict CP terminal status validation ([8ee7729](https://github.com/gciatto/kt-constraints/commit/8ee77293649007941079a36d0f3210f76f569e2e))
* **minizinc:** register engine contracts before spec instantiation ([ccb0aa2](https://github.com/gciatto/kt-constraints/commit/ccb0aa2f9a3fe25a961c89db3ee8af009648bec6))
* **minizinc:** register engine contracts before spec instantiation ([1b72fbf](https://github.com/gciatto/kt-constraints/commit/1b72fbf8469cc4c488162ddbd7c2d75a2458581d))
* **minizinc:** report JS runtime load failures from every test ([31a4fe3](https://github.com/gciatto/kt-constraints/commit/31a4fe3377f2b5c5fd8253788019e066e5b9e209))
* **minizinc:** report JS runtime load failures from every test ([730414d](https://github.com/gciatto/kt-constraints/commit/730414d69cf220e9211fb3eed097a5e0ea1d3fcf))
* **minizinc:** require a missing Node CLI to be unavailable, not a crash ([11151c6](https://github.com/gciatto/kt-constraints/commit/11151c6939cd15128d0a2a6594239847a8382638))
* **minizinc:** require a missing Node CLI to be unavailable, not a crash ([baafff6](https://github.com/gciatto/kt-constraints/commit/baafff6589bf7d2498825579af92a0f61d876916))
* **minizinc:** require browser close during run start to raise no page error ([98910a8](https://github.com/gciatto/kt-constraints/commit/98910a835a421d2fec0bc69052dd554746cd9d27))
* **minizinc:** require browser close during run start to raise no page error ([3c8f80b](https://github.com/gciatto/kt-constraints/commit/3c8f80b702837436676adfda9f147572620058d5))
* **minizinc:** require Chuffed browser Wasm runtime evidence ([5a3b549](https://github.com/gciatto/kt-constraints/commit/5a3b54996d93a42d3a6280a1abc47972320ccf50))
* **minizinc:** require Chuffed browser Wasm runtime evidence ([b80914c](https://github.com/gciatto/kt-constraints/commit/b80914cd086468c0825efb34d61a72a6e4dfba6c))
* **minizinc:** require deadline executors to stop on close ([5653900](https://github.com/gciatto/kt-constraints/commit/5653900e6e71c8172e2dc7156f36dc6a8c007a7b))
* **minizinc:** require deterministic lossless Gecode lowering ([5002f49](https://github.com/gciatto/kt-constraints/commit/5002f49895e407607982dc5d64f078d5c8dfd620))
* **minizinc:** require Node backend close to leave no CLI temporary files ([fe42a89](https://github.com/gciatto/kt-constraints/commit/fe42a891ed6e8490a3f83c56bb371e84cba2beab))
* **minizinc:** require Node backend close to leave no CLI temporary files ([3dea7bf](https://github.com/gciatto/kt-constraints/commit/3dea7bfb3a94a283ae402011ad602ce717dfc571))
* **minizinc:** require real Gecode conformance and process cleanup ([d2a861e](https://github.com/gciatto/kt-constraints/commit/d2a861eecc2407d2a6d57cd13cef1cb5724d6f5a))
* **minizinc:** rerun explicitly requested external runtimes ([73272b1](https://github.com/gciatto/kt-constraints/commit/73272b16b6d15cf95365ec86dc3d9c85c95f1565))
* **minizinc:** specify bounded MIP support and witness completion ([7668e4f](https://github.com/gciatto/kt-constraints/commit/7668e4f4cbbfad56ec40fad0758cf2df979a57bc))
* **minizinc:** specify bounded MIP support and witness completion ([360bf18](https://github.com/gciatto/kt-constraints/commit/360bf18f6927663bd6af2118617a935710ba2500))
* **minizinc:** specify engine identity range and catalogue contracts ([f631918](https://github.com/gciatto/kt-constraints/commit/f63191877de7bb1e708e6628f8867f938c06f2c5))
* **minizinc:** specify engine identity range and catalogue contracts ([7c995a0](https://github.com/gciatto/kt-constraints/commit/7c995a069d61cc2985f0433fa651dc45f839468b))
* **minizinc:** specify real JS transport contracts and route identity ([0814739](https://github.com/gciatto/kt-constraints/commit/081473948dddf5ec7233433c513d693f17e7ef05))
* **minizinc:** specify real JS transport contracts and route identity ([8ad2f1c](https://github.com/gciatto/kt-constraints/commit/8ad2f1cabb32eff01de80dcbc1759f750ff5b80b))
* **minizinc:** validate protocol evidence and decoded assignments ([f879463](https://github.com/gciatto/kt-constraints/commit/f879463e8250d544c641ecd57bac8ff2058a9298))
* **minizinc:** verify JS deadlines and reject missing runtime reports ([4cafcbc](https://github.com/gciatto/kt-constraints/commit/4cafcbcfc4e4f54d361c6ad4744ed5a6f4245b92))
* **minizinc:** verify JS deadlines and reject missing runtime reports ([325d637](https://github.com/gciatto/kt-constraints/commit/325d6374ea42c63a7dd92115baf405c6e70275b1))
* **mip:** a 250-variable allDifferent must lower within five seconds ([76e54f0](https://github.com/gciatto/kt-constraints/commit/76e54f0e76597eb5f5a2c380fe5001eb872626af))
* **mip:** specify the SCIP and HiGHS routes through OR-Tools ([e241b64](https://github.com/gciatto/kt-constraints/commit/e241b64fa0c0b82612ff86eeb2671e13424a2309))
* **model:** 40,000 named constraints must be added within five seconds ([5c5db21](https://github.com/gciatto/kt-constraints/commit/5c5db215d222b019885e7060e40ff57ab65fad68))
* **model:** define labels projection and objective isolation ([39d4730](https://github.com/gciatto/kt-constraints/commit/39d47301a3fdb984636922bf82f98ad632cecd63))
* **model:** expose mutable snapshot collections ([8e1fa40](https://github.com/gciatto/kt-constraints/commit/8e1fa400f17a951123d6712a80ca1d687a96b488))
* **portfolio:** bundled default routes per platform and auto solving ([47d44f1](https://github.com/gciatto/kt-constraints/commit/47d44f12adba79072f504e466da669b6fe767149))
* **portfolio:** fallback and race never turn timeouts or failures into proofs ([094da25](https://github.com/gciatto/kt-constraints/commit/094da256d4f00a5aee53f354208f1510164db6e6))
* **portfolio:** manual cross-route benchmark with correctness and auto overhead ([24f5a65](https://github.com/gciatto/kt-constraints/commit/24f5a6504a9a8d7ac7fab1029d216838a94d193a))
* preserve contract filename casing on all platforms ([0949aac](https://github.com/gciatto/kt-constraints/commit/0949aacfe33709458896f6798a8921512e797ef0))
* regular, subcircuit, bin packing, 2D non-overlap, distinct values and soft constraints ([3bd4f74](https://github.com/gciatto/kt-constraints/commit/3bd4f7460e653dd341ea435a2ad914e93550082b))
* run wall-clock scaling guards on the JVM only ([47bd660](https://github.com/gciatto/kt-constraints/commit/47bd6600983f0d70b7f41931a067b037335da3f6))
* seeded benchmark instances and a single-machine scheduling family with a closed-form optimum ([cbf42da](https://github.com/gciatto/kt-constraints/commit/cbf42da602092535cf716a5ac8716c1538327aba))
* share the oracle differential catalogue and runner through test-solve ([eb4fa8c](https://github.com/gciatto/kt-constraints/commit/eb4fa8c1bda075c9cdd3bfa900e83fdc8ddc8d4e))
* shared constraint-store contract, run against Z3 on JVM, JS and Native ([e27754f](https://github.com/gciatto/kt-constraints/commit/e27754fd4132b33284797dd6d4f4ca4f5f4a6d8d))
* simplify differential collection and cover oracle size cap ([a187e32](https://github.com/gciatto/kt-constraints/commit/a187e32fdcb607eaf1629e60fb5b3840a0fa87cd))
* **solve-kotlin:** add manual phase benchmark workloads ([16b65fc](https://github.com/gciatto/kt-constraints/commit/16b65fc333acac9d04274aed44e9fe6167dd7a83))
* **solve-kotlin:** all-different with more operands than values is a presolve conflict ([c6adc37](https://github.com/gciatto/kt-constraints/commit/c6adc37c7369d20e0b314b6c2704b61085044f3e))
* **solve-kotlin:** bind circuit gate values once instead of a redundant non-null assertion ([d89e264](https://github.com/gciatto/kt-constraints/commit/d89e264e9c76e631327b2acc2fac5245a692c8b1))
* **solve-kotlin:** bound work after an arithmetic contradiction ([c2a1b81](https://github.com/gciatto/kt-constraints/commit/c2a1b81413baed0cec76e827e1ad75ea16f01ddb))
* **solve-kotlin:** constant element lookups in objectives and reified comparisons ([9e5b78a](https://github.com/gciatto/kt-constraints/commit/9e5b78a4e9fa1e874020b12dbe1692028903102a))
* **solve-kotlin:** cover arithmetic resource limits and cancellation ([12a1514](https://github.com/gciatto/kt-constraints/commit/12a15145d2732f18aef9bd4825d087ba78a25eac))
* **solve-kotlin:** cover exact and interruptible integer embeddings ([85dbc1c](https://github.com/gciatto/kt-constraints/commit/85dbc1cfc08a0465ff929f8d624f1b23607a8658))
* **solve-kotlin:** cover SAT interruption and consumer isolation ([c936227](https://github.com/gciatto/kt-constraints/commit/c936227fe942f8f3007442403e07d5d459575fdd))
* **solve-kotlin:** cover scoped definedness limits and session recovery ([233e0b9](https://github.com/gciatto/kt-constraints/commit/233e0b9b69c2ae44f650f56eaede911d19c48993))
* **solve-kotlin:** cover shrinking sums and one-bit signed products ([c5f467d](https://github.com/gciatto/kt-constraints/commit/c5f467d377940d93ff584a7b514b57e1f969927f))
* **solve-kotlin:** keep the order-literal precedence model within the oracle's quick enumeration range ([19aa1c6](https://github.com/gciatto/kt-constraints/commit/19aa1c69e3a9c64be118bf5cff7023317d5ad4dd))
* **solve-kotlin:** linear sums with coefficients beyond machine range stay exact ([c63a234](https://github.com/gciatto/kt-constraints/commit/c63a234bf5873748bb8f527b0e2ee3bf944d8114))
* **solve-kotlin:** multi-seed family benchmark reporting median, spread and timeouts ([12a0670](https://github.com/gciatto/kt-constraints/commit/12a0670677a66b151f0b935a5297c8cc72fea467))
* **solve-kotlin:** opt-in cross-platform benchmark of the seeded catalogue on every target ([b1bcf95](https://github.com/gciatto/kt-constraints/commit/b1bcf95641ae6b1f086b285cb197854be8c60ded))
* **solve-kotlin:** require exact SAT lowering for discrete real predicates ([51c2df6](https://github.com/gciatto/kt-constraints/commit/51c2df6a76ba41425b68e91f6b2475dc5c963d5d))
* **solve-kotlin:** require referential support traversal for shared expressions ([3cb38eb](https://github.com/gciatto/kt-constraints/commit/3cb38ebc4d55986017900fd5e9addeeba2d18016))
* **solve-kotlin:** require snapshot isolation for check assumptions ([0d622b6](https://github.com/gciatto/kt-constraints/commit/0d622b67199bf0d8d0ecb85aca42e5d13d273f29))
* **solve-kotlin:** specify backend conformance and mixed exact semantics ([bca1177](https://github.com/gciatto/kt-constraints/commit/bca11772ac4bbbd257940ad63832e379a4a07689))
* **solve-kotlin:** specify exact domain restriction and rollback ([7b96d7e](https://github.com/gciatto/kt-constraints/commit/7b96d7e02226a44dbd6aafbc198ee227e825234b))
* **solve-kotlin:** specify exact integer and bit-vector circuits ([8609336](https://github.com/gciatto/kt-constraints/commit/860933678314f9e82f67aaeb252b46b14c597cdb))
* **solve-kotlin:** specify exact linear feasibility and witnesses ([1475ce0](https://github.com/gciatto/kt-constraints/commit/1475ce08c88c146bc666cab752c6a860632b437f))
* **solve-kotlin:** specify incremental SAT semantics with truth tables ([0af8c5f](https://github.com/gciatto/kt-constraints/commit/0af8c5f47085f4c3d4ca8991759d7a4df7cc081d))
* **solve-kotlin:** specify native linear constraints in the SAT kernel and linear sums in every Boolean context ([e133b6e](https://github.com/gciatto/kt-constraints/commit/e133b6ee519db90757fb2f6238ea7138d83a63d6))
* **solve-kotlin:** specify order literals for inequalities between shifted medium-domain variables ([201b208](https://github.com/gciatto/kt-constraints/commit/201b208cdde6443375e3cf1f0fd17717d8891b8b))
* **solve-kotlin:** specify reusable Boolean circuit gates ([a62b69e](https://github.com/gciatto/kt-constraints/commit/a62b69eea2a8feacf0d0108fd5fed81331bf80a4))
* **solve-kotlin:** specify small-domain value literals across all-different, equalities, tables and element indices ([1ac1cd5](https://github.com/gciatto/kt-constraints/commit/1ac1cd597dc67fe571ee29f4a81df50b3e3ff203))
* **solve-kotlin:** specify sound exact integer presolve ([10a57fb](https://github.com/gciatto/kt-constraints/commit/10a57fbf0ae78ebe4a8d25b6730a259748301b67))
* **solve-kotlin:** time benchmark phases through a suspending inline helper ([f91c4a8](https://github.com/gciatto/kt-constraints/commit/f91c4a8ab52ee2bdc4d30a59e1bfe7d35c8107bb))
* **solve-kotlin:** top-level disequalities between shifted small-domain variables and their differences ([d98d072](https://github.com/gciatto/kt-constraints/commit/d98d072b2b87f3d1ea40d2b2cebbe451687cb725))
* **solve-kotlin:** use exact literal constructors in snapshot regression ([e4c5998](https://github.com/gciatto/kt-constraints/commit/e4c59981d4b0d45a51a4fb2528930618ed5ad433))
* **solve-kotlin:** verify theory lemmas across assumption and frame changes ([5b5e8a0](https://github.com/gciatto/kt-constraints/commit/5b5e8a0e905e88e0a5df7e9a851d4817fdab2d3b))
* **solve-portfolio:** lift Kotest's per-invocation limit on the long route benchmark ([7b39ba4](https://github.com/gciatto/kt-constraints/commit/7b39ba4fda201ddd09bc47b46cff827ec9775137))
* **solve-portfolio:** measure the unpublished kotlin/direct route alongside the bundled ones ([91058f5](https://github.com/gciatto/kt-constraints/commit/91058f5bd29d39b816499b0072146b2cce7e673d))
* **solve-portfolio:** opt-in benchmark of every bundled route plus kotlin/direct on each target ([dc588d8](https://github.com/gciatto/kt-constraints/commit/dc588d80f654ca0bc993281764f620f9d95a1dc4))
* **solve-portfolio:** run native tests against the pinned Z3, also in CI ([fa11bd6](https://github.com/gciatto/kt-constraints/commit/fa11bd62151463c379e10933ca000de038de6766))
* **solve-spi:** fakes declare the integer and global capabilities real backends declare ([bbc8a52](https://github.com/gciatto/kt-constraints/commit/bbc8a521cad65bd92193145910834cbbff5f7eef))
* **solve-spi:** feature-, capability- and platform-aware ranking with evidence hook ([3190577](https://github.com/gciatto/kt-constraints/commit/3190577aebaa8939d1b8acb6ef3be5a3845cc0ad))
* **solve-spi:** model features and problem classes for ranking ([047c9e8](https://github.com/gciatto/kt-constraints/commit/047c9e85e99d213ee4f49a504f74eacc4a4e8442))
* **solve:** define exact assignments and evidence-aware results ([e2b6c1d](https://github.com/gciatto/kt-constraints/commit/e2b6c1d21c155b70586810baa1a3847f98f7ce30))
* **solve:** reject contradictory optimal objective bounds ([1715445](https://github.com/gciatto/kt-constraints/commit/171544559b4b180eb62aceab9a879eecf0eec8e2))
* **solve:** share batch enumeration and lifecycle contracts ([c510288](https://github.com/gciatto/kt-constraints/commit/c510288a59aec1a443b9b7bae811115ae8c6356f))
* **solve:** specify a capped exact finite oracle and projection ([adbe072](https://github.com/gciatto/kt-constraints/commit/adbe07208eccc424d65d8f041904a6c2d88b9adf))
* specify decimal rational parsing and the real-expression DSL ([0586e41](https://github.com/gciatto/kt-constraints/commit/0586e415ec15fa14d517d599fe9f9bd26d19cd00))
* specify exact linear real arithmetic across core, routes and Z3 ([40219c1](https://github.com/gciatto/kt-constraints/commit/40219c134497ce4a991130e7880420d8b9df3d3c))
* specify fixed-width bit-vector semantics across core, routes and Z3 ([fda591f](https://github.com/gciatto/kt-constraints/commit/fda591f7de7a24df407d6352d10cf4c6426cede3))
* specify incremental sessions, checkpoint ownership and check results ([6a663e5](https://github.com/gciatto/kt-constraints/commit/6a663e5ae4f8e2087c5532ce0d9759cd18dde887))
* specify machine-integer bounds, exact narrowing reads and Java number conversions ([9db5af2](https://github.com/gciatto/kt-constraints/commit/9db5af2b38f2aa27dd492aa758f762b75f458e22))
* **spi:** batch satisfaction stops at the first solution ([867ced4](https://github.com/gciatto/kt-constraints/commit/867ced48f7588eb42e01821601a8c41a751c37f9))
* **spi:** cover late cancellation and cleanup failures ([880c310](https://github.com/gciatto/kt-constraints/commit/880c31088a7a72781a30f191678b0f612846157b))
* **spi:** distinguish backend deadlines from caller cancellation ([bb50f33](https://github.com/gciatto/kt-constraints/commit/bb50f33d021eaaf14669948ae8cf80e65bc5666a))
* **spi:** require explicit route-aware backend eligibility ([95b0233](https://github.com/gciatto/kt-constraints/commit/95b023393238accb4e7fd8f23156093bb9e88863))
* **spi:** specify resource-safe stream and solver lifecycles ([4b78b3f](https://github.com/gciatto/kt-constraints/commit/4b78b3fbee15a5ee234c84334b884e15cea2b8ea))
* **test-solve:** probe store entailment only on satisfiable oracle cases ([cab38f1](https://github.com/gciatto/kt-constraints/commit/cab38f1fc26bcbdf4c507929d5268231f37e7edb))
* **test-solve:** sessions accept variables declared after opening ([6b15ca5](https://github.com/gciatto/kt-constraints/commit/6b15ca56170cb81107528f0c69dd41d506fc445d))
* **test-solve:** store explanations, forks, exact reals, timeouts and cancellation ([311ffcf](https://github.com/gciatto/kt-constraints/commit/311ffcf5c703185d4deb6ac5a342abbd8d4b719d))
* **test-solve:** undoing a mark consumes it, so retry from a fresh mark ([907c9b8](https://github.com/gciatto/kt-constraints/commit/907c9b8908cb30882dc0b42b9650cd4e96f00076))
* typed per-backend solve options for CP-SAT, Choco, MIP and MiniZinc ([980c798](https://github.com/gciatto/kt-constraints/commit/980c7989fe44bf2fcf518a6ae2c7c80cc4d13ba3))
* widen wall-clock scaling budgets for slow CI runners ([49b2676](https://github.com/gciatto/kt-constraints/commit/49b26766311a1fdfa165fee599738bd9ddcf3c28))
* wrap the oracle's supported-node list ([81255aa](https://github.com/gciatto/kt-constraints/commit/81255aa2807a68c02682d85ff375b198876fbd8a))
* **z3:** cover negative and integral real numerals ([d85d60e](https://github.com/gciatto/kt-constraints/commit/d85d60e0228031253234314570f404257652e530))
* **z3:** cover session deadlines, close during a check and repeated frames ([e442ca4](https://github.com/gciatto/kt-constraints/commit/e442ca4c0b1085059d17fde5f766311b7d928d0f))
* **z3:** every Z3 route must declare the real and bit-vector capabilities it supports ([ce1da1b](https://github.com/gciatto/kt-constraints/commit/ce1da1b1ad7a315ff94dcda43df6a60b7646de47))
* **z3:** require sessions to stay usable after a cancelled check on JVM and Wasm ([8c08c2e](https://github.com/gciatto/kt-constraints/commit/8c08c2e2b9fd14d28100acca47c4e43d2e9cfc74))
* **z3:** require the direct JVM route to pass shared contracts and oracle differentials ([f237913](https://github.com/gciatto/kt-constraints/commit/f237913816090d28874fdffe5ba35315bf69ea78))
* **z3:** require the official z3-solver Wasm route to pass shared contracts on Node ([313435c](https://github.com/gciatto/kt-constraints/commit/313435ca0c6b8539defe9091aeef5ecf703af702))
* **z3:** require wide-domain optima to be proven within a deadline on every route ([65bb382](https://github.com/gciatto/kt-constraints/commit/65bb38225bad460a755bf7ba466b0afe064ccca3))
* **z3:** run the Wasm contracts in a cross-origin-isolated browser ([85c181a](https://github.com/gciatto/kt-constraints/commit/85c181a2f0a1eb4f2fb0be7250b045552e4f96d5))
* **z3:** specify the Kotlin/Native C API route and provision its host library ([87ddf28](https://github.com/gciatto/kt-constraints/commit/87ddf2850d8c7df62d1515955116509fbb5ef866))

### Build and continuous integration

* **core:** link checked multiplatform integer dependency ([3f95bce](https://github.com/gciatto/kt-constraints/commit/3f95bce9de04314445b7e4134e41e2fed265a205))
* exercise real Gecode and portable solver contracts ([62228ee](https://github.com/gciatto/kt-constraints/commit/62228eee17197b6eb13ceede89a7ebb040126cf9))
* install system EGL instead of a global bundle library path ([e326788](https://github.com/gciatto/kt-constraints/commit/e326788a384a9c30b191fe6eabddb355e789f32a))
* install system EGL instead of a global bundle library path ([72d279e](https://github.com/gciatto/kt-constraints/commit/72d279e70700f97c6304d99008a3c3e4d32f9bec))
* keep browser runtime logs and upload its reports on failure ([bade3af](https://github.com/gciatto/kt-constraints/commit/bade3af65fb81bb7fed8237970bff2320e45b897))
* keep browser runtime logs and upload its reports on failure ([514b44a](https://github.com/gciatto/kt-constraints/commit/514b44a155c546643a8c878514e3188596bc1910))
* load bundled Gecode libraries and verify both JS transports ([39b1979](https://github.com/gciatto/kt-constraints/commit/39b1979dc38208f585decbbc2c05064169ee3326))
* load bundled Gecode libraries and verify both JS transports ([4cedcc4](https://github.com/gciatto/kt-constraints/commit/4cedcc4c421f93b4ff2af6e8b297e5e786fe3e59))
* **minizinc:** declare JSON protocol and coroutine dependencies ([b067dd0](https://github.com/gciatto/kt-constraints/commit/b067dd003cdadb0fc902f80806d222a51dec1158))
* **minizinc:** pin the official JS runtime and browser toolchain ([8a9239d](https://github.com/gciatto/kt-constraints/commit/8a9239d53ce57e92bad2204c395dc50db408071e))
* **minizinc:** pin the official JS runtime and browser toolchain ([e13344b](https://github.com/gciatto/kt-constraints/commit/e13344baf340b6603d703fb9314f446b6351f528))
* npm publication of the TypeScript facade and Maven publication of the portfolio ([1f89311](https://github.com/gciatto/kt-constraints/commit/1f8931153c93c840c2defdd6faee479c6ed9f6a4))
* publish the portfolio module ([1152613](https://github.com/gciatto/kt-constraints/commit/1152613aa9bf75ae0bcec85dfbdff6d94d85218d))
* publish the working backends and both facades ([1f3e346](https://github.com/gciatto/kt-constraints/commit/1f3e34627cd4a59c9cb088db936adf7c9f3071a3))
* **release:** publish on GitHub Packages and attach the C library zips to releases ([3778f2a](https://github.com/gciatto/kt-constraints/commit/3778f2a7c1a182cfd07bf7f7ae8d907c2c6131fc))
* require five engines from a checksum-pinned MiniZinc bundle ([177c1b3](https://github.com/gciatto/kt-constraints/commit/177c1b3729986c4176f42d64d9c8bf30274b3521))
* require five engines from a checksum-pinned MiniZinc bundle ([5ce20c0](https://github.com/gciatto/kt-constraints/commit/5ce20c0f8cc9af9483837f5f11ad8df272a0ed27))
* run the TypeScript checks against the MiniZinc CLI and in Chromium ([aff35d1](https://github.com/gciatto/kt-constraints/commit/aff35d19d88c4129dfe73d785b7bc12945cda811))
* run the Z3 C API contracts on every native host ([adcfd77](https://github.com/gciatto/kt-constraints/commit/adcfd77361dff7b2343b49d8bbcd3c4bbb54d605))
* skip the duplicate pull-request run for develop and master ([d354e3e](https://github.com/gciatto/kt-constraints/commit/d354e3eaf7f6f6ba6977572c624396f69d4bfc9e))
* **solve-kotlin:** release Native test binaries for representative measurements ([3c04bed](https://github.com/gciatto/kt-constraints/commit/3c04bed96bd784b735e74c528f17447e9221f286))
* **solve-portfolio:** forward MINIZINC_EXECUTABLE to Kotlin/JS tests for the Node CLI route ([d560a5f](https://github.com/gciatto/kt-constraints/commit/d560a5f1987570539300cf7068e3d27751aeb2ce))
* **spi:** declare portable coroutine lifecycle dependency ([00f54ec](https://github.com/gciatto/kt-constraints/commit/00f54ec5bca478d7b767ea047c77d14faafc4f1b))
* verify the Z3 browser Wasm route alongside the MiniZinc browser job ([6c6b5ba](https://github.com/gciatto/kt-constraints/commit/6c6b5ba831d5749d5f4c50e95989f909637da388))

### General maintenance

* bootstrap kt-constraints ([42e0d1f](https://github.com/gciatto/kt-constraints/commit/42e0d1fe497b2fb53f62e1d62f4a1bacc6b6d0e7))
* **ci:** include the portable backend in Native test jobs ([3092936](https://github.com/gciatto/kt-constraints/commit/3092936b58a7efbbe6057aae63f117a6d27d5ee0))
* **interop-java:** add the portable backend to the test classpath ([939e9b0](https://github.com/gciatto/kt-constraints/commit/939e9b0477f57935bb7de9589b277517bbcea536))
* **interop-js:** add the portable backend only to JS tests ([8651140](https://github.com/gciatto/kt-constraints/commit/8651140f57d6903305c3e58781eff8375a0d197e))
* merge rebase-merged master into develop ([599493c](https://github.com/gciatto/kt-constraints/commit/599493cac14a7a7881c9cd311c85b250f1c3c2d0))
* **solve-kotlin:** register an isolated JVM benchmark task ([46a86c7](https://github.com/gciatto/kt-constraints/commit/46a86c7d434d004c462f6581f97c448390913a00))
* **solve-kotlin:** scaffold the portable backend module ([9f9f3cb](https://github.com/gciatto/kt-constraints/commit/9f9f3cb9e85970a74dfd2e0a82cfd2b75b0a5288))

### Style improvements

* format M2 lifecycle compiler and runtime code ([f814d43](https://github.com/gciatto/kt-constraints/commit/f814d4334a5032825b7d7b7f32e4bf2e3616c260))
* format portable solve contracts ([050222e](https://github.com/gciatto/kt-constraints/commit/050222e61a26eb6622720eadb71eb70187fe5968))
* **solve-kotlin:** apply ktlint formatting to the portable backend ([2c62e16](https://github.com/gciatto/kt-constraints/commit/2c62e16419c4d839b6b6e2fc6e0f7b781e1d3e8d))
* **test:** format MiniZinc engine contract specs ([6e9fa42](https://github.com/gciatto/kt-constraints/commit/6e9fa42e79f197c6f653d1c458b44ed566f9d8d2))
* **test:** format MiniZinc engine contract specs ([c8dec37](https://github.com/gciatto/kt-constraints/commit/c8dec37305544881d26cd6c65744a9aaf47a88de))

### Refactoring

* **interop-js:** one external interface per file ([a4e17c2](https://github.com/gciatto/kt-constraints/commit/a4e17c2743fc52e4f25b8820bd0e0cb1c03b694e))
* keep one top-level type per source file ([de455e2](https://github.com/gciatto/kt-constraints/commit/de455e283d22711621546c25f40903fd0d63deb5))
* **minizinc:** map the JS version probe rejection without a broad catch ([746d77b](https://github.com/gciatto/kt-constraints/commit/746d77b328fc3a27221d02bf0941b8785e813744))
* **minizinc:** map the JS version probe rejection without a broad catch ([aab42a5](https://github.com/gciatto/kt-constraints/commit/aab42a56dfc92e38c33ff3275c7cb73b73e69db3))
* **minizinc:** tighten async failures and runtime probes ([37385be](https://github.com/gciatto/kt-constraints/commit/37385be2c923070ee38c90a77f6049e70729e85f))
* **solve-kotlin:** separate SAT storage propagation and learning ([5f72e19](https://github.com/gciatto/kt-constraints/commit/5f72e19e38e9a7de2dd9bf2082bd89858e63b426))
* **solve-kotlin:** tidy session and budget imports ([69b94a8](https://github.com/gciatto/kt-constraints/commit/69b94a84c80fb1fbe5b77caf8878d0ed62f8a0bb))
* **solve-spi:** share the bounded-integer support checker of Choco and CP-SAT ([02dece1](https://github.com/gciatto/kt-constraints/commit/02dece19bf9e8d0a83de1fdc620c38a7ff05c7d8))
* **solve-spi:** StoreGuard and per-item support reports for constraint stores ([4430476](https://github.com/gciatto/kt-constraints/commit/44304767136f8619e3522917c1f4d107508df3a0))
* split crowded main packages into cohesive subpackages ([e95001d](https://github.com/gciatto/kt-constraints/commit/e95001d2c0ef47dd5fc00b24f2a463b3ef29584d))
* **test-solve:** share the benchmark environment lookup across test modules ([f241b7e](https://github.com/gciatto/kt-constraints/commit/f241b7ebbf1fa34e85c23fa55f7414eb165c0c3b))
* **z3:** share translation, streams and sessions behind a platform port ([16882ca](https://github.com/gciatto/kt-constraints/commit/16882ca7c06a57ac8300f04579fc3834ab01415b))
