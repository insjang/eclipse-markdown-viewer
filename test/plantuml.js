// NODE_PATH=<jsdom>/node_modules node test/plantuml.js <svg file>: plantuml fences become sanitized SVG with the preview's style
const assert = require('assert'), fs = require('fs'), path = require('path');
const { JSDOM } = require('jsdom');
const dom = new JSDOM('<div id="c"></div>');
global.window = dom.window; global.document = dom.window.document;
global.DOMPurify = require('../web/purify.min.js')(dom.window);
const svg = fs.readFileSync(process.argv[2], 'utf8');
const calls = [];
window.mdPlantUml = (src, style) => { calls.push({ src, style }); return svg; };
const src = fs.readFileSync(path.join(__dirname, '../web/preview.js'), 'utf8');
const api = new Function('markdownit', 'markdownitTaskLists', 'hljs', src.replace(/^const md = .*$/m, 'const md = createMd(markdownit, markdownitTaskLists, null, () => BASE);')
	+ '; return { md, drawPlantUml, set: (f, c, t) => { FONT = f; COLORS = c; THEME = t; } };')(require('../web/markdown-it.min.js'), require('../web/markdown-it-task-lists.min.js'), null);
api.set({ name: 'Malgun Gothic', size: 12 }, { dark: { '--fg': '#f0f6fc', '--quote': '#9198a1', '--pre-bg': '#151b23' }, light: {} }, 'dark');
const d = document.createElement('div');
d.innerHTML = api.md.render('# t\n\n```plantuml\nAlice -> 상담원 : 연결\n```\n\n```puml\nA -> B\n```\n');
assert.equal(d.querySelectorAll('.plantuml').length, 2, 'both fences become plantuml blocks');
api.drawPlantUml(d, 'dark');
assert.equal(calls.length, 2); const seen = calls[0];
assert(seen.src.includes('Alice -> 상담원'), 'source handed over as written');
assert(seen.style.includes('defaultFontName "Malgun Gothic"') && seen.style.includes('defaultFontSize 16') && seen.style.includes('#f0f6fc'), 'font, size in px and theme colors: ' + seen.style);
const h = d.innerHTML;
assert(h.includes('<svg') && h.includes('상담원') && h.includes('Malgun Gothic'), 'svg kept with text and font');
assert(!/<script|onload|onclick/i.test(h), 'no active content');
console.log('plantuml ok');
