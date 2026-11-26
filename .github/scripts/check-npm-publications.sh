#!/usr/bin/env bash
set -euo pipefail

./scripts/publish-npm.sh -PforceVersion=0.0.0-ci github --dry-run
./scripts/publish-npm.sh -PforceVersion=0.0.0-ci npmjs --dry-run
