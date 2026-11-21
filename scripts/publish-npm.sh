#!/usr/bin/env bash
# Publishes the TypeScript facade to npm. Usage: publish-npm.sh -PforceVersion=X.Y.Z [--dry-run]
# The token comes only from the NPM_TOKEN environment variable (a GitHub Actions secret), never from files in the repo.
set -euo pipefail
versionArg="$1"
dryRun="${2:-}"
./gradlew "$versionArg" :interop-js:jsNodeProductionLibraryDistribution
package="interop-js/build/dist/js/productionLibrary"
if [ "$dryRun" = "--dry-run" ]; then
    npm publish "$package" --access public --dry-run
    exit 0
fi
: "${NPM_TOKEN:?NPM_TOKEN must be set to publish}"
npmrc="$(mktemp)"
trap 'rm -f "$npmrc"' EXIT
echo "//registry.npmjs.org/:_authToken=${NPM_TOKEN}" >"$npmrc"
NPM_CONFIG_USERCONFIG="$npmrc" npm publish "$package" --access public
