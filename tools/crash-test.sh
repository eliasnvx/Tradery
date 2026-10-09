#!/usr/bin/env bash
# Release checklist "kill -9": two launches of a Forge dev server with the crash test mod
# (forge/src/clientTest/.../TraderyCrashTest.java), killed with kill -9 in between.
# Run from anywhere: tools/crash-test.sh   Result: forge/build/run/crashTest/crash-result.txt
set -euo pipefail
cd "$(dirname "$0")/.."
DIR=forge/build/run/crashTest
rm -rf "$DIR/world" "$DIR"/crash-*.txt
mkdir -p "$DIR"
echo "eula=true" > "$DIR/eula.txt"
printf 'level-type=minecraft\\:flat\nonline-mode=false\n' > "$DIR/server.properties"

echo "launch 1: trades, save, more trades"
./gradlew :forge:runCrashTest --console=plain > "$DIR/launch-1.log" 2>&1 &
GRADLE=$!
for _ in $(seq 1 300); do
    [ -f "$DIR/crash-ready.txt" ] && break
    sleep 1
done
if [ ! -f "$DIR/crash-ready.txt" ]; then
    echo "launch 1 never got ready, see $DIR/launch-1.log"
    kill "$GRADLE" || true
    exit 1
fi
echo "kill -9 $(cat "$DIR/crash-ready.txt")"
kill -9 "$(cat "$DIR/crash-ready.txt")"
wait "$GRADLE" || true

echo "launch 2: check the rollback"
./gradlew :forge:runCrashTest --console=plain > "$DIR/launch-2.log" 2>&1 || true
cat "$DIR/crash-result.txt"
grep -q '^PASS' "$DIR/crash-result.txt"
