#!/usr/bin/env bash
set -euo pipefail

{
  echo '# Kt-Constraints CI summary'
  echo '| Stage | Outcome |'
  echo '| --- | --- |'
  echo "| Build and lint | $BUILD |"
  echo "| Multi-OS tests | $EXTENSIVE |"
  echo "| Documentation | $DOCS |"
  echo "| Release (opt-in) | $RELEASE |"
} >> "$GITHUB_STEP_SUMMARY"
