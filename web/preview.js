// Markdown -> HTML with source line anchors (data-line), mermaid blocks, heading ids, doc-relative links.
function createMd(markdownit, taskLists, hljs, getBase) {
	const md = markdownit({ html: true, linkify: true, highlight: (s, lang) => {
		if (hljs && lang && hljs.getLanguage(lang)) try { return hljs.highlight(s, { language: lang, ignoreIllegals: true }).value; } catch (e) {}
		return '';
	} }).use(taskLists);
	md.linkify.set({ fuzzyLink: false }); // "other.md" is not a Moldovan domain
	const valid = md.validateLink.bind(md);
	md.validateLink = u => /^file:/i.test(u) || valid(u); // doc-relative links become file:// URLs
	md.core.ruler.push('src_lines', state => {
		const seen = {};
		state.tokens.forEach((t, i) => {
			if (t.map && t.block && t.nesting >= 0) t.attrSet('data-line', String(t.map[0]));
			if (t.type === 'heading_open') {
				let id = state.tokens[i + 1].content.trim().toLowerCase().replace(/[^\p{L}\p{N}\s_-]/gu, '').replace(/\s/g, '-');
				seen[id] = (seen[id] || 0) + 1;
				t.attrSet('id', seen[id] > 1 ? id + '-' + (seen[id] - 1) : id);
			}
		});
	});
	const fence = md.renderer.rules.fence;
	md.renderer.rules.fence = (tokens, i, opt, env, self) => {
		const t = tokens[i];
		const lang = t.info.trim().split(/\s+/)[0];
		if (lang === 'mermaid') return `<div class="mermaid" data-line="${t.map[0]}">${md.utils.escapeHtml(t.content)}</div>`;
		if (lang === 'plantuml' || lang === 'puml') return `<div class="plantuml" data-line="${t.map[0]}">${md.utils.escapeHtml(t.content)}</div>`;
		return fence(tokens, i, opt, env, self);
	};
	const norm = md.normalizeLink.bind(md);
	md.normalizeLink = u => norm(/^([a-z][a-z0-9+.-]*:|#|\/)/i.test(u) ? u : getBase() + u);
	return md;
}
if (typeof module !== 'undefined') module.exports = { createMd };

let BASE = '', SRC = '', MERMAID = {}, FONTVARS = '', COLORS = { dark: {}, light: {} }, FONT = { name: 'Malgun Gothic', size: 11 }, THEME = 'dark', SPACING = { node: 25, rank: 30, pad: 6 };
const md = typeof window !== 'undefined' ? createMd(window.markdownit, window.markdownitTaskLists, window.hljs, () => BASE) : null;

function applyStyle(font, size, code, csize, lh, theme, breaks, colors, spacing) {
	md.set({ breaks: !!breaks }); // keep single newlines as <br>
	const r = document.documentElement, s = r.style;
	r.dataset.theme = theme;
	s.setProperty('--font', `'${font}', 'Malgun Gothic', sans-serif`);
	s.setProperty('--size', size + 'pt');
	s.setProperty('--code-font', `'${code}', D2Coding, Consolas, monospace`);
	s.setProperty('--code-size', csize + 'pt');
	s.setProperty('--lh', lh);
	// indent = width of 4 monospace Latin chars at body size
	const m = document.createElement('span');
	m.style.cssText = 'position:absolute;visibility:hidden;white-space:pre;font-size:var(--size);font-family:var(--code-font)';
	m.textContent = '0000';
	document.body.appendChild(m);
	s.setProperty('--indent', m.getBoundingClientRect().width + 'px');
	m.remove();
	FONT = { name: font, size: size };
	SPACING = spacing || SPACING;
	THEME = theme;
	FONTVARS = s.cssText;
	COLORS = colors || COLORS;
	for (const [k, v] of Object.entries(COLORS[theme] || {})) s.setProperty(k, v);
	document.getElementById('hl').href = theme === 'light' ? 'hl-light.css' : 'hl-dark.css';
	// diagrams use the body font and size too
	const ff = `'${font}', 'Malgun Gothic', sans-serif`;
	MERMAID = { startOnLoad: false, securityLevel: 'strict', theme: theme === 'light' ? 'default' : 'dark',
		fontFamily: ff, themeVariables: { fontFamily: ff, fontSize: size + 'pt' }, ...mermaidSpacing(SPACING) };
	mermaid.initialize(MERMAID);
}

function render(src, base) {
	SRC = src; BASE = base;
	const y = window.scrollY;
	document.getElementById('c').replaceChildren(sanitize(md.render(src)));
	drawPlantUml(document.getElementById('c'), THEME);
	window.scrollTo(0, y);
	// diagrams grow once drawn, which moves everything below them: aim again then
	mermaid.run({ querySelector: '#c .mermaid', suppressErrors: true }).then(() => doScroll(), () => doScroll());
}

// Raw HTML in markdown is allowed (<br>, <details>...) but sanitized by DOMPurify before insertion.
// Only http(s)/mailto/file (doc-relative links) and #anchors survive as URLs.
const PURIFY = { RETURN_DOM_FRAGMENT: true, FORBID_TAGS: ['style', 'form'],
	ALLOWED_URI_REGEXP: /^(?:(?:https?|mailto|file):|#|[^a-z]|[a-z+.\-]+(?:[^a-z+.\-:]|$))/i };
function sanitize(html) {
	const f = DOMPurify.sanitize(html, PURIFY);
	for (const e of f.querySelectorAll('[src],[href]')) // raw <img src="x.svg"> -> relative to the document too
		for (const a of ['src', 'href']) {
			const v = e.getAttribute(a);
			if (v && !/^([a-z][a-z0-9+.-]*:|#|\/)/i.test(v)) e.setAttribute(a, BASE + v);
		}
	return f;
}

// Spacing from the preferences; Mermaid's defaults are node 50, rank 50, padding 15.
// Sequence and ER diagrams have their own settings, scaled from their defaults.
function mermaidSpacing({ node, rank, pad }) {
	const scale = (value, of, by) => Math.max(2, Math.round(value * by / of));
	return {
		flowchart: { nodeSpacing: node, rankSpacing: rank, padding: pad, diagramPadding: 4 },
		sequence: { actorMargin: scale(50, 50, node), messageMargin: scale(35, 50, rank), boxMargin: scale(10, 15, pad), noteMargin: scale(10, 15, pad) },
		er: { nodeSpacing: scale(140, 50, node), rankSpacing: scale(80, 50, rank), entityPadding: pad, diagramPadding: 4 }
	};
}

// PlantUML blocks are drawn by the plugin (Java) through the mdPlantUml browser function,
// with the preview's body font and the colors of the given theme.
function plantUmlStyle(theme) {
	const c = COLORS[theme] || {}, px = Math.round(FONT.size * 4 / 3); // pt -> px, as the page uses pt
	const f = FONT.name.replace(/"/g, '');
	return `skinparam defaultFontName "${f}"\nskinparam defaultFontSize ${px}\nskinparam backgroundColor transparent\n`
		+ `<style>\nroot { FontName "${f}"; FontSize ${px}; FontColor ${c['--fg'] || '#1f2328'}; LineColor ${c['--quote'] || '#59636e'}; BackGroundColor ${c['--pre-bg'] || '#f6f8fa'} }\n`
		+ `document { BackGroundColor transparent }\n</style>`;
}

function drawPlantUml(root, theme) {
	if (typeof window.mdPlantUml !== 'function') return;
	for (const e of root.querySelectorAll('.plantuml')) {
		const svg = window.mdPlantUml(e.textContent, plantUmlStyle(theme));
		// the SVG carries the diagram's own text and links: sanitized like any other HTML
		e.replaceChildren(DOMPurify.sanitize(svg, { RETURN_DOM_FRAGMENT: true, USE_PROFILES: { svg: true, svgFilters: true, html: true } }));
	}
}

// first source line of the block at the top of the viewport
function topLine() {
	let line = 0;
	for (const e of document.querySelectorAll('#c [data-line]')) {
		if (e.getBoundingClientRect().top > 8) break;
		line = +e.dataset.line;
	}
	return line;
}

// Source -> preview goes by the scrollbar: the editor's scroll position as a fraction
// (0 top, 1 bottom) is applied to the preview. Kept until the page has settled: the
// preview was hidden a moment ago and has no layout yet, and Mermaid draws later.
let PENDING = -1, PENDING_UNTIL = 0;
function scrollToFraction(f) {
	PENDING = Math.min(1, Math.max(0, f));
	PENDING_UNTIL = Date.now() + 3000;
	requestAnimationFrame(() => requestAnimationFrame(doScroll));
}

function doScroll() {
	if (PENDING < 0 || Date.now() > PENDING_UNTIL) return;
	window.scrollTo(0, PENDING * (document.documentElement.scrollHeight - window.innerHeight));
}

// the user scrolling takes over from a pending jump
if (typeof window !== 'undefined') {
	window.addEventListener('wheel', () => { PENDING = -1; }, { passive: true });
	window.addEventListener('keydown', () => { PENDING = -1; });
}

// standalone light-theme page for headless Edge --print-to-pdf (mermaid renders there on load)
function exportDoc() {
	const vars = (FONTVARS + Object.entries(COLORS.light).map(([k, v]) => `${k}:${v};`).join('')).replace(/"/g, '&quot;');
	return `<!doctype html><html data-theme="light" style="${vars}"><head><meta charset="utf-8">`
		+ '<link rel="stylesheet" href="preview.css"><link rel="stylesheet" href="hl-light.css">'
		+ '<script src="mermaid.min.js"></script><script>mermaid.initialize(' + JSON.stringify({ ...MERMAID, startOnLoad: true, theme: 'default' }).replace(/</g, '\\u003c') + ')</script>'
		+ `</head><body class="export"><div id="c">${(() => { const d = document.createElement('div'); d.append(sanitize(md.render(SRC))); drawPlantUml(d, 'light'); return d.innerHTML; })()}</div></body></html>`;
}
