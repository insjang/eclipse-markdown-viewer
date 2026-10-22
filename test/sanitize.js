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
// network (UNC) paths: Windows would connect to the host and send the user's NTLM hash
const n = document.createElement('div');
n.append(sanitize('<img src="//h/a.png"><img src="file://h/b.png"><img src="\\\\h\\c.png"><img src="/\\h/d.png"><img src="file:////h/e.png">'
	+ '<img src="file:///%5C%5Ch/f.png"><img srcset="ok.png 1x, //h/g.png 2x"><video poster="//h/p.png"><source src="//h/v.mp4"></video>'
	+ '<a href="//h/s/x.md">l</a><table><td background="//h/t.png">t</td></table><svg><image xlink:href="//h/i.png"/><rect fill="url(//h/f.svg#a)"/></svg>'
	+ '<div style="background:url(//h/x)">s1</div><div style="background:u\\72l(&quot;\\5c\\5ch/y&quot;)">s2</div><div style="background-image:image-set(\'//h/z.png\' 1x)">s3</div>'
	+ '<img src="ok.png"><img src="https://e.com/a.png"><img src="data:image/png;base64,AAAA"><div style="color:red;background:url(img/bg.png)">fine</div>'));
const u = n.innerHTML;
assert(!/[\\/]{2}h|%5C%5Ch|\\5c|style="background/i.test(u.replace(/https:\/\/e\.com/g, '')), 'network paths removed: ' + u);
assert(u.includes('src="file:///D:/docs/ok.png"') && u.includes('src="https://e.com/a.png"') && u.includes('src="data:image/png') && u.includes('url(img/bg.png)'), 'local, web and data images kept: ' + u);
assert(!sanitize('<img src="//h/a.png">').querySelector('img').hasAttribute('src'), 'protocol-relative img');
// Mermaid draws images and styles itself: a diagram naming a network path is not handed to it
const m = document.createElement('div');
m.append(sanitize('<div class="mermaid">graph TD; A["&lt;img src=//h/x.png&gt;"]</div><div class="mermaid">graph TD; B@{ img: "file://h/y.png" }</div>'
	+ '<div class="mermaid">graph TD; A[/x/]-->B[https://e.com]; click A "https://e.com"</div>'));
assert.equal(m.querySelectorAll('.mermaid').length, 1, 'only the plain diagram is drawn: ' + m.innerHTML);
// heading ids that DOMPurify would drop as DOM clobbering (title, links, body) get the GitHub user-content- prefix, #links follow
const markdownit = require('../web/markdown-it.min.js');
const md = require('../web/preview.js').createMd(markdownit, require('../web/markdown-it-task-lists.min.js'), null, () => 'file:///D:/docs/');
const t = document.createElement('div');
t.append(sanitize(md.render('[a](#title) [b](#links) [c](#body) [d](#정책-결정) [e](#top) [f](#raw)\n\n# Title\n## Links\n## Body\n## 정책 결정\n\n<a name="raw"></a>')));
for (const id of ['title', 'links', 'body', '정책-결정']) assert(t.querySelector('[id="user-content-' + id + '"]'), 'id kept: ' + id + ' ' + t.innerHTML);
assert(t.innerHTML.includes('href="#user-content-title"') && t.innerHTML.includes('href="#user-content-links"') && t.innerHTML.includes('href="#user-content-body"')
	&& t.innerHTML.includes('href="#user-content-%EC%A0%95') && t.innerHTML.includes('href="#user-content-raw"') && t.innerHTML.includes('href="#top"'), 'anchors follow: ' + t.innerHTML);
console.log('sanitize ok');
