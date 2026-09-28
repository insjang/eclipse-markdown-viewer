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
if (process.argv[2]) {
	const out = md.render(fs.readFileSync(process.argv[2], 'utf8'));
	console.log('real doc:', (out.match(/<table/g) || []).length, 'tables,', (out.match(/<tr/g) || []).length, 'rows,', (out.match(/data-line/g) || []).length, 'line anchors, leftover "| --- |":', /\|\s*-{3,}\s*\|/.test(out));
}
console.log('ok');
