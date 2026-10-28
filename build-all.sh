#!/usr/bin/env bash
# Builds the core (with its tests) and every loader/version jar with ONE command.
# Output: dist/lyricsworld-<loader>-<mc>-<version>.jar
#
#   ./build-all.sh                 build everything
#   ./build-all.sh 1.20.1          only targets whose name contains "1.20.1"
#   MOD_VERSION=1.0.0 ./build-all.sh
#
# Needs JDK 17 on PATH (the Minecraft toolchains run on 17; the legacy jars are still compiled for Java 8).
set -euo pipefail
cd "$(dirname "$0")"

VERSION="${MOD_VERSION:-$(grep -E '^modVersion=' gradle.properties | cut -d= -f2)}"
FILTER="${1:-}"
mkdir -p dist
rm -f dist/*.jar

echo "==> core: compile + unit tests"
./gradlew --no-daemon -PmodVersion="$VERSION" :core:test

failed=()
for dir in targets/*/; do
  name="$(basename "$dir")"
  [[ "$name" == common* ]] && continue
  [[ -n "$FILTER" && "$name" != *"$FILTER"* ]] && continue
  echo "==> $name"
  if (cd "$dir" && ./gradlew --no-daemon -PmodVersion="$VERSION" build); then
    # the release jar is the one without -dev / -sources / -all
    find "$dir/build/libs" -name '*.jar' ! -name '*-dev.jar' ! -name '*-sources.jar' ! -name '*-all.jar' ! -name '*-shadow.jar' -exec cp {} dist/ \;
  else
    failed+=("$name")
  fi
done

echo
ls -la dist/ || true
if ((${#failed[@]})); then
  echo "FAILED targets: ${failed[*]}" >&2
  exit 1
fi
