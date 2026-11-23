#!/usr/bin/env bash
# Publishes the TypeScript facade. Usage: publish-npm.sh -PforceVersion=X.Y.Z github|npmjs [--dry-run]
# - github: GitHub Packages, whose npm registry only takes packages scoped by the repository owner, so the package is
#   renamed @<owner>/kt-constraints-interop-js. Token: GITHUB_PACKAGES_TOKEN.
# - npmjs: the public npm registry, unscoped. Token: NPM_TOKEN.
# Tokens come only from environment variables (GitHub Actions secrets), never from files in the repository.
set -euo pipefail
versionArg="$1"
registry="$2"
dryRun="${3:-}"
./gradlew "$versionArg" :interop-js:jsNodeProductionLibraryDistribution
package="interop-js/build/dist/js/productionLibrary"
case "$registry" in
    github)
        owner="${GITHUB_REPOSITORY_OWNER:-gciatto}"
        staged="$(mktemp -d)"
        trap 'rm -rf "$staged"' EXIT
        cp -R "$package/." "$staged"
        (cd "$staged" && npm pkg set "name=@$owner/$(npm pkg get name | tr -d '"')")
        package="$staged"
        url="https://npm.pkg.github.com"
        tokenVariable=GITHUB_PACKAGES_TOKEN
        ;;
    npmjs)
        url="https://registry.npmjs.org"
        tokenVariable=NPM_TOKEN
        ;;
    *)
        echo "Unknown registry '$registry': expected github or npmjs" >&2
        exit 2
        ;;
esac
if [ "$dryRun" = "--dry-run" ]; then
    npm publish "$package" --access public --registry "$url" --dry-run
    exit 0
fi
token="${!tokenVariable:-}"
if [ -z "$token" ]; then
    echo "$tokenVariable must be set to publish to $registry" >&2
    exit 1
fi
npmrc="$(mktemp)"
trap 'rm -f "$npmrc"; rm -rf "${staged:-}"' EXIT
echo "//${url#https://}/:_authToken=${token}" >"$npmrc"
# A flag, not NPM_CONFIG_USERCONFIG: `npx semantic-release` exports npm_config_userconfig=~/.npmrc, which npm may prefer.
npm publish "$package" --access public --registry "$url" --userconfig "$npmrc"
