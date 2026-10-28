// NODE_PATH=<jsdom>/node_modules node test/patch.js : the Preview is brought in line with a new render instead of being replaced
// (blocks that did not change stay, with their drawn diagram and their code button; only data-line moves)
const assert = require('assert'), fs = require('fs'), path = require('path');
const { JSDOM } = require('jsdom');
const dom = new JSDOM('<!doctype html><div id="c"></div>');
global.window = dom.window; global.document = dom.window.document;
global.DOMPurify = require('../web/purify.min.js')(dom.window);
const src = fs.readFileSync(path.join(__dirname, '../web/preview.js'), 'utf8');
const api = new Function('markdownit', 'markdownitTaskLists', src.replace(/^const md = .*$/m, 'const md = createMd(markdownit, markdownitTaskLists, null, () => "");')
	+ '; return { md, sanitize, patch, copyButtons, full: () => { FULL = true; } };')(require('../web/markdown-it.min.js'), require('../web/markdown-it-task-lists.min.js'));
const c = document.getElementById('c');
const render = text => api.patch(c, api.sanitize(api.md.render(text)));
const blocks = () => [...c.children];
const lines = () => blocks().map(b => b.getAttribute('data-line') || (b.querySelector('[data-line]') || b).getAttribute('data-line'));

const doc = '# Title\n\nfirst paragraph\n\n```js\nlet a = 1;\n```\n\n```mermaid\ngraph TD; A-->B\n```\n\nlast paragraph\n';
assert.strictEqual(render(doc), true, 'the first render replaces everything');
const first = blocks();
assert.strictEqual(first.length, 5, 'five blocks: ' + c.innerHTML);
// what the page does after a render: a code button, a drawn diagram
api.copyButtons(c);
const mer = c.querySelector('.mermaid');
mer.setAttribute('data-processed', 'true');
mer.replaceChildren(document.createElementNS('http://www.w3.org/2000/svg', 'svg'));
assert.strictEqual(c.querySelectorAll('.copy').length, 1, 'one code button');
const before = blocks();

// nothing changed: nothing is touched
assert.strictEqual(render(doc), false, 'a patch is not a full replace');
api.copyButtons(c);
assert.deepStrictEqual(blocks(), before, 'the same nodes');
assert.strictEqual(c.querySelectorAll('.copy').length, 1, 'still one code button (not a second one)');
assert(c.querySelector('.mermaid svg') && c.querySelector('.mermaid').hasAttribute('data-processed'), 'the drawn diagram is still there');

// one paragraph edited: only that block is new, the diagram and the code block stay, the ones below get their new line
const edited = doc.replace('first paragraph', 'first paragraph, edited');
render(edited);
api.copyButtons(c);
const after = blocks();
assert.notStrictEqual(after[1], before[1], 'the edited paragraph is a new node');
assert.strictEqual(after[0], before[0], 'the heading stays');
assert.strictEqual(after[2], before[2], 'the code block (with its button) stays');
assert.strictEqual(after[3], before[3], 'the diagram stays');
assert.strictEqual(after[4], before[4], 'the last paragraph stays');
assert.strictEqual(c.querySelectorAll('.copy').length, 1, 'one code button');
assert(c.querySelector('.mermaid svg'), 'the diagram is not drawn again');

// two lines inserted above: everything below stays and gets its data-line
const moved = edited.replace('# Title\n', '# Title\n\nnew one\n\nnew two\n');
render(moved);
api.copyButtons(c);
const nodes = blocks();
assert.strictEqual(nodes.length, 7, 'two new paragraphs: ' + c.innerHTML);
assert.strictEqual(nodes[nodes.length - 2], after[3], 'the diagram stays after an insert above it');
assert.strictEqual(nodes[nodes.length - 1], after[4], 'the last paragraph stays');
assert.strictEqual(c.querySelector('.mermaid').getAttribute('data-line'), String(moved.split('\n').findIndex(l => l.startsWith('```mermaid'))), 'data-line of the diagram moved with the text');
assert.strictEqual(c.querySelectorAll('.copy').length, 1, 'one code button');

// a diagram edited: that one is a new node, not drawn yet; the rest stays
const redrawn = moved.replace('A-->B', 'A-->C');
render(redrawn);
const m2 = c.querySelector('.mermaid');
assert(!m2.hasAttribute('data-processed') && !m2.querySelector('svg'), 'the edited diagram is new and not drawn');
assert.strictEqual(blocks()[0], nodes[0], 'the heading still stays');

// the text data-line="3" in code is text, not a line mark: changing it is a change
const codeDoc = n => ['intro', '', '```html', '<p data-line="' + n + '">x</p>', '```', '', '`data-line="' + n + '"`', ''].join('\n');
api.full();
render(codeDoc(3));
render(codeDoc(7));
assert(c.textContent.includes('data-line="7"') && !c.textContent.includes('data-line="3"'), 'code text that looks like a line mark is updated: ' + c.textContent);

// after a style change everything is replaced
api.full();
assert.strictEqual(render(redrawn), true, 'full replace after a style change');
assert.strictEqual(c.querySelectorAll('.copy').length, 0, 'fresh nodes have no button yet');
console.log('patch ok');
