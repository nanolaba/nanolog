#!/bin/sh
# Builds a GraalVM native image of a tiny program that logs through Nanolog, with the Nanolog jar in target and only
# the metadata that the jar carries (META-INF/native-image), and runs it twice: with the default ConsoleLogger, whose
# settings come from a nanolog.properties on the classpath, and with JsonLogger chosen by -Dnanolog.logger.
# Run after `mvn package` with GraalVM's native-image on the PATH.
set -e
jar=$(ls target/nanolog-*.jar | grep -v -e sources -e javadoc | head -n 1)
out=target/native-smoke
rm -rf "$out"
mkdir -p "$out/classes"
javac -d "$out/classes" -cp "$jar" src/it/native/smoke/Smoke.java
cp src/it/native/nanolog.properties "$out/classes/"
native-image --no-fallback -cp "$jar:$out/classes" -o "$out/smoke" smoke.Smoke

"$out/smoke" > "$out/console.txt" 2>&1 || { cat "$out/console.txt"; exit 1; }
cat "$out/console.txt"
# no date: the settings were read; [Smoke]: the caller was found
grep -qx 'INFO \[Smoke\] Hello from a native image' "$out/console.txt"
grep -qx 'WARN \[Smoke\] Answer 42' "$out/console.txt"
grep -qx 'ERROR \[Smoke\] Failed java.lang.IllegalStateException: boom' "$out/console.txt"
grep -q 'at smoke.Smoke.main' "$out/console.txt"

"$out/smoke" -Dnanolog.logger=com.nanolaba.logging.JsonLogger > "$out/json.txt" 2>&1 || { cat "$out/json.txt"; exit 1; }
cat "$out/json.txt"
grep '"message":"Hello from a native image"' "$out/json.txt" | grep -q '"logger":"smoke.Smoke"'
grep -q '"class":"java.lang.IllegalStateException"' "$out/json.txt"

echo "native image OK: ConsoleLogger and JsonLogger"
