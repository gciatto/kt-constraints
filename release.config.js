// Mirrors kt-math / 2p-Kt's Conventional Commits release process; publishing is gated in GHA (ENABLE_RELEASE).
//
// Live targets: Maven artifacts and the npm package on GitHub Packages, and the C shared library zips of
// :interop-c attached to the GitHub release. Maven Central and npmjs stay wired but off until the repository
// variables ENABLE_MAVEN_CENTRAL / ENABLE_NPMJS are 'true' (see build-and-deploy.yml).
//
// These commands are @semantic-release/exec templates: `${...}` is a template variable there, so shell variables
// are written as `$NAME`, without braces.
const version = '-PforceVersion=${nextRelease.version}';

const prepareCmd = `
./gradlew ${version} :interop-c:releaseAssets || exit 4
`.trim();

const publishCmd = `
./gradlew ${version} publishAllPublicationsToGithubRepository || exit 3
./scripts/publish-npm.sh ${version} github || exit 3
if [ "$ENABLE_MAVEN_CENTRAL" = "true" ]; then ./scripts/publish-maven-central.sh ${version} || exit 3; fi
if [ "$ENABLE_NPMJS" = "true" ]; then ./scripts/publish-npm.sh ${version} npmjs || exit 3; fi
`.trim();

const config = require('semantic-release-preconfigured-conventional-commits');
config.plugins.push(
  ['@semantic-release/exec', { prepareCmd, publishCmd }],
  ['@semantic-release/github', {
    assets: [{ path: 'interop-c/build/distributions/kt-constraints-c-*.zip' }],
  }],
  '@semantic-release/git',
);
module.exports = config;
