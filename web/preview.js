// UI text inside the page, replaced from messages*.properties by the plugin (setLabels)
const L = { frontMatter: 'Front matter', find: 'Find', findNone: 'No results', findPrev: 'Previous (Shift+Enter)', findNext: 'Next (Enter)', findClose: 'Close (Esc)', copy: 'Copy', copied: 'Copied', zoomReset: 'Original size' };
function setLabels(l) { Object.assign(L, l); }

// Markdown -> HTML with source line anchors (data-line), mermaid blocks, math, heading ids, doc-relative links.
function createMd(markdownit, taskLists, hljs, getBase, mathPlugin, katex, plugins, plantUmlOn = () => true) {
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
	md.inline.ruler.before('link', 'wikilink', wikilink);
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
				const id = slug(state.tokens[i + 1].content);
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
		if ((lang === 'plantuml' || lang === 'puml') && plantUmlOn()) return `<div class="plantuml" data-line="${t.map[0]}">${md.utils.escapeHtml(t.content)}</div>`;
		return fence(tokens, i, opt, env, self);
	};
	const norm = md.normalizeLink.bind(md);
	md.normalizeLink = u => norm(/^([a-z][a-z0-9+.-]*:|#|\/)/i.test(u) ? u : getBase() + u);
	return md;
}

const slug = s => s.trim().toLowerCase().replace(/[^\p{L}\p{N}\s_-]/gu, '').replace(/\s/g, '-'); // heading ids, as Headings.slug

// Obsidian wikilinks: [[note]], [[note#Heading|label]], [[#Heading]]; ![[image.png|300]] shows the image, other embeds are links.
// The plugin resolves the targets in the project (env.wiki: target -> file URL, or null): a missing one is a link to nowhere.
const WIKI = /^(!?)\[\[([^[\]|#\n]*)(?:#([^[\]|\n]*))?(?:\|([^[\]\n]*))?\]\]/, WIKI_IMAGE = /\.(png|jpe?g|gif|svg|webp|bmp)$/i;
function wikilink(state, silent) {
	const c = state.src.charCodeAt(state.pos);
	if (c !== 0x5B && c !== 0x21) return false; // [ or !
	const m = WIKI.exec(state.src.slice(state.pos, state.pos + 4000)); // MarkdownLinks.MAX_LINE
	const target = m && m[2].trim(), h = m && m[3] && m[3].trim(), label = m && m[4] && m[4].trim();
	if (!m || !target && !h) return false;
	if (silent) return true;
	const wiki = state.env.wiki || {}, url = !target ? '' : Object.hasOwn(wiki, target) ? wiki[target] : null; // not toString & co.
	if (m[1] && url && WIKI_IMAGE.test(target)) {
		const t = state.push('image', 'img', 0);
		t.attrs = [['src', url], ['alt', '']];
		if (/^\d+$/.test(label || '')) t.attrs.push(['width', label]);
		t.children = [];
	} else {
		const t = state.push('link_open', 'a', 1);
		if (url == null) t.attrSet('class', 'wikilink-missing');
		else t.attrSet('href', url + (h ? '#' + slug(h) : ''));
		state.push('text', '', 0).content = label || (h ? (target ? target + ' > ' : '') + h : target);
		state.push('link_close', 'a', -1);
	}
	state.pos += m[0].length;
	return true;
}

// GitHub alerts: a top-level quote whose first line is only [!NOTE], [!TIP], [!IMPORTANT], [!WARNING] or [!CAUTION], drawn as on GitHub.
// Any other [!type] is an Obsidian callout: the types and aliases below (an unknown one is a note), a title after the marker,
// and - (folded) or + (open) right after it make it a <details>.
const CALLOUT = /^\[!([a-z][\w-]*)\]([+-]?)[ \t]*([^\n]*)(?:\n|$)/i, GITHUB = ['note', 'tip', 'important', 'warning', 'caution'];
const CALLOUTS = { summary: 'abstract', tldr: 'abstract', hint: 'tip', check: 'success', done: 'success', help: 'question', faq: 'question',
	attention: 'warning', fail: 'failure', missing: 'failure', error: 'danger', cite: 'quote' };
function alerts(state) {
	const t = state.tokens;
	for (let i = 0; i + 2 < t.length; i++) {
		if (t[i].type !== 'blockquote_open' || t[i].level !== 0 || t[i + 1].type !== 'paragraph_open') continue;
		const m = CALLOUT.exec(t[i + 2].content);
		if (!m) continue;
		const name = m[1].toLowerCase(), title = m[3].trim(), github = GITHUB.includes(name) && !m[2] && !title;
		const type = github ? name : Object.hasOwn(CALLOUTS, name) ? CALLOUTS[name] : Object.hasOwn(ALERT_ICONS, name) ? name : 'note';
		const tag = m[2] ? 'details' : 'div';
		t[i].tag = tag;
		t[i].attrJoin('class', github ? 'markdown-alert markdown-alert-' + type : 'markdown-alert callout callout-' + type);
		if (m[2] === '+') t[i].attrSet('open', '');
		t[i].meta = { alert: github ? type : { type, title: state.md.utils.escapeHtml(title || name[0].toUpperCase() + name.slice(1)), tag: m[2] ? 'summary' : 'p' } };
		for (let j = i + 1; j < t.length; j++) if (t[j].type === 'blockquote_close' && t[j].level === 0) { t[j].tag = tag; break; }
		t[i + 2].content = t[i + 2].content.slice(m[0].length);
		if (!t[i + 2].content) t[i + 1].hidden = t[i + 3].hidden = true; // nothing else on the marker's paragraph
	}
}
const ALERT_ICONS = { // GitHub Octicons (MIT), 16x16
	note: 'M0 8a8 8 0 1 1 16 0A8 8 0 0 1 0 8Zm8-6.5a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0-13ZM6.5 7.75A.75.75 0 0 1 7.25 7h1a.75.75 0 0 1 .75.75v2.75h.25a.75.75 0 0 1 0 1.5h-2a.75.75 0 0 1 0-1.5h.25v-2h-.25a.75.75 0 0 1-.75-.75ZM8 6a1 1 0 1 1 0-2 1 1 0 0 1 0 2Z',
	tip: 'M8 1.5c-2.363 0-4 1.69-4 3.75 0 .984.424 1.625.984 2.304l.214.253c.223.264.47.556.673.848.284.411.537.896.621 1.49a.75.75 0 0 1-1.484.211c-.04-.282-.163-.547-.37-.847a8.456 8.456 0 0 0-.542-.68c-.084-.1-.173-.205-.268-.32C3.201 7.75 2.5 6.766 2.5 5.25 2.5 2.31 4.863 0 8 0s5.5 2.31 5.5 5.25c0 1.516-.701 2.5-1.328 3.259-.095.115-.184.22-.268.319-.207.245-.383.453-.541.681-.208.3-.33.565-.37.847a.751.751 0 0 1-1.485-.212c.084-.593.337-1.078.621-1.489.203-.292.45-.584.673-.848.075-.088.147-.173.213-.253.561-.679.985-1.32.985-2.304 0-2.06-1.637-3.75-4-3.75ZM5.75 12h4.5a.75.75 0 0 1 0 1.5h-4.5a.75.75 0 0 1 0-1.5ZM6 15.25a.75.75 0 0 1 .75-.75h2.5a.75.75 0 0 1 0 1.5h-2.5a.75.75 0 0 1-.75-.75Z',
	important: 'M0 1.75C0 .784.784 0 1.75 0h12.5C15.216 0 16 .784 16 1.75v9.5A1.75 1.75 0 0 1 14.25 13H8.06l-2.573 2.573A1.458 1.458 0 0 1 3 14.543V13H1.75A1.75 1.75 0 0 1 0 11.25Zm1.75-.25a.25.25 0 0 0-.25.25v9.5c0 .138.112.25.25.25h2a.75.75 0 0 1 .75.75v2.19l2.72-2.72a.749.749 0 0 1 .53-.22h6.5a.25.25 0 0 0 .25-.25v-9.5a.25.25 0 0 0-.25-.25Zm7 2.25v2.5a.75.75 0 0 1-1.5 0v-2.5a.75.75 0 0 1 1.5 0ZM9 9a1 1 0 1 1-2 0 1 1 0 0 1 2 0Z',
	warning: 'M6.457 1.047c.659-1.234 2.427-1.234 3.086 0l6.082 11.378A1.75 1.75 0 0 1 14.082 15H1.918a1.75 1.75 0 0 1-1.543-2.575Zm1.763.707a.25.25 0 0 0-.44 0L1.698 13.132a.25.25 0 0 0 .22.368h12.164a.25.25 0 0 0 .22-.368Zm.53 3.996v2.5a.75.75 0 0 1-1.5 0v-2.5a.75.75 0 0 1 1.5 0ZM9 11a1 1 0 1 1-2 0 1 1 0 0 1 2 0Z',
	caution: 'M4.47.22A.749.749 0 0 1 5 0h6c.199 0 .389.079.53.22l4.25 4.25c.141.14.22.331.22.53v6a.749.749 0 0 1-.22.53l-4.25 4.25A.749.749 0 0 1 11 16H5a.749.749 0 0 1-.53-.22L.22 11.53A.749.749 0 0 1 0 11V5c0-.199.079-.389.22-.53Zm.84 1.28L1.5 5.31v5.38l3.81 3.81h5.38l3.81-3.81V5.31L10.69 1.5ZM8 4a.75.75 0 0 1 .75.75v3.5a.75.75 0 0 1-1.5 0v-3.5A.75.75 0 0 1 8 4Zm0 8a1 1 0 1 1 0-2 1 1 0 0 1 0 2Z',
	// Obsidian's other callouts
	abstract: 'M3.626 3.533a.249.249 0 0 0-.126.217v9.5c0 .138.112.25.25.25h8.5a.25.25 0 0 0 .25-.25v-9.5a.249.249 0 0 0-.126-.217.75.75 0 0 1 .752-1.298c.541.313.874.89.874 1.515v9.5A1.75 1.75 0 0 1 12.25 15h-8.5A1.75 1.75 0 0 1 2 13.25v-9.5c0-.625.333-1.202.874-1.515a.75.75 0 0 1 .752 1.298ZM5.75 1h4.5a.75.75 0 0 1 .75.75v3a.75.75 0 0 1-.75.75h-4.5A.75.75 0 0 1 5 4.75v-3A.75.75 0 0 1 5.75 1Zm.75 3h3V2.5h-3Z',
	todo: 'M0 8a8 8 0 1 1 16 0A8 8 0 0 1 0 8Zm1.5 0a6.5 6.5 0 1 0 13 0 6.5 6.5 0 0 0-13 0Zm10.28-1.72-4.5 4.5a.75.75 0 0 1-1.06 0l-2-2a.751.751 0 0 1 .018-1.042.751.751 0 0 1 1.042-.018l1.47 1.47 3.97-3.97a.751.751 0 0 1 1.042.018.751.751 0 0 1 .018 1.042Z',
	success: 'M13.78 4.22a.75.75 0 0 1 0 1.06l-7.25 7.25a.75.75 0 0 1-1.06 0L2.22 9.28a.751.751 0 0 1 .018-1.042.751.751 0 0 1 1.042-.018L6 10.94l6.72-6.72a.75.75 0 0 1 1.06 0Z',
	question: 'M0 8a8 8 0 1 1 16 0A8 8 0 0 1 0 8Zm8-6.5a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0-13ZM6.92 6.085h.001a.749.749 0 1 1-1.342-.67c.169-.339.436-.701.849-.977C6.845 4.16 7.369 4 8 4a2.756 2.756 0 0 1 1.637.525c.503.377.863.965.863 1.725 0 .448-.115.83-.329 1.15-.205.307-.47.513-.692.662-.109.072-.22.138-.313.195l-.006.004a6.24 6.24 0 0 0-.26.16.952.952 0 0 0-.276.245.75.75 0 0 1-1.248-.832c.184-.264.42-.489.692-.661.103-.067.207-.132.313-.195l.007-.004c.1-.061.182-.11.258-.161a.969.969 0 0 0 .277-.245C8.96 6.514 9 6.427 9 6.25a.612.612 0 0 0-.262-.525A1.27 1.27 0 0 0 8 5.5c-.369 0-.595.09-.74.187a1.01 1.01 0 0 0-.34.398ZM9 11a1 1 0 1 1-2 0 1 1 0 0 1 2 0Z',
	failure: 'M2.344 2.343h-.001a8 8 0 0 1 11.314 11.314A8.002 8.002 0 0 1 .234 10.089a8 8 0 0 1 2.11-7.746Zm1.06 10.253a6.5 6.5 0 1 0 9.108-9.275 6.5 6.5 0 0 0-9.108 9.275ZM6.03 4.97 8 6.94l1.97-1.97a.749.749 0 0 1 1.275.326.749.749 0 0 1-.215.734L9.06 8l1.97 1.97a.749.749 0 0 1-.326 1.275.749.749 0 0 1-.734-.215L8 9.06l-1.97 1.97a.749.749 0 0 1-1.275-.326.749.749 0 0 1 .215-.734L6.94 8 4.97 6.03a.751.751 0 0 1 .018-1.042.751.751 0 0 1 1.042-.018Z',
	danger: 'M9.504.43a1.516 1.516 0 0 1 2.437 1.713L10.415 5.5h2.123c1.57 0 2.346 1.909 1.22 3.004l-7.34 7.142a1.249 1.249 0 0 1-.871.354h-.302a1.25 1.25 0 0 1-1.157-1.723L5.633 10.5H3.462c-1.57 0-2.346-1.909-1.22-3.004L9.503.429Zm1.047 1.074L3.286 8.571A.25.25 0 0 0 3.462 9H6.75a.75.75 0 0 1 .694 1.034l-1.713 4.188 6.982-6.793A.25.25 0 0 0 12.538 7H9.25a.75.75 0 0 1-.683-1.06l2.008-4.418.003-.006a.036.036 0 0 0-.004-.009l-.006-.006-.008-.001c-.003 0-.006.002-.009.004Z',
	bug: 'M4.72.22a.75.75 0 0 1 1.06 0l1 .999a3.488 3.488 0 0 1 2.441 0l.999-1a.748.748 0 0 1 1.265.332.75.75 0 0 1-.205.729l-.775.776c.616.63.995 1.493.995 2.444v.327c0 .1-.009.197-.025.292.408.14.764.392 1.029.722l1.968-.787a.75.75 0 0 1 .556 1.392L13 7.258V9h2.25a.75.75 0 0 1 0 1.5H13v.5c0 .409-.049.806-.141 1.186l2.17.868a.75.75 0 0 1-.557 1.392l-2.184-.873A4.997 4.997 0 0 1 8 16a4.997 4.997 0 0 1-4.288-2.427l-2.183.873a.75.75 0 0 1-.558-1.392l2.17-.868A5.036 5.036 0 0 1 3 11v-.5H.75a.75.75 0 0 1 0-1.5H3V7.258L.971 6.446a.75.75 0 0 1 .558-1.392l1.967.787c.265-.33.62-.583 1.03-.722a1.677 1.677 0 0 1-.026-.292V4.5c0-.951.38-1.814.995-2.444L4.72 1.28a.75.75 0 0 1 0-1.06Zm.53 6.28a.75.75 0 0 0-.75.75V11a3.5 3.5 0 1 0 7 0V7.25a.75.75 0 0 0-.75-.75ZM6.173 5h3.654A.172.172 0 0 0 10 4.827V4.5a2 2 0 1 0-4 0v.327c0 .096.077.173.173.173Z',
	example: 'M5.75 2.5h8.5a.75.75 0 0 1 0 1.5h-8.5a.75.75 0 0 1 0-1.5Zm0 5h8.5a.75.75 0 0 1 0 1.5h-8.5a.75.75 0 0 1 0-1.5Zm0 5h8.5a.75.75 0 0 1 0 1.5h-8.5a.75.75 0 0 1 0-1.5ZM2 14a1 1 0 1 1 0-2 1 1 0 0 1 0 2Zm1-6a1 1 0 1 1-2 0 1 1 0 0 1 2 0ZM2 4a1 1 0 1 1 0-2 1 1 0 0 1 0 2Z',
	quote: 'M1.75 2.5h10.5a.75.75 0 0 1 0 1.5H1.75a.75.75 0 0 1 0-1.5Zm4 5h8.5a.75.75 0 0 1 0 1.5h-8.5a.75.75 0 0 1 0-1.5Zm0 5h8.5a.75.75 0 0 1 0 1.5h-8.5a.75.75 0 0 1 0-1.5ZM2.5 7.75v6a.75.75 0 0 1-1.5 0v-6a.75.75 0 0 1 1.5 0Z' };
ALERT_ICONS.info = ALERT_ICONS.note; // Octicons' info is GitHub's note icon
/** a GitHub alert's type, or a callout's { type, title (HTML), tag } */
function alertTitle(a) {
	const c = typeof a === 'string' ? { type: a, title: a[0].toUpperCase() + a.slice(1), tag: 'p' } : a;
	return `<${c.tag} class="markdown-alert-title"><svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true"><path d="${ALERT_ICONS[c.type]}"></path></svg>${c.title}</${c.tag}>\n`;
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

let BASE = '', SRC = '', WIKILINKS = {}, PLANTUML = true, MERMAID = {}, FONTVARS = '', COLORS = { dark: {}, light: {} }, FONT = { name: 'system-ui', size: 11 }, THEME = 'dark', SPACING = { node: 25, rank: 30, pad: 6 };
const PLUGINS = typeof window !== 'undefined' ? [[window.markdownitFootnote], [window.markdownitEmoji, { shortcuts: {} }]] : []; // no :) -> emoji, as on GitHub
const md = typeof window !== 'undefined' ? createMd(window.markdownit, window.markdownitTaskLists, window.hljs, () => BASE, window.markdownitKatex, window.katex, PLUGINS, () => PLANTUML) : null;

function applyStyle(font, size, code, csize, lh, theme, breaks, colors, spacing, plantuml, ls) {
	PLANTUML = plantuml !== false; // off: a plantuml block stays a plain code block
	FULL = true; // fonts, colors and diagram settings changed: everything is drawn again, not patched
	md.set({ breaks: !!breaks }); // keep single newlines as <br>
	const r = document.documentElement, s = r.style;
	r.dataset.theme = theme;
	s.setProperty('--font', `'${font}', system-ui, sans-serif`);
	s.setProperty('--size', size + 'pt');
	s.setProperty('--code-font', `'${code}', Consolas, monospace`);
	s.setProperty('--code-size', csize + 'pt');
	s.setProperty('--lh', lh);
	// letter spacing as CSS writes it (normal, -0.02em, 0.5px); a bare number is px, as in letter-spacing:-1px
	const lsv = /^\s*-?\d*\.?\d+\s*$/.test(ls || '') ? ls.trim() + 'px' : ls;
	s.setProperty('--ls', lsv && CSS.supports('letter-spacing', lsv) ? lsv : 'normal');
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

function render(src, base, wiki) {
	SRC = src; BASE = base; WIKILINKS = wiki || {};
	const c = document.getElementById('c');
	const y = window.scrollY;
	const all = patch(c, sanitize(md.render(src, { wiki: WIKILINKS })));
	drawPlantUml(c, THEME);
	if (all) window.scrollTo(0, y); // a patch leaves what is above untouched, and the browser keeps its place on the page
	copyButtons(c);
	findRun(false);
	rebuildMap();
	// diagrams grow once drawn, which moves everything below them: aim again then.
	// Only the ones not drawn yet: those of blocks that did not change are still there.
	const drawn = () => { c.querySelectorAll('.mermaid').forEach(cleanSvg); doScroll(); rebuildMap(); findRun(false); drawMarks(); c.querySelectorAll('.mermaid').forEach(panZoom); };
	c.querySelectorAll('.mermaid:not([data-processed])').forEach(e => { e.dataset.src = e.textContent; }); // for plainText: mermaid swaps it for the SVG
	mermaid.run({ querySelector: '#c .mermaid:not([data-processed])', suppressErrors: true }).then(drawn, drawn);
	document.fonts.ready.then(() => { doScroll(); rebuildMap(); drawMarks(); }); // so do formulas once the KaTeX fonts are in
	drawMarks();
}

// Typing changes a block or two. Replacing the whole page would draw every diagram again (Mermaid is by far the
// slowest part) and throw away the scroll anchor, so the page is brought in line with the fresh render instead:
// the blocks at the start and at the end that did not change stay, only the middle is replaced.
let FULL = true; // the first render, and after a style change: replace everything
// What a node looked like when it was inserted, before a diagram was drawn into it: that is what a fresh render of
// the same block looks like, and it is kept on the node as _k. A block below an inserted line keeps its look, only
// its data-line moves, so that is left out (on a copy: a regex over the markup would also eat the text data-line="3" in code).
function lineEls(n) { return [n, ...n.querySelectorAll('[data-line]')].filter(e => e.hasAttribute('data-line')); }
function keyOf(n) {
	if (n.nodeType !== 1) return 't' + n.nodeValue;
	const k = n.cloneNode(true);
	for (const e of lineEls(k)) e.removeAttribute('data-line');
	return 'e' + k.outerHTML;
}
function copyLines(o, n) {
	if (o.nodeType !== 1) return;
	const oe = lineEls(o), ne = lineEls(n);
	if (oe.length === ne.length) oe.forEach((e, i) => e.setAttribute('data-line', ne[i].getAttribute('data-line')));
}
/** Returns true when everything was replaced. */
function patch(c, frag) {
	if (FULL) {
		FULL = false;
		for (const n of frag.childNodes) n._k = keyOf(n);
		c.replaceChildren(frag);
		return true;
	}
	const oldN = [...c.childNodes], newN = [...frag.childNodes];
	const ok = oldN.map(n => n._k || (n._k = keyOf(n))), nk = newN.map(keyOf);
	let a = 0, b = 0;
	while (a < oldN.length && a < newN.length && ok[a] === nk[a]) a++;
	while (b < oldN.length - a && b < newN.length - a && ok[oldN.length - 1 - b] === nk[newN.length - 1 - b]) b++;
	for (let i = 0; i < a; i++) copyLines(oldN[i], newN[i]);
	for (let i = 0; i < b; i++) copyLines(oldN[oldN.length - 1 - i], newN[newN.length - 1 - i]);
	const before = b > 0 ? oldN[oldN.length - b] : null;
	for (let i = a; i < oldN.length - b; i++) oldN[i].remove();
	for (let i = a; i < newN.length - b; i++) { newN[i]._k = nk[i]; c.insertBefore(newN[i], before); }
	return false;
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
		if (mermaidRemote(e.textContent)) e.replaceWith(Object.assign(document.createElement('pre'),
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
// Mermaid source: only what can load something is checked (url(), src, srcset, href, img:, @import...), so a label may name
// a network path as plain text.
function mermaidRemote(src) {
	// all HTML entities (the browser's own table) and Mermaid's #92; until nothing changes, then CSS escapes
	const ta = document.createElement('textarea');
	let t = src;
	for (let i = 0; i < 4; i++) {
		// Mermaid's #name; and #92; are its &name; and &#92;; a textarea then parses entities only, no markup
		ta.innerHTML = t.replace(/(?<!&)#(\w+);/g, (m, n) => /^\+?\d+$/.test(n) ? '&#' + n + ';' : '&' + n + ';').replace(/</g, '&lt;');
		const d = ta.value.replace(/&?#(x[0-9a-f]+|\d+);/gi, (m, c) => String.fromCodePoint(Math.min(c[0] === 'x' || c[0] === 'X' ? parseInt(c.slice(1), 16) : +c, 0x10ffff)));
		if (d === t) break;
		t = d;
	}
	t = t.replace(/\\([0-9a-f]{1,6})\s?/gi, (m, h) => String.fromCodePoint(Math.min(parseInt(h, 16), 0xfffd)));
	if (/image-set/i.test(t)) return true;
	// everything after the first thing that loads (src, srcset, url(), @import...) to the end of the diagram: every token in it
	// is checked, and one that still looks like an entity (&name; or #name;) is refused. Not cut at > or }: a quoted value
	// (srcset="x>y 1x, //host/a 2x") can hold them. So a diagram that loads something may not name a network path later on
	const m = /(?:\b(?:src|srcset|href|xlink:href|img|poster|background|data|action|formaction|cite|lowsrc|dynsrc|ping)\s*[:=]|url\s*\(|@import)([\s\S]*)/i.exec(t);
	// read twice: split at any white space (srcset candidates), and with tabs and line ends taken out first, as the URL parser
	// does (src="/<tab>/host" is //host to the browser)
	const bad = u => u && (/[&#][a-z0-9#]+;/i.test(u) || remote(u));
	if (m && (m[1].split(/[\s,"'()<>{}]+/).some(bad) || m[1].replace(/[\t\n\r]/g, '').split(/[ \f\v,"'()<>{}]+/).some(bad))) return true;
	return false;
}
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

// Mermaid draws with securityLevel strict, and its SVG goes through DOMPurify too, as PlantUML's does. Its labels are HTML
// in <foreignObject>, which DOMPurify drops by default: the result comes back as nodes, not as markup parsed again, so
// letting HTML in there is safe, and scripts, handlers, javascript: links and network paths still go.
const MERMAID_PURIFY = { RETURN_DOM_FRAGMENT: true, USE_PROFILES: { svg: true, svgFilters: true, html: true },
	ADD_TAGS: ['foreignObject'], HTML_INTEGRATION_POINTS: { foreignobject: true } };
function cleanSvg(e) {
	if (e.dataset.clean || !e.querySelector(':scope > svg')) return;
	e.dataset.clean = '1';
	e.replaceChildren(DOMPurify.sanitize(e.innerHTML, MERMAID_PURIFY));
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
	for (const e of root.querySelectorAll('.plantuml:not([data-drawn])')) { // blocks that did not change are drawn already
		e.dataset.drawn = '1';
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

// the block that starts at the source line (0-based), or the last one before it
function blockAt(n) {
	let best = null;
	for (const e of document.querySelectorAll('#c [data-line]')) if (+e.dataset.line <= n && (!best || +e.dataset.line > +best.dataset.line)) best = e;
	return best;
}

// Outline click
function scrollToLine(n) {
	const best = blockAt(n);
	PENDING = -1;
	if (best) best.scrollIntoView({ block: 'start' });
}

// Bookmarks of the Source ([{line, quick}], lines from 0): a ribbon in the left margin beside the block of each,
// outside #c so that copy, export and find never see them
let MARKS = [], MARK_AT = null;
function setBookmarks(marks) {
	MARKS = marks;
	drawMarks();
}

function drawMarks() {
	document.querySelectorAll('body > .bm').forEach(e => e.remove());
	for (const m of MARKS) {
		const b = blockAt(m.line);
		if (!b) continue;
		const d = document.createElement('div');
		d.className = 'bm';
		d.textContent = m.quick ?? '';
		d.style.top = (b.getBoundingClientRect().top + window.scrollY) + 'px';
		document.body.append(d);
	}
}

// next (1) or previous (-1) bookmark from the last one reached, or from the top of the view; around the document
function markStep(dir) {
	const blocks = [...new Set(MARKS.map(m => blockAt(m.line)).filter(Boolean))];
	if (!blocks.length) return;
	const tops = blocks.map(b => b.getBoundingClientRect().top).sort((a, b) => a - b);
	blocks.sort((a, b) => a.getBoundingClientRect().top - b.getBoundingClientRect().top);
	const from = MARK_AT && MARK_AT.isConnected ? MARK_AT.getBoundingClientRect().top : 0;
	let i = dir > 0 ? tops.findIndex(t => t > from + 1) : tops.findLastIndex(t => t < from - 1);
	if (i < 0) i = dir > 0 ? 0 : blocks.length - 1;
	MARK_AT = blocks[i];
	PENDING = -1;
	MARK_AT.scrollIntoView({ block: 'start' });
}

// the source line of the first block that starts in view: where a bookmark set on the Preview goes
function topLine() {
	for (const e of document.querySelectorAll('#c [data-line]')) if (e.getBoundingClientRect().top >= -1) return +e.dataset.line;
	return 0;
}

// Copy button on code blocks: the code as written, through the plugin's clipboard (mdCopy) when there is one,
// as navigator.clipboard needs a secure context and permission that a file: page in WebView2 or WebKitGTK may not get
function copyButtons(root) {
	for (const code of root.querySelectorAll('pre > code')) {
		const pre = code.parentElement;
		if (pre.parentElement.classList.contains('code-box')) continue; // a block that stayed in a patch has its button already
		const box = document.createElement('div'), b = document.createElement('button');
		box._k = pre._k; // the key of the block as the fresh render has it, see patch()
		box.className = 'code-box';
		b.className = 'copy';
		b.textContent = L.copy;
		b.onclick = () => {
			const text = code.textContent.replace(/\n$/, '');
			if (typeof window.mdCopy === 'function') window.mdCopy(text);
			else navigator.clipboard.writeText(text);
			b.textContent = L.copied;
			setTimeout(() => { b.textContent = L.copy; }, 1500);
		};
		pre.replaceWith(box);
		box.append(pre, b);
	}
}

// Mermaid diagrams: Ctrl+wheel zooms around the pointer, dragging moves, the button brings back the original size
function panZoom(box) {
	const svg = box.querySelector(':scope > svg');
	if (!svg || box.querySelector('.zoom-reset')) return;
	let s = 1, x = 0, y = 0, drag = null;
	const reset = Object.assign(document.createElement('button'), { className: 'zoom-reset', textContent: L.zoomReset, hidden: true });
	const apply = () => {
		svg.style.transform = s === 1 && !x && !y ? '' : `translate(${x}px, ${y}px) scale(${s})`;
		reset.hidden = !svg.style.transform;
	};
	reset.onclick = () => { s = 1; x = y = 0; apply(); };
	svg.style.transformOrigin = '0 0';
	box.append(reset);
	box.addEventListener('wheel', e => {
		if (!e.ctrlKey) return; // plain wheel scrolls the page
		e.preventDefault(); // not the browser's page zoom
		const r = svg.getBoundingClientRect(), ox = r.left - x, oy = r.top - y;
		const px = (e.clientX - r.left) / s, py = (e.clientY - r.top) / s;
		s = Math.min(10, Math.max(0.2, s * Math.exp(-e.deltaY * 0.002)));
		x = e.clientX - px * s - ox;
		y = e.clientY - py * s - oy;
		apply();
	}, { passive: false });
	box.addEventListener('pointerdown', e => {
		if (e.button !== 0 || e.target === reset) return;
		drag = { cx: e.clientX, cy: e.clientY, x, y };
		box.setPointerCapture(e.pointerId);
		e.preventDefault();
	});
	box.addEventListener('pointermove', e => {
		if (!drag) return;
		x = drag.x + e.clientX - drag.cx;
		y = drag.y + e.clientY - drag.cy;
		apply();
	});
	const end = () => { drag = null; };
	box.addEventListener('pointerup', end);
	box.addEventListener('pointercancel', end);
}

// Find in the preview (Ctrl+F on the Preview tab). All matches are highlighted with the CSS Custom Highlight API
// (no change to the page, works in diagram text too); without it only the count and moving to a match work.
let FIND = null, HITS = [], HIT = -1;
function findOpen() {
	if (!FIND) {
		FIND = document.createElement('div');
		FIND.id = 'find';
		const input = Object.assign(document.createElement('input'), { type: 'text', placeholder: L.find, spellcheck: false });
		const button = (text, title, d) => Object.assign(document.createElement('button'), { textContent: text, title, onclick: () => d ? findStep(d) : findClose() });
		FIND.append(input, document.createElement('span'), button('\u2191', L.findPrev, -1), button('\u2193', L.findNext, 1), button('\u00d7', L.findClose, 0));
		input.addEventListener('input', () => findRun(true));
		input.addEventListener('keydown', e => {
			if (e.key === 'Enter') { e.preventDefault(); findStep(e.shiftKey ? -1 : 1); }
		});
		document.body.append(FIND);
	}
	FIND.hidden = false;
	const input = FIND.querySelector('input');
	input.focus();
	input.select();
	findRun(true);
}

function findClose() {
	if (!FIND) return;
	FIND.hidden = true;
	HITS = [];
	if (window.CSS && CSS.highlights) CSS.highlights.clear();
}

// all matches, ignoring case, at most FIND_MAX; from the first one (typing) or near the current one (the page was drawn again).
// Never throws: it runs inside render(), before the diagrams are drawn.
const FIND_MAX = 10000;
let FIND_MORE = false;
function findRun(fromStart) {
	if (!FIND || FIND.hidden) return;
	try {
		const q = FIND.querySelector('input').value.toLowerCase();
		HITS = [];
		FIND_MORE = false;
		if (q) {
			// not the MathML copy of formulas (the visible KaTeX HTML is searched) or the inside of a closed <details>
			const w = document.createTreeWalker(document.getElementById('c'), NodeFilter.SHOW_TEXT, { acceptNode: n =>
				n.parentElement.closest('.katex-mathml, style, button, details:not([open]) > :not(summary)') ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT });
			scan: for (let n; (n = w.nextNode());) {
				const t = n.data.toLowerCase();
				for (let i = t.indexOf(q); i >= 0; i = t.indexOf(q, i + q.length)) {
					if (HITS.length === FIND_MAX) { FIND_MORE = true; break scan; }
					const r = document.createRange();
					r.setStart(n, i);
					r.setEnd(n, i + q.length);
					HITS.push(r);
				}
			}
		}
		HIT = HITS.length ? (fromStart ? 0 : Math.min(Math.max(HIT, 0), HITS.length - 1)) : -1;
		if (window.CSS && CSS.highlights) {
			const h = new Highlight(); // added one by one: new Highlight(...HITS) overflows the stack past ~125,000
			for (const r of HITS) h.add(r);
			CSS.highlights.set('find', h);
		}
		findShow(fromStart);
	} catch (e) {
		HITS = [];
		HIT = -1;
	}
}

function findStep(d) {
	if (!HITS.length) return;
	HIT = (HIT + d + HITS.length) % HITS.length;
	findShow(true);
}

function findShow(scroll) {
	FIND.querySelector('span').textContent = HITS.length ? `${HIT + 1}/${HITS.length}${FIND_MORE ? '+' : ''}` : FIND.querySelector('input').value ? L.findNone : '';
	if (window.CSS && CSS.highlights) {
		if (HIT >= 0) CSS.highlights.set('find-current', new Highlight(HITS[HIT]));
		else CSS.highlights.delete('find-current');
	}
	if (scroll && HIT >= 0) {
		PENDING = -1;
		HITS[HIT].startContainer.parentElement.scrollIntoView({ block: 'center', inline: 'nearest' });
	}
}

if (typeof window !== 'undefined') window.addEventListener('keydown', e => {
	if (e.ctrlKey && e.shiftKey && !e.altKey && e.code === 'Minus' && typeof window.mdKey === 'function') {
		e.preventDefault(); // Ctrl+_ splits the editor in Eclipse; the browser would zoom out
		window.mdKey('M1+M2+-');
	} else if ((e.ctrlKey || e.metaKey) && !e.altKey && !e.shiftKey && e.key.toLowerCase() === 'f') {
		e.preventDefault(); // instead of the browser's own find bar
		findOpen();
	} else if (e.key === 'F3' && !e.altKey) { // Alt+F3 is the next bookmark
		e.preventDefault();
		if (FIND && !FIND.hidden) findStep(e.shiftKey ? -1 : 1);
		else findOpen();
	} else if (e.key === 'Escape' && FIND && !FIND.hidden) {
		findClose();
	}
});

// double-click: Source tab at the line the block starts on (data-line), through the plugin (mdSource)
if (typeof window !== 'undefined') window.addEventListener('dblclick', e => {
	if (typeof window.mdSource !== 'function' || e.target.closest('#find, a, button, input, summary')) return;
	const b = e.target.closest('#c [data-line]');
	if (b) window.mdSource(+b.dataset.line);
});

// the user scrolling takes over from a pending jump
if (typeof window !== 'undefined') {
	window.addEventListener('wheel', () => { PENDING = -1; MARK_AT = null; }, { passive: true });
	window.addEventListener('resize', () => drawMarks());
	window.addEventListener('keydown', () => { PENDING = -1; });
}

// Export HTML / Copy HTML: the preview as it is drawn (diagrams included), current theme, without the page's buttons
function renderedClone(styled) {
	const src = document.getElementById('c'), c = src.cloneNode(true);
	if (styled) { // every element gets its computed look in style="", for mail and wiki editors that drop <style>
		const a = [src, ...src.querySelectorAll('*')], b = [c, ...c.querySelectorAll('*')];
		a.forEach((e, i) => {
			if (!(e instanceof HTMLElement)) return; // SVG keeps its own styles
			const cs = getComputedStyle(e);
			b[i].style.cssText = INLINE.map(p => `${p}:${cs.getPropertyValue(p)}`).join(';');
		});
	}
	c.querySelectorAll('.copy, .zoom-reset').forEach(e => e.remove());
	c.querySelectorAll('.mermaid > svg').forEach(e => { e.style.transform = ''; });
	return c;
}
const INLINE = ['color', 'background-color', 'font-family', 'font-size', 'font-weight', 'font-style', 'line-height', 'text-align', 'text-decoration-line',
	'white-space', 'margin', 'padding', 'border-top', 'border-right', 'border-bottom', 'border-left', 'border-radius', 'border-collapse', 'list-style-type', 'vertical-align', 'display'];

// whole page; the plugin puts the CSS in place of /*CSS*/ (the first one, in <head>)
function htmlDoc(title) {
	const vars = (FONTVARS + Object.entries(COLORS[THEME] || {}).map(([k, v]) => `${k}:${v};`).join('')).replace(/"/g, '&quot;');
	return `<!doctype html><html data-theme="${THEME}" style="${vars}"><head><meta charset="utf-8"><title>${md.utils.escapeHtml(title)}</title><style>/*CSS*/</style></head>`
		+ `<body><div id="c">${renderedClone(false).innerHTML}</div></body></html>`;
}

// [html, plain text] for the clipboard
function htmlFragment() {
	const c = renderedClone(true), cs = getComputedStyle(document.body);
	return [`<div style="color:${cs.color};background-color:${cs.backgroundColor};font-family:${cs.fontFamily.replace(/"/g, "'")};font-size:${cs.fontSize};line-height:${cs.lineHeight}">${c.innerHTML}</div>`, plainText(true)];
}

// Copy and Select All of the Preview tab (Ctrl+C, Ctrl+A, context menu): in the find bar's box its text, else the page
function copyText() {
	const a = document.activeElement;
	return a instanceof HTMLInputElement ? a.value.substring(a.selectionStart, a.selectionEnd) : plainText(false);
}
function selectAll() {
	const a = document.activeElement;
	if (a instanceof HTMLInputElement) a.select();
	else getSelection().selectAllChildren(document.getElementById('c'));
}

// text for the clipboard, of the selection or the whole page: formulas as their TeX ($...$), diagrams as their source, no button labels
function plainText(whole) {
	const box = document.createElement('div'), sel = getSelection();
	if (whole) box.append(...[...document.getElementById('c').childNodes].map(n => n.cloneNode(true)));
	else for (let i = 0; i < sel.rangeCount; i++) box.append(sel.getRangeAt(i).cloneContents());
	box.querySelectorAll('.copy, .zoom-reset').forEach(e => e.remove());
	box.querySelectorAll('.katex').forEach(e => {
		const tex = e.querySelector('annotation[encoding="application/x-tex"]');
		if (tex) e.replaceWith(e.parentElement?.classList.contains('katex-display') ? `$$${tex.textContent}$$` : `$${tex.textContent}$`);
	});
	box.querySelectorAll('.katex-mathml').forEach(e => e.remove()); // a formula cut by the selection: as shown
	box.querySelectorAll('.mermaid').forEach(e => { if (e.dataset.src) e.replaceWith(Object.assign(document.createElement('pre'), { textContent: e.dataset.src.trim() })); });
	box.style.cssText = 'position:absolute;left:-99999px;top:0';
	document.body.append(box); // innerText needs layout
	const text = box.innerText;
	box.remove();
	return text;
}

// standalone light-theme page for headless Edge --print-to-pdf (mermaid renders there on load)
function exportDoc() {
	const vars = (FONTVARS + Object.entries(COLORS.light).map(([k, v]) => `${k}:${v};`).join('')).replace(/"/g, '&quot;');
	return `<!doctype html><html data-theme="light" style="${vars}"><head><meta charset="utf-8">`
		+ '<link rel="stylesheet" href="preview.css"><link rel="stylesheet" href="hl-light.css"><link rel="stylesheet" href="katex.min.css">'
		+ '<script src="mermaid.min.js"></script><script>mermaid.initialize(' + JSON.stringify({ ...MERMAID, startOnLoad: true, theme: 'default' }).replace(/</g, '\\u003c') + ')</script>'
		+ `</head><body class="export"><div id="c">${(() => { const d = document.createElement('div'); d.append(sanitize(md.render(SRC, { wiki: WIKILINKS }))); drawPlantUml(d, 'light'); return d.innerHTML; })()}</div></body></html>`;
}
