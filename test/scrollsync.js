// NODE_PATH=<jsdom>/node_modules node test/scrollsync.js : the line map of the Source <-> Preview sync is not built again on a layout
// change while no Source is followed, and is right when the sync starts or a position is asked for
const assert = require('assert'), fs = require('fs'), path = require('path');
const { JSDOM } = require('jsdom');
const dom = new JSDOM('<!doctype html><div id="c"></div>');
global.window = dom.window; global.document = dom.window.document;
window.scrollTo = (x, y) => { window.__y = y; }; // jsdom has no layout and no scrolling
let measured = 0;
// a block's y is ten times its first source line, so the expected map is easy to read
dom.window.Element.prototype.getBoundingClientRect = function () { measured++; return { top: this.dataset && this.dataset.line ? +this.dataset.line * 10 : 0 }; };
const src = fs.readFileSync(path.join(__dirname, '../web/scrollsync.js'), 'utf8');
const sync = new Function(src + '; return ScrollSync;')();
const c = document.getElementById('c');
const blocks = lines => { c.innerHTML = lines.map(l => '<p data-line="' + l + '">x</p>').join(''); };

blocks([0, 5, 9]);
sync.setEnd(0, 0);
measured = 0;
sync.relayout(); // not following a Source
sync.relayout();
assert.strictEqual(measured, 0, 'no block is measured while the sync is off: ' + measured);

blocks([0, 5, 7, 9]); // the text changed meanwhile
sync.setActive(true);
assert(measured > 0, 'the map is built when the sync starts');
assert.strictEqual(sync.toLine(70), 7, 'and it has the block that appeared while the sync was off: ' + sync.toLine(70));

measured = 0;
sync.relayout(); // following: layout changes are followed
assert(measured > 0, 'the map is built again on a layout change while following');

sync.setActive(false);
blocks([0, 3, 9]);
measured = 0;
sync.relayout();
assert.strictEqual(measured, 0, 'off again: nothing measured');
sync.syncTo(3, 0, 0, 20); // a position asked for without the sync: the map is built when needed
assert(measured > 0, 'a map dropped by relayout is built again by syncTo');
assert.strictEqual(window.__y, 0, 'syncTo moved the page (clamped by the stub page height)');
assert.strictEqual(sync.toLine(30), 3, 'with the current blocks: ' + sync.toLine(30));
console.log('scrollsync ok');
