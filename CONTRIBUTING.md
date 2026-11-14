# Contributing

Use Conventional Commits (enforced by the pre-commit plugin, as in 2p-Kt). Open small PRs, supply Kotest
`FunSpec` tests, and keep the public API KDoc-documented. Backends must satisfy shared test contracts before being
advertised as available. Keep new cross-platform code in `commonMain` and avoid dependencies on 2p-Kt.

Run `./gradlew ktlintFormat`, then `./gradlew ktlintCheck detektAll jvmTest jsTest --parallel --continue` and report test/platform
coverage in PRs. Java public API changes require Java integration tests and JS facade changes require TypeScript tests.
