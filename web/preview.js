// UI text inside the page, replaced from messages*.properties by the plugin (setLabels)
const L = { frontMatter: 'Front matter' };
function setLabels(l) { Object.assign(L, l); }

// Markdown -> HTML with source line anchors (data-line), mermaid blocks, math, heading ids, doc-relative links.
function createMd(markdownit, taskLists, hljs, getBase, mathPlugin, katex) {
	const md = markdownit({ html: true, linkify: true, highlight: (s, lang) => {
		if (hljs && lang && hljs.getLanguage(lang)) try { return hljs.highlight(s, { language: lang, ignoreIllegals: true }).value; } catch (e) {}
		return '';
	} }).use(taskLists);
	// $inline$, $$display$$, ```math (as on GitHub); bad TeX shows inline in red (KaTeX's #cc0000 is too dark
	// on the dark theme), \href, \url, \includegraphics stay text (trust: false)
	if (mathPlugin) md.use(mathPlugin, { katex, enableFencedBlocks: true, throwOnError: false, trust: false, errorColor: '#f85149' });
	md.block.ruler.before('table', 'front_matter', frontMatter);
	md.renderer.rules.front_matter = (tokens, i, opt, env, self) => {
		const rows = yamlRows(tokens[i].content), e = md.utils.escapeHtml;
		const body = rows.length ? '<table><tbody>' + rows.map(([k, v]) => `<tr><th>${e(k)}</th><td>${e(v)}</td></tr>`).join('') + '</tbody></table>'
			: `<pre>${e(tokens[i].content)}</pre>`;
		return `<details class="front-matter"${self.renderAttrs(tokens[i])}><summary>${e(L.frontMatter)}</summary>${body}</details>\n`;
	};
	md.linkify.set({ fuzzyLink: false }); // "other.md" is not a Moldovan domain
	const valid = md.validateLink.bind(md);
	md.validateLink = u => /^file:\/\/\/(?![\\/])/i.test(u) || valid(u); // doc-relative links become file:/// URLs (no host, see remote())
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

// YAML front matter: a --- line at the very top up to the next --- or ... line. The source keeps its lines,
// so data-line of everything below stays the line in the editor.
function frontMatter(state, start, end, silent) {
	if (start !== 0 || state.bMarks[0] !== 0 || state.src.slice(0, state.eMarks[0]).trimEnd() !== '---') return false;
	let l = 1;
	while (l < end && !/^(---|\.\.\.)\s*$/.test(state.src.slice(state.bMarks[l], state.eMarks[l]))) l++;
	if (l >= end) return false; // not closed: an ordinary rule
	if (silent) return true;
	const t = state.push('front_matter', '', 0);
	t.block = true;
	t.map = [0, l + 1];
	t.content = state.getLines(1, l, 0, false);
	state.line = l + 1;
	return true;
}

// top-level "key: value" lines; indented lines and list items belong to the key above
function yamlRows(s) {
	const rows = [];
	for (const line of s.split('\n')) {
		const m = /^([^\s#-][^:]*):(?:\s+(.*))?$/.exec(line);
		if (m) rows.push([m[1], (m[2] || '').trim()]);
		else if (rows.length && line.trim() && !/^\s*#/.test(line)) {
			const r = rows[rows.length - 1];
			r[1] += (r[1] ? '\n' : '') + line.trim();
		}
	}
	return rows;
}
if (typeof module !== 'undefined') module.exports = { createMd, yamlRows };

let BASE = '', SRC = '', MERMAID = {}, FONTVARS = '', COLORS = { dark: {}, light: {} }, FONT = { name: 'system-ui', size: 11 }, THEME = 'dark', SPACING = { node: 25, rank: 30, pad: 6 };
const md = typeof window !== 'undefined' ? createMd(window.markdownit, window.markdownitTaskLists, window.hljs, () => BASE, window.markdownitKatex, window.katex) : null;

function applyStyle(font, size, code, csize, lh, theme, breaks, colors, spacing) {
	md.set({ breaks: !!breaks }); // keep single newlines as <br>
	const r = document.documentElement, s = r.style;
	r.dataset.theme = theme;
	s.setProperty('--font', `'${font}', system-ui, sans-serif`);
	s.setProperty('--size', size + 'pt');
	s.setProperty('--code-font', `'${code}', Consolas, monospace`);
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
	const ff = `'${font}', system-ui, sans-serif`;
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
	document.fonts.ready.then(() => doScroll()); // so do formulas once the KaTeX fonts are in
}

// Raw HTML in markdown is allowed (<br>, <details>...) but sanitized by DOMPurify before insertion.
// Only http(s)/mailto/file (doc-relative links) and #anchors survive as URLs.
// semantics/annotation: KaTeX's MathML wraps the TeX source in them (text only); without them it leaks into the page text.
// SANITIZE_NAMED_PROPS: ids become user-content-<id> as on GitHub, so headings such as "Title" or "Links" keep theirs
const PURIFY = { RETURN_DOM_FRAGMENT: true, FORBID_TAGS: ['style', 'form'], ADD_TAGS: ['semantics', 'annotation'], SANITIZE_NAMED_PROPS: true,
	ALLOWED_URI_REGEXP: /^(?:(?:https?|mailto|file):|#|[^a-z]|[a-z+.\-]+(?:[^a-z+.\-:]|$))/i };
function sanitize(html) {
	const f = DOMPurify.sanitize(html, PURIFY);
	for (const e of f.querySelectorAll('[src],[href]')) // raw <img src="x.svg"> -> relative to the document too
		for (const a of ['src', 'href']) {
			const v = e.getAttribute(a);
			if (v && !/^([a-z][a-z0-9+.-]*:|#|\/)/i.test(v)) e.setAttribute(a, BASE + v);
		}
	const ids = new Set([...f.querySelectorAll('[id],[name]')].flatMap(e => [e.id, e.getAttribute('name')]));
	for (const e of f.querySelectorAll('a[href^="#"]')) { // #links -> the prefixed ids
		const h = e.getAttribute('href').slice(1);
		try { if (ids.has('user-content-' + decodeURIComponent(h))) e.setAttribute('href', '#user-content-' + h); } catch (x) {}
	}
	for (const e of f.querySelectorAll('.mermaid')) // Mermaid loads images and styles itself: no network paths there either
		if (MERMAID_NET.test(e.textContent)) e.replaceWith(Object.assign(document.createElement('pre'),
			{ className: 'plantuml-error', textContent: 'Mermaid: network paths (\\\\host, //host, file:, url()) are not allowed\n' + e.textContent }));
	return f;
}

// A file: URL with a host, or one whose path starts with two slashes, is a network (UNC) path: Windows would
// connect to that host (SMB, then WebDAV) and send the user's NTLM hash just for showing an image.
// Such URLs are dropped wherever they can load something: src, href, srcset, poster, CSS url() and strings.
function remote(u) {
	try {
		const r = new URL(u.trim(), BASE || document.baseURI);
		return r.protocol === 'file:' && (r.host !== '' || /^[\\/]{2}/.test(decodeURIComponent(r.pathname)));
	} catch (e) {
		return true;
	}
}
function cssUrls(css) {
	css = css.replace(/\\([0-9a-f]{1,6})\s?|\\(.)/gis, (m, h, c) => h ? String.fromCodePoint(Math.min(parseInt(h, 16), 0xfffd)) : c);
	return [...css.matchAll(/url\(\s*(["']?)(.*?)\1\s*\)|(["'])(.*?)\3/gis)].map(m => m[2] ?? m[4]);
}
const URL_ATTR = /^(?:src|href|xlink:href|poster|background|action|formaction|data|cite)$/;
const MERMAID_NET = /file:|(?<!https?:)[\\/]{2}|[\\/](?:%5c|%2f)|%5c%5c|\\0*(?:2f|5c)|#(?:47|92|x2f|x5c);|&#(?:0*(?:47|92)|x0*(?:2f|5c))|[&#]b?sol;|url\s*\(|image-set|\bimg\s*:\s*["']?(?!https?:|data:)/i;
if (typeof DOMPurify !== 'undefined') {
	DOMPurify.addHook('uponSanitizeAttribute', (n, a) => {
		const v = a.attrValue || '', urls = cssUrls(v);
		if (URL_ATTR.test(a.attrName)) urls.push(v);
		if (a.attrName === 'srcset') urls.push(...v.split(',').map(s => s.trim().split(/\s+/)[0]));
		if (urls.some(remote)) a.keepAttr = false;
	});
	DOMPurify.addHook('uponSanitizeElement', (n, d) => { // <style> inside SVG
		if (d.tagName === 'style' && cssUrls(n.textContent).some(remote)) n.textContent = '';
	});
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

// Preview -> source goes by the scrollbar too: how far down the preview is, 0 to 1
function scrollFraction() {
	const max = document.documentElement.scrollHeight - window.innerHeight;
	return max <= 0 ? 0 : Math.min(1, window.scrollY / max);
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
		+ '<link rel="stylesheet" href="preview.css"><link rel="stylesheet" href="hl-light.css"><link rel="stylesheet" href="katex.min.css">'
		+ '<script src="mermaid.min.js"></script><script>mermaid.initialize(' + JSON.stringify({ ...MERMAID, startOnLoad: true, theme: 'default' }).replace(/</g, '\\u003c') + ')</script>'
		+ `</head><body class="export"><div id="c">${(() => { const d = document.createElement('div'); d.append(sanitize(md.render(SRC))); drawPlantUml(d, 'light'); return d.innerHTML; })()}</div></body></html>`;
}
