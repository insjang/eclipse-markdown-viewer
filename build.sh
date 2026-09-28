#!/usr/bin/env bash
# Builds local.mdpreview_<ver>.jar against eclipse.2609. `install` copies it into dropins (Eclipse 2609 must be closed).
set -euo pipefail
ECLIPSE=/mnt/c/appdev/apps/eclipse.2609
HERE=$(cd "$(dirname "$0")" && pwd)
OUT=$HERE/build
# new qualifier every build so a plain (non -clean) restart picks up the replaced JAR
VER=$(sed -n 's/^Bundle-Version: //p' "$HERE/META-INF/MANIFEST.MF" | tr -d '\r' | sed -E "s/\.v[0-9-]+$/.v$(date +%Y%m%d-%H%M)/")
JAR=local.mdpreview_$VER.jar
CP="$(find $ECLIPSE/plugins -maxdepth 1 -name '*.jar' | tr '\n' ':')"
rm -rf "$OUT/classes" && mkdir -p "$OUT/classes"
javac -encoding UTF-8 --release 21 -nowarn -cp "$CP" -d "$OUT/classes" $(find "$HERE/src" -name '*.java')
cp -r "$HERE/web" "$HERE/plugin.xml" "$OUT/classes/"
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
