#!/usr/bin/env bash
# Builds local.mdpreview_<ver>.jar against eclipse.2609. `install` copies it into dropins (Eclipse 2609 must be closed).
set -euo pipefail
ECLIPSE=/mnt/c/appdev/apps/eclipse.2609
HERE=$(cd "$(dirname "$0")" && pwd)
OUT=$HERE/build
# new qualifier every build so a plain (non -clean) restart picks up the replaced JAR
VER=$(sed -n 's/^Bundle-Version: //p' "$HERE/META-INF/MANIFEST.MF" | tr -d '\r' | sed -E "s/\.v[0-9-]+$/.v$(date +%Y%m%d-%H%M%S)/")
JAR=local.mdpreview_$VER.jar
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
mkdir -p "$OUT/classes/lib" && cp "$PUML" "$OUT/classes/lib/"
sed -E "s/^Bundle-Version: .*/Bundle-Version: $VER/" "$HERE/META-INF/MANIFEST.MF" > "$OUT/MANIFEST.MF"
rm -f "$OUT"/local.mdpreview_*.jar
(cd "$OUT/classes" && jar cfm "../$JAR" "$OUT/MANIFEST.MF" .)
echo "built $OUT/$JAR ($(stat -c %s "$OUT/$JAR") bytes)"
if [ "${1:-}" = install ]; then
	if powershell.exe -NoProfile -Command "(Get-Process eclipse -ErrorAction SilentlyContinue).Path" 2>/dev/null | tr -d '\r' | grep -q "eclipse.2609"; then
		echo "eclipse.2609 is running — close it first" >&2; exit 1
	fi
	rm -f $ECLIPSE/dropins/local.mdpreview_*.jar
	cp "$OUT/$JAR" $ECLIPSE/dropins/ && echo "installed $ECLIPSE/dropins/$JAR — restart Eclipse"
fi
