#!/bin/sh
# Builds a runtime image with jlink from the Nanolog jar in target and a tiny module that requires it, and runs it.
# jlink refuses automatic modules, so this fails unless the jar carries its module descriptor
# (META-INF/versions/9/module-info.class). Run after `mvn package` on JDK 11 or newer.
set -e
jar=$(ls target/nanolog-*.jar | grep -v -e sources -e javadoc | head -n 1)
out=target/jlink-smoke
sep=':'
case "$(uname -s)" in MINGW* | MSYS* | CYGWIN*) sep=';' ;; esac
rm -rf "$out"
mkdir -p "$out/classes"
javac -d "$out/classes" --module-path "$jar" src/it/jlink/smoke/module-info.java src/it/jlink/smoke/smoke/Main.java
jlink --module-path "$jar$sep$out/classes" --add-modules smoke --output "$out/image"
"$out/image/bin/java" -Dnanolog.agent=off -m smoke/smoke.Main > "$out/output.txt" 2>&1 || { cat "$out/output.txt"; exit 1; }
cat "$out/output.txt"
grep -q "Hello from a jlink image" "$out/output.txt"
echo "jlink image OK: $("$out/image/bin/java" --list-modules | tr '\n' ' ')"
