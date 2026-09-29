#!/usr/bin/env bash
# Builds io.github.insjang.mdviewer_<ver>.jar against an Eclipse install.
# `install` copies it into that install's dropins/ (close Eclipse first).
# `site` also builds a p2 update site in build/site (Install New Software, Marketplace).
#   ECLIPSE=/path/to/eclipse ./build.sh [install|site]
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
if [ "${1:-}" = site ]; then
	# feature.xml and category.xml carry 1.0.0.qualifier / 0.0.0: give the feature the bundle's version
	S=$OUT/site-src; rm -rf "$S" "$OUT/site" "$OUT/p2cfg" "$OUT/p2.log" && mkdir -p "$S/plugins" "$S/features/f"
	cp "$OUT/$JAR" "$S/plugins/"
	sed -E "s/1\.0\.0\.qualifier/$VER/; s/version=\"0\.0\.0\"/version=\"$VER\"/" "$HERE/feature/feature.xml" > "$S/features/f/feature.xml"
	(cd "$S/features/f" && jar cf "../io.github.insjang.mdviewer_$VER.jar" feature.xml) && rm -r "$S/features/f"
	# run the p2 publisher from $ECLIPSE with a throwaway configuration area, so the install itself is not touched
	mkdir -p "$OUT/p2cfg" && cp -r "$ECLIPSE/configuration/config.ini" "$ECLIPSE/configuration/org.eclipse.equinox.simpleconfigurator" "$OUT/p2cfg/"
	p2() { java -jar "$(ls "$ECLIPSE"/plugins/org.eclipse.equinox.launcher_*.jar | tail -1)" -configuration "$OUT/p2cfg" -nosplash -consoleLog -application "$@" >> "$OUT/p2.log" 2>&1 || { tail -30 "$OUT/p2.log" >&2; exit 1; }; }
	p2 org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher -source "$S" -metadataRepository "file:$OUT/site" -artifactRepository "file:$OUT/site" -publishArtifacts -compress -repositoryName "Markdown Viewer"
	p2 org.eclipse.equinox.p2.publisher.CategoryPublisher -metadataRepository "file:$OUT/site" -categoryDefinition "file:$HERE/feature/category.xml" -categoryQualifier mdviewer -compress
	rm -rf "$S" "$OUT/p2cfg"; echo "update site in $OUT/site"
fi
if [ "${1:-}" = install ]; then
	# from WSL, refuse while that Eclipse is running (a replaced JAR is only picked up on restart anyway)
	if command -v powershell.exe >/dev/null && powershell.exe -NoProfile -Command "(Get-Process eclipse -ErrorAction SilentlyContinue).Path" 2>/dev/null | tr -d '\r' | grep -qi "$(basename "$ECLIPSE")"; then
		echo "Eclipse in $ECLIPSE is running: close it first" >&2; exit 1
	fi
	rm -f "$ECLIPSE"/dropins/io.github.insjang.mdviewer_*.jar
	cp "$OUT/$JAR" $ECLIPSE/dropins/ && echo "installed $ECLIPSE/dropins/$JAR: restart Eclipse"
fi
