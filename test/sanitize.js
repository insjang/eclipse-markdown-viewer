// node test/sanitize.js (needs jsdom on NODE_PATH): raw HTML stays, active content is stripped
const assert = require('assert'), fs = require('fs'), path = require('path');
const { JSDOM } = require('jsdom');
const dom = new JSDOM('<div id="c"></div>');
global.document = dom.window.document;
global.DOMPurify = require('../web/purify.min.js')(dom.window);
const src = fs.readFileSync(path.join(__dirname, '../web/preview.js'), 'utf8');
const sanitize = new Function(src.replace(/^const md = .*$/m, 'const md = null;') + '; BASE = "file:///D:/docs/"; return sanitize;')();
const d = document.createElement('div');
d.append(sanitize('<p data-line="3">ok<br><details><summary>s</summary>x</details></p><img src="x" onerror="location.href=\'file:///C:/x.exe\'">'
	+ '<a href="javascript:alert(1)">j</a><a href=" JaVaScRiPt:1">k</a><a href="java&#9;script:1">tab</a><a href="&#x6A;avascript:1">ent</a>'
	+ '<script>bad()</script><iframe src="file:///C:/"></iframe><style>*{}</style>'
	+ '<svg><a xlink:href="javascript:1"><text>t</text></a><animate attributeName="href" to="javascript:1"/><set attributeName="href" to="javascript:1"/></svg>'
	+ '<div onclick="x" onmouseover="y">z</div><a href="https://e.com">w</a><a href="file:///D:/docs/a.md#s">f</a><a href="#top">t</a>'
	+ '<img src="diagrams/x.svg"><svg width="4"><rect width="4" height="2"/></svg><ul><li class="task-list-item"><input type="checkbox" checked disabled> done</li></ul><img src="file:///D:/docs/i.png">'));
const h = d.innerHTML;
assert(h.includes('<br>') && h.includes('<details>') && h.includes('data-line="3"'), 'harmless html kept');
assert(!/onerror|onclick|onmouseover|javascript|<script|<iframe|<style|<animate|<set/i.test(h), 'active content removed: ' + h);
assert(h.includes('href="https://e.com"') && h.includes('href="file:///D:/docs/a.md#s"') && h.includes('href="#top"'), 'links kept: ' + h);
assert(h.includes('type="checkbox"') && h.includes('src="file:///D:/docs/i.png"'), 'task list + local image kept');
assert(h.includes('src="file:///D:/docs/diagrams/x.svg"') && h.includes('<rect'), 'raw img made doc-relative, inline svg kept: ' + h);
console.log('sanitize ok');
