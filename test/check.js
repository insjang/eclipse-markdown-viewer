// node test/check.js <file.md> : table/mermaid/data-line/heading id/relative link checks
const assert = require('assert'), fs = require('fs'), path = require('path');

const markdownit = require('../web/markdown-it.min.js');
const tasks = require('../web/markdown-it-task-lists.min.js');
const { createMd } = require('../web/preview.js');
const md = createMd(markdownit, tasks, null, () => 'file:///D:/docs/');
const h = md.render('# 정책 결정 (안건)\n\n| a | b |\n|---|---|\n| 1 | 2 |\n\n```mermaid\ngraph TD; A-->B\n```\n\n- [x] done\n\n![i](img/a.png) [x](other.md#s) [w](https://e.com)\n');
assert(h.includes('<table data-line="2">'), 'table + data-line');
assert(/<tr data-line="4">/.test(h), 'row line');
assert(h.includes('<div class="mermaid" data-line="6">graph TD; A--&gt;B'), 'mermaid');
assert(h.includes('id="정책-결정-안건"'), 'heading id');
assert(h.includes('src="file:///D:/docs/img/a.png"') && h.includes('href="file:///D:/docs/other.md#s"') && h.includes('href="https://e.com"'), 'links');
assert(h.includes('type="checkbox"'), 'task list');
// front matter: a collapsed key/value table, the lines below keep their data-line
const fm = md.render('---\ntitle: Notes\ntags:\n  - a\n  - b\nurl: https://e.com/x\n---\n# Head\n\n<b>x</b>\n');
assert(fm.startsWith('<details class="front-matter" data-line="0"><summary>Front matter</summary><table>'), 'front matter: ' + fm);
assert(fm.includes('<th>title</th><td>Notes</td>') && fm.includes('<th>tags</th><td>- a\n- b</td>') && fm.includes('<th>url</th><td>https://e.com/x</td>'), 'rows: ' + fm);
assert(fm.includes('<h1 data-line="7" id="head">'), 'line after front matter: ' + fm);
assert(!md.render('text\n\n---\na: b\n---\n').includes('front-matter'), 'only at line 1');
assert(!md.render('---\nnot closed\n').includes('front-matter'), 'unclosed is a rule');
assert(md.render('---\n<script>x</script>: 1\n...\n').includes('<th>&lt;script&gt;x&lt;/script&gt;</th>'), 'escaped, ... closes');
// GitHub alerts, footnotes, emoji
const gm = createMd(markdownit, tasks, null, () => '', null, null,
	[[require('../web/markdown-it-footnote.min.js')], [require('../web/markdown-it-emoji.min.js'), { shortcuts: {} }]]);
const al = gm.render('> [!NOTE]\n> Read **this**.\n\n> [!warning]\n\n> [!TIP] not alone\n\n> plain\n> > [!NOTE]\n> > nested\n');
assert(al.includes('<div class="markdown-alert markdown-alert-note" data-line="0">\n<p class="markdown-alert-title"><svg') && al.includes('Note</p>\n<p data-line="0">Read <strong>this</strong>.</p>\n</div>'), 'note: ' + al);
assert(al.includes('markdown-alert-warning') && al.includes('Warning</p>\n</div>'), 'marker only: ' + al);
assert(!al.includes('markdown-alert-tip') && (al.match(/markdown-alert-note/g) || []).length === 1 && al.includes('<blockquote data-line="8">'), 'text after marker, nested: not alerts ' + al);
const fn = gm.render('Text[^1] :smile: :) :nope:\n\n[^1]: The note.\n');
assert(fn.includes('<sup class="footnote-ref"><a href="#fn1" id="fnref1">[1]</a></sup>') && fn.includes('<li id="fn1" class="footnote-item">'), 'footnote: ' + fn);
assert(fn.includes('\u{1F604} :) :nope:'), 'emoji, no smiley shortcuts: ' + fn);
// crafted input must not backtrack in our rules (front matter rows, alerts)
for (const bad of ['---\na' + ' '.repeat(100000) + '\n---\n', '---\n' + 'a:'.repeat(50000) + ' x\n---\n', '> [!NOTE]' + ' '.repeat(100000) + 'x\n']) {
	const t = Date.now();
	gm.render(bad);
	assert(Date.now() - t < 500, 'slow: ' + (Date.now() - t) + ' ms');
}
if (process.argv[2]) {
	const out = md.render(fs.readFileSync(process.argv[2], 'utf8'));
	console.log('real doc:', (out.match(/<table/g) || []).length, 'tables,', (out.match(/<tr/g) || []).length, 'rows,', (out.match(/data-line/g) || []).length, 'line anchors, leftover "| --- |":', /\|\s*-{3,}\s*\|/.test(out));
}
console.log('ok');
