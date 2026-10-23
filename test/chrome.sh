#!/usr/bin/env bash
# Renders a .md in the real preview page with headless Chrome/Chromium and prints the page title, which the
# given script sets as its result:  test/chrome.sh <file.md> <script.js> [dark|light]
# e.g. test/chrome.sh docs/sample.md test/find-many.js  ->  "count=1/10000+ mermaid=true"
set -euo pipefail
HERE=$(cd "$(dirname "$0")/.." && pwd)
CHROME=${CHROME:-$(command -v google-chrome || command -v chromium || command -v chromium-browser)}
W=$(mktemp -d); trap 'rm -rf "$W"' EXIT
cp -r "$HERE/web/." "$W/"
python3 - "$1" "$2" "${3:-dark}" "$W" <<'PY'
import json, sys
md, js, theme, w = sys.argv[1:]
p = w + '/preview.html'; s = open(p).read()
s = s.replace('</body>', "<script>applyStyle('DejaVu Sans',11,'DejaVu Sans Mono',10,'1.6',%s,true,{dark:{},light:{}},null);render(%s,'file://%s/');%s</script></body>"
	% (json.dumps(theme), json.dumps(open(md).read()), w, open(js).read()))
open(p, 'w').write(s)
PY
"$CHROME" --headless=new --disable-gpu --no-sandbox --user-data-dir="$W/profile" --virtual-time-budget=8000 --dump-dom "file://$W/preview.html" 2>/dev/null | grep -o '<title>[^<]*' | sed 's/<title>//'
