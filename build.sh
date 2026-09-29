#!/usr/bin/env bash
# Builds io.github.insjang.mdviewer_<ver>.jar against an Eclipse install.
# `install` copies it into that install's dropins/ (close Eclipse first).
#   ECLIPSE=/path/to/eclipse ./build.sh [install]
set -euo pipefail
ECLIPSE=${ECLIPSE:?set ECLIPSE to the Eclipse installation directory}
HERE=$(cd "$(dirname "$0")" && pwd)
OUT=$HERE/build
# new qualifier every build so a plain (non -clean) restart picks up the replaced JAR
VER=$(sed -n 's/^Bundle-Version: //p' "$HERE/META-INF/MANIFEST.MF" | tr -d '\r' | sed -E "s/\.(qualifier|v[0-9-]+)$/.v$(date +%Y%m%d-%H%M%S)/")
JAR=io.github.insjang.mdviewer_$VER.jar
# PlantUML (MIT build) is fetched once and checked, not kept in git: 17 MB
PUML_VER=1.2026.8; PUML_SHA1=d19535573bd16fc2150592448a91d164a2163aaa
PUML=$HERE/jars/plantuml-mit.jar
if [ ! -f "$PUML" ] || [ "$(sha1sum "$PUML" | cut -d' ' -f1)" != $PUML_SHA1 ]; then
	mkdir -p "$HERE/jars"
	curl -sfL -o "$PUML" https://repo1.maven.org/maven2/net/sourceforge/plantuml/plantuml-mit/$PUML_VER/plantuml-mit-$PUML_VER.jar
	[ "$(sha1sum "$PUML" | cut -d' ' -f1)" = $PUML_SHA1 ] || { echo "plantuml checksum mismatch" >&2; exit 1; }
fi
CP="$PUML:$(find $ECLIPSE/plugins -maxdepth 1 -name '*.jar' | tr '\n' ':')"
rm -rf "$OUT/classes" && mkdir -p "$OUT/classes"
javac -encoding UTF-8 --release 21 -nowarn -cp "$CP" -d "$OUT/classes" $(find "$HERE/src" -name '*.java')
cp -r "$HERE/web" "$HERE/plugin.xml" "$OUT/classes/"
(cd "$HERE/src" && find . -type f ! -name "*.java" -exec cp --parents {} "$OUT/classes/" \;) # messages*.properties
mkdir -p "$OUT/classes/lib" && cp "$PUML" "$OUT/classes/lib/"
sed -E "s/^Bundle-Version: .*/Bundle-Version: $VER/" "$HERE/META-INF/MANIFEST.MF" > "$OUT/MANIFEST.MF"
rm -f "$OUT"/io.github.insjang.mdviewer_*.jar
(cd "$OUT/classes" && jar cfm "../$JAR" "$OUT/MANIFEST.MF" .)
echo "built $OUT/$JAR ($(stat -c %s "$OUT/$JAR") bytes)"
if [ "${1:-}" = install ]; then
	# from WSL, refuse while that Eclipse is running (a replaced JAR is only picked up on restart anyway)
	if command -v powershell.exe >/dev/null && powershell.exe -NoProfile -Command "(Get-Process eclipse -ErrorAction SilentlyContinue).Path" 2>/dev/null | tr -d '\r' | grep -qi "$(basename "$ECLIPSE")"; then
		echo "Eclipse in $ECLIPSE is running: close it first" >&2; exit 1
	fi
	rm -f "$ECLIPSE"/dropins/io.github.insjang.mdviewer_*.jar
	cp "$OUT/$JAR" $ECLIPSE/dropins/ && echo "installed $ECLIPSE/dropins/$JAR: restart Eclipse"
fi
