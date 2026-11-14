#!/usr/bin/env bash
# Inspired by 2p-Kt's scoped retry: do not re-upload already-successful Maven Central bundles.
set -uo pipefail
versionArg="$1"
log="$(mktemp)"
./gradlew "$versionArg" publishAllPublicationsToProjectLocalRepository \
  zipMavenCentralPortalPublication releaseMavenCentralPortalPublication --continue >"$log" 2>&1
status=$?
cat "$log"
if [ "$status" -ne 0 ]; then
    failures=$(grep -oE "Execution failed for task ':[^:]+:(zip|release)MavenCentralPortalPublication'" "$log" |
        sed -E "s/Execution failed for task ':([^:]+):.*/\1/" | sort -u) || true
    if [ -z "$failures" ]; then rm -f "$log"; exit 3; fi
    echo "Retrying failed Maven Central modules only: $failures"
    tasks=()
    for m in $failures; do
        tasks+=(":$m:zipMavenCentralPortalPublication" ":$m:releaseMavenCentralPortalPublication")
    done
    ./gradlew "$versionArg" "${tasks[@]}" --continue || { rm -f "$log"; exit 3; }
fi
rm -f "$log"
