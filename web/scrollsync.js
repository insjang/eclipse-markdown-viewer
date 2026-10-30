// Source <-> preview scroll sync by source line. Blocks carry data-line (their first source line); in between,
// positions are interpolated. The editor tells its top line (fractional) and the top line it has when scrolled
// to its very end; towards that end the mapping is stretched so that both sides reach their ends together.
const ScrollSync = (() => {
	let anchors = [], total = 0, lend = 0, vlines = 0, active = false, lastY = -1000, lastLine = null, framePending = false, observed = false;
	const maxY = () => Math.max(0, document.documentElement.scrollHeight - window.innerHeight);

	// one anchor per source line (the outermost element wins), y growing with the line
	function rebuild() {
		const c = document.getElementById('c');
		if (!c) return;
		if (!observed && window.ResizeObserver) { // the page body does not exist yet when this script loads
			observed = true;
			new ResizeObserver(() => relayout()).observe(c);
		}
		const raw = [...c.querySelectorAll('[data-line]')].map(e => ({ line: +e.dataset.line, y: e.getBoundingClientRect().top + window.scrollY }));
		raw.sort((a, b) => a.line - b.line || a.y - b.y);
		anchors = [{ line: 0, y: 0 }];
		for (const a of raw) {
			const last = anchors[anchors.length - 1];
			if (a.line === last.line) continue;
			anchors.push({ line: a.line, y: Math.max(a.y, last.y) });
		}
		const back = document.getElementById('backlinks'); // below the document, not part of it
		anchors.push({ line: Math.max(total, anchors[anchors.length - 1].line + 1), y: back ? back.getBoundingClientRect().top + window.scrollY : document.documentElement.scrollHeight });
	}

	function interp(from, to, v) {
		let lo = 0, hi = anchors.length - 2;
		if (hi < 0) return 0;
		while (lo < hi) {
			const mid = (lo + hi + 1) >> 1;
			if (anchors[mid][from] <= v) lo = mid; else hi = mid - 1;
		}
		const a = anchors[lo], b = anchors[lo + 1], span = b[from] - a[from];
		return span <= 0 ? a[to] : a[to] + (v - a[from]) / span * (b[to] - a[to]);
	}

	// line -> y, stretched over the last screenful of the editor so that its end meets the preview's end
	function toY(line) {
		let y = interp('line', 'y', line);
		if (lend > 0) {
			const start = Math.max(0, lend - vlines);
			const w = lend > start ? Math.min(1, Math.max(0, (line - start) / (lend - start))) : (line >= lend ? 1 : 0);
			y += w * (maxY() - interp('line', 'y', lend));
		}
		return Math.min(maxY(), Math.max(0, y));
	}

	// y -> line, the inverse of toY by bisection
	function toLine(y) {
		if (lend <= 0) return interp('y', 'line', y);
		if (y <= 0) return 0;
		if (y >= maxY() - 1) return lend;
		let lo = 0, hi = lend;
		for (let k = 0; k < 40; k++) {
			const mid = (lo + hi) / 2;
			if (toY(mid) < y) lo = mid; else hi = mid;
		}
		return (lo + hi) / 2;
	}

	function setY(y) {
		window.scrollTo(0, y);
		lastY = window.scrollY; // what the browser really did (clamped, rounded): its own scroll event is ignored below
	}

	function syncTo(line, endLine, visibleLines, totalLines) {
		lend = endLine; vlines = visibleLines; total = totalLines;
		if (!anchors.length) rebuild();
		lastLine = line;
		setY(toY(line));
	}

	function currentLine() {
		rebuild();
		return [toLine(window.scrollY), maxY() > 0 && window.scrollY >= maxY() - 1];
	}

	function onScroll() {
		if (!active || Math.abs(window.scrollY - lastY) < 2 || framePending) return;
		framePending = true;
		requestAnimationFrame(() => {
			framePending = false;
			if (typeof window.mdScroll !== 'function') return;
			lastLine = toLine(window.scrollY);
			lastY = window.scrollY;
			window.mdScroll(lastLine, maxY() > 0 && window.scrollY >= maxY() - 1);
		});
	}

	// the layout changed (diagram drawn, image loaded, window or sash resized, text re-rendered):
	// find the blocks again and keep the editor's top line where it was. While no Source is followed nothing reads the map: it is
	// dropped here and built again when the sync starts (setActive) or a position is asked for (syncTo, currentLine)
	function relayout() {
		if (!active) {
			anchors = [];
			return;
		}
		rebuild();
		if (lastLine !== null) setY(toY(lastLine));
	}

	function setActive(on) {
		active = on;
		if (on) rebuild();
	}

	if (typeof window !== 'undefined') {
		window.addEventListener('scroll', onScroll, { passive: true });
		window.addEventListener('resize', () => relayout());
	}

	return { rebuild, relayout, syncTo, currentLine, setActive, isActive: () => active, toY, toLine, setAnchors: a => { anchors = a; }, setEnd: (e, v) => { lend = e; vlines = v; } };
})();

function syncTo(line, endLine, visibleLines, totalLines) { ScrollSync.syncTo(line, endLine, visibleLines, totalLines); }
function currentLine() { return ScrollSync.currentLine(); }
function setSync(on) { ScrollSync.setActive(on); }
function rebuildMap() { ScrollSync.relayout(); }
