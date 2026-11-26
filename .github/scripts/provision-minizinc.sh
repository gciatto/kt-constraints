#!/usr/bin/env bash
set -euo pipefail

curl --fail --location --retry 3 -o "$RUNNER_TEMP/minizinc.tgz" \
  https://github.com/MiniZinc/MiniZincIDE/releases/download/2.10.1/MiniZincIDE-2.10.1-x86_64-linux-gnu.tgz
echo "b63dd88491d47b05d0eb0ff4aa1112d1e6f77292c023800b20b98851458ce01c  $RUNNER_TEMP/minizinc.tgz" | sha256sum --check
mkdir "$RUNNER_TEMP/minizinc"
tar -xzf "$RUNNER_TEMP/minizinc.tgz" -C "$RUNNER_TEMP/minizinc" --strip-components=1
echo "$RUNNER_TEMP/minizinc/bin" >> "$GITHUB_PATH"
# fzn-gecode finds bundled Qt/ICU via its RUNPATH but needs the system EGL library.
# A global LD_LIBRARY_PATH would break host tools such as git (pcre2 symbol clash).
sudo apt-get update -qq && sudo apt-get install -y -qq libegl1
