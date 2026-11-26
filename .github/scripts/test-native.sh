#!/usr/bin/env bash
set -euo pipefail

./gradlew \
  ":core:${TARGET}Test" \
  ":model:${TARGET}Test" \
  ":dsl:${TARGET}Test" \
  ":solve:${TARGET}Test" \
  ":solve-spi:${TARGET}Test" \
  ":test-solve:${TARGET}Test" \
  ":solve-kotlin:${TARGET}Test" \
  ":solve-minizinc:${TARGET}Test" \
  ":solve-z3:${TARGET}Test" \
  ":solve-linear:${TARGET}Test" \
  ":solve-highs:${TARGET}Test" \
  ":solve-auto:${TARGET}Test" \
  --parallel --continue
