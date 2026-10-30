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
// Obsidian callouts: aliases, titles, folds, unknown types as note; GitHub's own five (marker alone) stay as above
const co = gm.render('> [!faq]- Why *this*?\n> Because.\n\n> [!Summary]\n> s\n\n> [!tip]+ <b>Mine</b>\n\n> [!whatever]\n> w\n\n> [!caution] Careful\n\n> [!constructor]\n');
assert(co.includes('<details class="markdown-alert callout callout-question" data-line="0">\n<summary class="markdown-alert-title"><svg') && co.includes('Why *this*?</summary>\n<p data-line="0">Because.</p>\n</details>'), 'folded faq: ' + co);
assert(co.includes('<div class="markdown-alert callout callout-abstract" data-line="3">\n<p class="markdown-alert-title"><svg') && co.includes('Summary</p>'), 'alias, default title as written: ' + co);
assert(co.includes('<details class="markdown-alert callout callout-tip" open="" data-line="6">') && co.includes('&lt;b&gt;Mine&lt;/b&gt;</summary>\n</details>'), 'open fold, title escaped: ' + co);
assert(co.includes('callout callout-note" data-line="8">') && co.includes('Whatever</p>'), 'unknown type: a note titled as written: ' + co);
assert(co.includes('callout callout-caution" data-line="11">') && co.includes('Careful</p>') && co.includes('callout callout-note" data-line="13">'), 'caution keeps its own type; no Object keys: ' + co);
// wikilinks: resolved by the plugin (env.wiki), #heading to its id, labels, missing ones, image embeds, not in code
const wiki = { note: 'file:///D:/v/note.md', 'a b': 'file:///D:/v/a%20b.md', 'i.png': 'file:///D:/v/img/i.png', gone: null };
const wl = md.render('[[note]] [[a b#Part Two|two]] [[#Top]] [[gone]] [[toString]] ![[i.png|300]] ![[note]] `[[note]]` [[x]y]]\n\n```\n[[note]]\n```\n', { wiki });
assert(wl.includes('<a href="file:///D:/v/note.md">note</a> <a href="file:///D:/v/a%20b.md#part-two">two</a> <a href="#top">Top</a> <a class="wikilink-missing">gone</a> <a class="wikilink-missing">toString</a>'), 'links: ' + wl);
assert(wl.includes('<img src="file:///D:/v/img/i.png" alt="" width="300"> <a href="file:///D:/v/note.md">note</a> <code>[[note]]</code> [[x]y]]'), 'embeds, code: ' + wl);
assert(wl.includes('">[[note]]\n</code>') && md.render('[[a]]').includes('wikilink-missing'), 'fence; no map: missing');
// a wikilink in a link's text is skipped, not an error that blanks the page
assert(md.render('[a [[b]] c](x.md)').includes('wikilink-missing'), 'wikilink in a link text');
// Obsidian ==highlight==: not in code, \== and a lone == stay text, markup inside
const hl = md.render('==hi== `==no==` \\==x== a == b ==x **==y==** ==a *b*== ==a`b==`c==\n');
assert(hl.includes('<mark>hi</mark> <code>==no==</code> ==x== a == b ==x <strong><mark>y</mark></strong> <mark>a <em>b</em></mark> <mark>a<code>b==</code>c</mark>'), 'highlight: ' + hl);
// %%comments%%: hidden inline, over lines, and as a block; the blocks after it keep their source line; not in code
const cm = md.render('a %%b%% c %%x\n\n%%\nhidden\n\nstill\n%%\n# After\n\npara %%two\nlines%% end\n\n`%%k%%`\n\n```\n%%\nf\n%%\n```\n\n%%\nnot closed\n');
assert(cm.startsWith('<p data-line="0">a  c %%x</p>\n<h1 data-line="7" id="after">After</h1>\n<p data-line="9">para  end</p>\n<p data-line="12"><code>%%k%%</code></p>'), 'comments: ' + cm);
assert(cm.includes('%%\nf\n%%\n</code>') && cm.includes('<p data-line="20">%%\nnot closed</p>') && !cm.includes('hidden') && !cm.includes('still'), 'fence, unclosed: ' + cm);
assert(md.render('text\n%%\nx\n%%\nmore\n') === '<p data-line="0">text</p>\n<p data-line="4">more</p>\n', 'a comment block ends a paragraph');
// #tags: Obsidian's rules, Unicode letters, not a heading, number, URL part, link text or code
const tg = md.render('#tag #123 #y1984 a#b http://x/#y [#in](u) #한글/sub-x_1 `#c` (#p)\n\n# Head #t\n');
assert(tg.includes('<span class="tag">#tag</span> #123 <span class="tag">#y1984</span> a#b <a href="http://x/#y">http://x/#y</a> <a href="file:///D:/docs/u">#in</a> <span class="tag">#한글/sub-x_1</span> <code>#c</code> (#p)'), 'tags: ' + tg);
assert(tg.includes('<h1 data-line="2" id="head-t">Head <span class="tag">#t</span></h1>'), 'tag in a heading: ' + tg);
const fn = gm.render('Text[^1] :smile: :) :nope:\n\n[^1]: The note.\n');
assert(fn.includes('<sup class="footnote-ref"><a href="#fn1" id="fnref1">[1]</a></sup>') && fn.includes('<li id="fn1" class="footnote-item">'), 'footnote: ' + fn);
assert(fn.includes('\u{1F604} :) :nope:'), 'emoji, no smiley shortcuts: ' + fn);
// crafted input must not backtrack in our rules (front matter rows, alerts, wikilinks)
for (const bad of ['---\na' + ' '.repeat(100000) + '\n---\n', '---\n' + 'a:'.repeat(50000) + ' x\n---\n', '> [!NOTE]' + ' '.repeat(100000) + 'x\n',
	'[['.repeat(50000), '![[' + 'a'.repeat(100000), '[[a|'.repeat(30000), '> [!a]' + '-'.repeat(100000) + '\n',
	'=='.repeat(100000), '==a'.repeat(50000), '%%'.repeat(100000), 'a %%' + 'b'.repeat(100000), '%%\n'.repeat(50000), '%%\n' + 'a\n'.repeat(50000),
	' #a'.repeat(50000), '#'.repeat(100000), '#' + 'a'.repeat(100000), '[' + '[%%'.repeat(30000), '[' + ' #a'.repeat(30000)]) {
	const t = Date.now();
	gm.render(bad);
	assert(Date.now() - t < 500, 'slow: ' + (Date.now() - t) + ' ms');
}
const uml = '```plantuml\nA -> B\n```\n';
assert(md.render(uml).includes('<div class="plantuml" data-line="0">'), 'plantuml block');
const off = createMd(markdownit, tasks, null, () => 'file:///D:/docs/', null, null, [], () => false).render(uml);
assert(off.includes('<pre') && !off.includes('class="plantuml"'), 'plantuml switched off: a plain code block');
if (process.argv[2]) {
	const out = md.render(fs.readFileSync(process.argv[2], 'utf8'));
	console.log('real doc:', (out.match(/<table/g) || []).length, 'tables,', (out.match(/<tr/g) || []).length, 'rows,', (out.match(/data-line/g) || []).length, 'line anchors, leftover "| --- |":', /\|\s*-{3,}\s*\|/.test(out));
}
console.log('ok');
