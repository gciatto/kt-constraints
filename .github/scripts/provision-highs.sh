#!/usr/bin/env bash
set -euo pipefail

case "${TARGET}" in
  linuxX64)
    highs=x86_64-linux-gnu-cxx11; highs_sha=16a80d17da67c885718898d07b44157877aa738d10c09a59c89228c12fe65beb
    lbt=x86_64-linux-gnu; lbt_sha=57e60364c2314ad2d3676a49f1b6d16569964048d34d7f0288a5ffd54dfce22c
    library=libhighs.so.1.16.0 ;;
  macosArm64)
    highs=aarch64-apple-darwin; highs_sha=3ade8c40b789c43bcd514c57ca2dc4fed9226483048553f239452b715064b50f
    lbt=aarch64-apple-darwin; lbt_sha=f7e337908a2f054db2e2ec0478deefd096b44c3fad1601d78821bb68783fa001
    # Unlike Linux, macOS resolves libhighs' zlib through @rpath, so it must sit beside it as well.
    zlib=aarch64-apple-darwin; zlib_sha=c0c8d05315e8948e6fba4854d5e7e96e2da21db5e0cefcf8140abe03ade8db41
    library=libhighs.dylib ;;
esac
base=https://github.com/JuliaBinaryWrappers
curl --fail --location --retry 3 -o "$RUNNER_TEMP/highs.tgz" \
  "$base/HiGHS_jll.jl/releases/download/HiGHS-v1.16.0%2B0/HiGHS.v1.16.0.$highs.tar.gz"
curl --fail --location --retry 3 -o "$RUNNER_TEMP/lbt.tgz" \
  "$base/libblastrampoline_jll.jl/releases/download/libblastrampoline-v5.16.0%2B0/libblastrampoline.v5.16.0.$lbt.tar.gz"
echo "$highs_sha  $RUNNER_TEMP/highs.tgz" | shasum -a 256 --check
echo "$lbt_sha  $RUNNER_TEMP/lbt.tgz" | shasum -a 256 --check
# libhighs finds libblastrampoline beside it through its $ORIGIN / @loader_path search path.
mkdir "$RUNNER_TEMP/highs"
tar -xzf "$RUNNER_TEMP/highs.tgz" -C "$RUNNER_TEMP/highs"
tar -xzf "$RUNNER_TEMP/lbt.tgz" -C "$RUNNER_TEMP/highs"
if [ -n "${zlib:-}" ]; then
  curl --fail --location --retry 3 -o "$RUNNER_TEMP/zlib.tgz" \
    "$base/Zlib_jll.jl/releases/download/Zlib-v1.3.1%2B2/Zlib.v1.3.1.$zlib.tar.gz"
  echo "$zlib_sha  $RUNNER_TEMP/zlib.tgz" | shasum -a 256 --check
  tar -xzf "$RUNNER_TEMP/zlib.tgz" -C "$RUNNER_TEMP/highs"
fi
echo "KT_CONSTRAINTS_HIGHS_LIBRARY=$RUNNER_TEMP/highs/lib/$library" >> "$GITHUB_ENV"
