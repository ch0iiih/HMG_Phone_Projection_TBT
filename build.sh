#!/bin/sh
set -eu
: "${HMG_API:?Set HMG_API to the HMG-API source directory}"
: "${ANDROID_JAR:?Set ANDROID_JAR to Android API 19 android.jar}"
: "${D8:?Set D8 to the Android SDK d8 executable}"
: "${SIGNING_KEY:?Set SIGNING_KEY to the author private key path}"
cd "$(dirname "$0")"
root=$PWD
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/runtime-stubs" "$tmp/runtime" "$tmp/stubs" "$tmp/classes" "$tmp/dex" "$tmp/mod" dist

find "$HMG_API/core/runtime/stubs" -name '*.java' > "$tmp/runtime-stubs.args"
find "$HMG_API/core/runtime/java" -name '*.java' > "$tmp/runtime.args"
find stubs -name '*.java' > "$tmp/stubs.args"
find src -name '*.java' > "$tmp/sources.args"
javac --release 8 -Xlint:-options -cp "$ANDROID_JAR" -d "$tmp/runtime-stubs" @"$tmp/runtime-stubs.args"
javac --release 8 -Xlint:-options -cp "$ANDROID_JAR:$tmp/runtime-stubs" -d "$tmp/runtime" @"$tmp/runtime.args"
javac --release 8 -Xlint:-options -cp "$ANDROID_JAR" -d "$tmp/stubs" @"$tmp/stubs.args"
javac --release 8 -Xlint:-options -cp "$ANDROID_JAR:$tmp/runtime:$tmp/stubs" -d "$tmp/classes" @"$tmp/sources.args"

jar cf "$tmp/classes.jar" -C "$tmp/classes" hmgapi
"$D8" --min-api 19 --lib "$ANDROID_JAR" --classpath "$tmp/runtime" --classpath "$tmp/stubs" --output "$tmp/dex" "$tmp/classes.jar"
jar cMf "$tmp/mod/hooks.jar" -C "$tmp/dex" classes.dex
cp mod.json settings.json export-logs.sh "$tmp/mod/"
cp -R recipes "$tmp/mod/"
java -cp "$tmp/runtime" hmgapi.engine.Signing sign "$tmp/mod" "$SIGNING_KEY"
java -cp "$tmp/runtime" hmgapi.engine.Signing verify "$tmp/mod"
version=$(sed -n 's/.*"version": "\([^"]*\)".*/\1/p' mod.json)
archive="$root/dist/phone-projection-tbt-$version.zip"
(cd "$tmp/mod" && find . -type f | LC_ALL=C sort | zip -q -X -D "$tmp/package.zip" -@)
mv "$tmp/package.zip" "$archive"
printf '%s\n' "$archive"
