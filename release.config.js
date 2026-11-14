// Mirrors kt-math / 2p-Kt's Conventional Commits release process, but publishing is gated in GHA.
const version = '-PforceVersion=${nextRelease.version}';
const config = require('semantic-release-preconfigured-conventional-commits');
config.plugins.push(
  ['@semantic-release/exec', {
    publishCmd: `./scripts/publish-maven-central.sh ${version}`,
  }],
  '@semantic-release/github',
  '@semantic-release/git',
);
module.exports = config;
