// UI text inside the page, replaced from messages*.properties by the plugin (setLabels)
const L = { frontMatter: 'Front matter' };
function setLabels(l) { Object.assign(L, l); }

// Markdown -> HTML with source line anchors (data-line), mermaid blocks, math, heading ids, doc-relative links.
function createMd(markdownit, taskLists, hljs, getBase, mathPlugin, katex, plugins) {
	const md = markdownit({ html: true, linkify: true, highlight: (s, lang) => {
		if (hljs && lang && hljs.getLanguage(lang)) try { return hljs.highlight(s, { language: lang, ignoreIllegals: true }).value; } catch (e) {}
		return '';
	} }).use(taskLists);
	// $inline$, $$display$$, ```math (as on GitHub); bad TeX shows inline in red (KaTeX's #cc0000 is too dark
	// on the dark theme), \href, \url, \includegraphics stay text (trust: false)
	if (mathPlugin) md.use(mathPlugin, { katex, enableFencedBlocks: true, throwOnError: false, trust: false, errorColor: '#f85149' });
	for (const [p, opt] of plugins || []) md.use(p, opt); // footnotes, emoji
	md.core.ruler.after('block', 'alerts', alerts);
	md.renderer.rules.blockquote_open = (tokens, i, opt, env, self) => self.renderToken(tokens, i, opt)
		+ (tokens[i].meta && tokens[i].meta.alert ? alertTitle(tokens[i].meta.alert) : '');
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

// GitHub alerts: a top-level quote whose first line is only [!NOTE], [!TIP], [!IMPORTANT], [!WARNING] or [!CAUTION]
const ALERT = /^\[!(note|tip|important|warning|caution)\][ \t]*(?:\n|$)/i;
function alerts(state) {
	const t = state.tokens;
	for (let i = 0; i + 2 < t.length; i++) {
		if (t[i].type !== 'blockquote_open' || t[i].level !== 0 || t[i + 1].type !== 'paragraph_open') continue;
		const m = ALERT.exec(t[i + 2].content);
		if (!m) continue;
		const type = m[1].toLowerCase();
		t[i].tag = 'div';
		t[i].attrJoin('class', 'markdown-alert markdown-alert-' + type);
		t[i].meta = { alert: type };
		for (let j = i + 1; j < t.length; j++) if (t[j].type === 'blockquote_close' && t[j].level === 0) { t[j].tag = 'div'; break; }
		t[i + 2].content = t[i + 2].content.slice(m[0].length);
		if (!t[i + 2].content) t[i + 1].hidden = t[i + 3].hidden = true; // nothing else on the marker's paragraph
	}
}
const ALERT_ICONS = { // GitHub Octicons (MIT), 16x16
	note: 'M0 8a8 8 0 1 1 16 0A8 8 0 0 1 0 8Zm8-6.5a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0-13ZM6.5 7.75A.75.75 0 0 1 7.25 7h1a.75.75 0 0 1 .75.75v2.75h.25a.75.75 0 0 1 0 1.5h-2a.75.75 0 0 1 0-1.5h.25v-2h-.25a.75.75 0 0 1-.75-.75ZM8 6a1 1 0 1 1 0-2 1 1 0 0 1 0 2Z',
	tip: 'M8 1.5c-2.363 0-4 1.69-4 3.75 0 .984.424 1.625.984 2.304l.214.253c.223.264.47.556.673.848.284.411.537.896.621 1.49a.75.75 0 0 1-1.484.211c-.04-.282-.163-.547-.37-.847a8.456 8.456 0 0 0-.542-.68c-.084-.1-.173-.205-.268-.32C3.201 7.75 2.5 6.766 2.5 5.25 2.5 2.31 4.863 0 8 0s5.5 2.31 5.5 5.25c0 1.516-.701 2.5-1.328 3.259-.095.115-.184.22-.268.319-.207.245-.383.453-.541.681-.208.3-.33.565-.37.847a.751.751 0 0 1-1.485-.212c.084-.593.337-1.078.621-1.489.203-.292.45-.584.673-.848.075-.088.147-.173.213-.253.561-.679.985-1.32.985-2.304 0-2.06-1.637-3.75-4-3.75ZM5.75 12h4.5a.75.75 0 0 1 0 1.5h-4.5a.75.75 0 0 1 0-1.5ZM6 15.25a.75.75 0 0 1 .75-.75h2.5a.75.75 0 0 1 0 1.5h-2.5a.75.75 0 0 1-.75-.75Z',
	important: 'M0 1.75C0 .784.784 0 1.75 0h12.5C15.216 0 16 .784 16 1.75v9.5A1.75 1.75 0 0 1 14.25 13H8.06l-2.573 2.573A1.458 1.458 0 0 1 3 14.543V13H1.75A1.75 1.75 0 0 1 0 11.25Zm1.75-.25a.25.25 0 0 0-.25.25v9.5c0 .138.112.25.25.25h2a.75.75 0 0 1 .75.75v2.19l2.72-2.72a.749.749 0 0 1 .53-.22h6.5a.25.25 0 0 0 .25-.25v-9.5a.25.25 0 0 0-.25-.25Zm7 2.25v2.5a.75.75 0 0 1-1.5 0v-2.5a.75.75 0 0 1 1.5 0ZM9 9a1 1 0 1 1-2 0 1 1 0 0 1 2 0Z',
	warning: 'M6.457 1.047c.659-1.234 2.427-1.234 3.086 0l6.082 11.378A1.75 1.75 0 0 1 14.082 15H1.918a1.75 1.75 0 0 1-1.543-2.575Zm1.763.707a.25.25 0 0 0-.44 0L1.698 13.132a.25.25 0 0 0 .22.368h12.164a.25.25 0 0 0 .22-.368Zm.53 3.996v2.5a.75.75 0 0 1-1.5 0v-2.5a.75.75 0 0 1 1.5 0ZM9 11a1 1 0 1 1-2 0 1 1 0 0 1 2 0Z',
	caution: 'M4.47.22A.749.749 0 0 1 5 0h6c.199 0 .389.079.53.22l4.25 4.25c.141.14.22.331.22.53v6a.749.749 0 0 1-.22.53l-4.25 4.25A.749.749 0 0 1 11 16H5a.749.749 0 0 1-.53-.22L.22 11.53A.749.749 0 0 1 0 11V5c0-.199.079-.389.22-.53Zm.84 1.28L1.5 5.31v5.38l3.81 3.81h5.38l3.81-3.81V5.31L10.69 1.5ZM8 4a.75.75 0 0 1 .75.75v3.5a.75.75 0 0 1-1.5 0v-3.5A.75.75 0 0 1 8 4Zm0 8a1 1 0 1 1 0-2 1 1 0 0 1 0 2Z' };
function alertTitle(type) {
	return `<p class="markdown-alert-title"><svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true"><path d="${ALERT_ICONS[type]}"></path></svg>${type[0].toUpperCase() + type.slice(1)}</p>\n`;
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
const PLUGINS = typeof window !== 'undefined' ? [[window.markdownitFootnote], [window.markdownitEmoji, { shortcuts: {} }]] : []; // no :) -> emoji, as on GitHub
const md = typeof window !== 'undefined' ? createMd(window.markdownit, window.markdownitTaskLists, window.hljs, () => BASE, window.markdownitKatex, window.katex, PLUGINS) : null;

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

// Outline click: the block that starts at the source line (0-based), or the last one before it
function scrollToLine(n) {
	let best = null;
	for (const e of document.querySelectorAll('#c [data-line]')) if (+e.dataset.line <= n && (!best || +e.dataset.line > +best.dataset.line)) best = e;
	PENDING = -1;
	if (best) best.scrollIntoView({ block: 'start' });
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
