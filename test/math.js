// NODE_PATH=<jsdom>/node_modules node test/math.js : $..$, $$..$$, ```math, \$ and prices, errors, no \href, sanitizer keeps KaTeX
const assert = require('assert'), fs = require('fs'), path = require('path');
const { JSDOM } = require('jsdom');
global.document = new JSDOM('<!doctype html>').window.document;
global.DOMPurify = require('../web/purify.min.js')(document.defaultView);
const src = fs.readFileSync(path.join(__dirname, '../web/preview.js'), 'utf8');
const api = new Function('a', src.replace(/^const md = .*$/m, 'const md = createMd(...a);') + '; return { md, sanitize };')(
	[require('../web/markdown-it.min.js'), require('../web/markdown-it-task-lists.min.js'), null, () => 'file:///D:/docs/',
		require('../web/markdown-it-katex.js'), require('../web/katex.min.js')]);
const html = api.md.render(fs.readFileSync(path.join(__dirname, 'math.md'), 'utf8'));
const d = document.createElement('div');
d.append(api.sanitize(html));
const text = s => { const e = document.createElement('div'); e.innerHTML = s; return e.textContent; };
const ks = [...d.querySelectorAll('.katex')];
const tex = ks.map(k => k.querySelector('annotation') && k.querySelector('annotation').textContent);
assert(tex.includes('e^{i\\pi} + 1 = 0') && tex.includes('\\sqrt{x^2+y^2}'), 'inline: ' + tex);
assert(d.querySelectorAll('.katex-display').length === 2, 'two display formulas ($$ and ```math)');
assert(tex.includes('\\alpha + \\beta'), '$`..`$');
assert(/costs \$5 and \$10, or \$20 escaped/.test(d.textContent), 'prices and \\$ stay text: ' + d.textContent.match(/costs.*escaped/));
assert(d.querySelectorAll('.katex-error').length >= 1, 'bad TeX shown as error');
assert(!d.querySelector('a') && !d.querySelector('img') && !/javascript/.test(d.innerHTML.replace(/<annotation[^]*?<\/annotation>/g, '')), 'no link or image from TeX');
assert(d.querySelector('li .katex') && d.querySelector('blockquote .katex') && d.querySelector('td .katex'), 'math in list, quote, table');
assert(d.querySelector('.katex-html') && d.querySelector('math semantics annotation'), 'HTML and MathML survive DOMPurify');
assert(!/<script|onerror|onload/i.test(d.innerHTML), 'nothing active');
console.log('math ok:', ks.length, 'formulas');
