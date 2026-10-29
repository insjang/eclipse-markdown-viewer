// node test/sanitize.js (needs jsdom on NODE_PATH): raw HTML stays, active content is stripped
const assert = require('assert'), fs = require('fs'), path = require('path');
const { JSDOM } = require('jsdom');
const dom = new JSDOM('<div id="c"></div>');
global.document = dom.window.document;
global.DOMPurify = require('../web/purify.min.js')(dom.window);
const src = fs.readFileSync(path.join(__dirname, '../web/preview.js'), 'utf8');
const [sanitize, MERMAID_PURIFY] = new Function(src.replace(/^const md = .*$/m, 'const md = null;') + '; BASE = "file:///D:/docs/"; return [sanitize, MERMAID_PURIFY];')();
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
// a network path as plain label text loads nothing: drawn; in the places that load, also when encoded: refused
const labels = document.createElement('div');
labels.append(sanitize('<div class="mermaid">graph TD; D[file:// 제거] --> E[\\\\wsl.localhost\\Ubuntu\\x and //host/y]</div>'
	+ '<div class="mermaid">graph TD; A["&lt;img src=&amp;#92;&amp;#92;h\\x.png&gt;"]</div><div class="mermaid">graph TD; A["&lt;img src=#92;#92;h/x&gt;"]</div>'
	+ '<div class="mermaid">graph TD; A; style A fill:url(\\5c\\5ch/x)</div><div class="mermaid">%%{init: {"themeCSS": "@import \'//h/x.css\'"}}%%\ngraph TD; A</div>'
	+ '<div class="mermaid">graph TD; A["&lt;img src=img/local.png&gt;"]</div>'
	+ '<div class="mermaid">graph TD; A["&lt;img src=&quot; //h/x.png&quot;&gt;"]</div><div class="mermaid">graph TD; A["&lt;img srcset=&quot;ok.png 1x, //h/y.png 2x&quot;&gt;"]</div>'
	+ '<div class="mermaid">graph TD; A["&lt;video poster=\\\\h\\p.png&gt;"]</div>'
	+ '<div class="mermaid">graph TD; A["&lt;img src=\n //h/n.png&gt;"]</div><div class="mermaid">graph TD; A["&lt;img src=&amp;bsol;&amp;bsol;h&amp;bsol;e.png&gt;"]</div>'
	+ '<div class="mermaid">graph TD; A["&lt;img src=&amp;amp;#x5c;&amp;amp;#x5c;h/d.png&gt;"]</div><div class="mermaid">graph TD; A["&lt;img src=#bsol;#bsol;h#bsol;m.png&gt;"]</div>'
	+ '<div class="mermaid">graph TD; A["&lt;img srcset=\'x&gt;y 1x, //h/z.png 2x\'&gt;"]</div><div class="mermaid">graph TD; A["&lt;img srcset=\'a}b 1x, //h/w.png 2x\'&gt;"]</div>'
	+ '<div class="mermaid">graph TD; A["&lt;img src=\'/\t/h/t.png\'&gt;"]</div><div class="mermaid">graph TD; A["&lt;img src=\'\\\\\n\\\\h\\l.png\'&gt;"]</div>'
	+ '<div class="mermaid">graph TD; A["&lt;img src=\'fi\r\nle://h/f.png\'&gt;"]</div><div class="mermaid">graph TD; A["&lt;img src=\'/&amp;#9;/h/e.png\'&gt;"]</div>'));
assert.equal(labels.querySelectorAll('.mermaid').length, 2, 'the label text and the local image are drawn, the seventeen loads refused: ' + labels.innerHTML);
// a real diagram that names file://, \\wsl.localhost and a UNC path in its labels (mermaid-paths.mmd)
const r = document.createElement('div');
const mmd = fs.readFileSync(path.join(__dirname, 'mermaid-paths.mmd'), 'utf8');
r.append(sanitize('<div class="mermaid">' + mmd.replace(/&/g, '&amp;').replace(/</g, '&lt;') + '</div>'));
assert.equal(r.querySelectorAll('.mermaid').length, 1, 'mermaid-paths.mmd is drawn: ' + r.innerHTML.slice(0, 200));
// heading ids that DOMPurify would drop as DOM clobbering (title, links, body) get the GitHub user-content- prefix, #links follow
const markdownit = require('../web/markdown-it.min.js');
const md = require('../web/preview.js').createMd(markdownit, require('../web/markdown-it-task-lists.min.js'), null, () => 'file:///D:/docs/');
const t = document.createElement('div');
t.append(sanitize(md.render('[a](#title) [b](#links) [c](#body) [d](#정책-결정) [e](#top) [f](#raw)\n\n# Title\n## Links\n## Body\n## 정책 결정\n\n<a name="raw"></a>')));
for (const id of ['title', 'links', 'body', '정책-결정']) assert(t.querySelector('[id="user-content-' + id + '"]'), 'id kept: ' + id + ' ' + t.innerHTML);
assert(t.innerHTML.includes('href="#user-content-title"') && t.innerHTML.includes('href="#user-content-links"') && t.innerHTML.includes('href="#user-content-body"')
	&& t.innerHTML.includes('href="#user-content-%EC%A0%95') && t.innerHTML.includes('href="#user-content-raw"') && t.innerHTML.includes('href="#top"'), 'anchors follow: ' + t.innerHTML);
// alerts keep their icon and classes, footnote links follow the prefixed ids
const g = require('../web/preview.js').createMd(markdownit, require('../web/markdown-it-task-lists.min.js'), null, () => '', null, null, [[require('../web/markdown-it-footnote.min.js')]]);
const a = document.createElement('div');
a.append(sanitize(g.render('> [!CAUTION]\n> Careful[^x].\n\n[^x]: Why.\n')));
assert(a.querySelector('div.markdown-alert.markdown-alert-caution > p.markdown-alert-title > svg > path[d]'), 'alert kept: ' + a.innerHTML);
assert(a.querySelector('a[href="#user-content-fn1"][id="user-content-fnref1"]') && a.querySelector('li[id="user-content-fn1"] a[href="#user-content-fnref1"]'), 'footnote anchors: ' + a.innerHTML);
// Mermaid's drawn SVG: the labels (HTML in foreignObject) stay, active content and network paths go
const v = document.createElement('div');
v.append(DOMPurify.sanitize('<svg xmlns="http://www.w3.org/2000/svg"><g class="node"><rect width="9" height="9"/><foreignObject width="9" height="9">'
	+ '<div xmlns="http://www.w3.org/1999/xhtml"><span class="nodeLabel"><p>Start <b>bold</b></p></span><img src="x" onerror="bad()"><img src="//h/a.png">'
	+ '<a href="javascript:1">j</a><script>bad()</script></div></foreignObject></g><image href="file://h/b.png"/><use href="//h/s.svg#a"/>'
	+ '<a href="javascript:1"><text>t</text></a><style>.a{background:url(//h/c.png)}</style></svg>', MERMAID_PURIFY));
const vh = v.innerHTML;
assert(v.querySelector('foreignObject div span.nodeLabel p b') && v.textContent.includes('Start bold'), 'mermaid label kept: ' + vh);
assert(!/onerror|javascript|<script|\/\/h\/|file:\/\/h/i.test(vh), 'mermaid active content and network paths removed: ' + vh);
console.log('sanitize ok');
