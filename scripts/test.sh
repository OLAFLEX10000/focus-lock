#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
test_dir=$(mktemp -d)
trap 'rm -rf "$test_dir"' EXIT HUP INT TERM
if [ -n "${JAVA_HOME:-}" ]; then
    javac_cmd="$JAVA_HOME/bin/javac"
    java_cmd="$JAVA_HOME/bin/java"
else
    javac_cmd=javac
    java_cmd=java
fi
"$javac_cmd" -d "$test_dir" \
    FocusLock/app/src/main/java/com/olaflex/focuslock/SessionClock.java \
    FocusLock/app/src/main/java/com/olaflex/focuslock/SelectionRules.java \
    FocusLock/tests/SessionClockTest.java \
    FocusLock/tests/SelectionRulesTest.java
"$java_cmd" -cp "$test_dir" SessionClockTest
"$java_cmd" -cp "$test_dir" SelectionRulesTest
