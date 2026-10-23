package io.github.insjang.mdviewer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Headings of a Markdown source for the outline and the link check: ATX (#) and setext (=== / ---), not in fenced code or front matter. */
final class Headings {
	/** level 1-6, text as written (inline markup kept), line from 0 (= data-line in the preview), id as the preview makes it */
	record Heading(int level, String text, int line, String id) {
	}

	record Node(Heading heading, List<Node> children) {
	}

	// no overlapping quantifiers (a crafted line must not backtrack): the closing #s are cut in code, see atxText()
	private static final Pattern ATX = Pattern.compile("^ {0,3}(#{1,6})(?:[ \\t]+(.*))?$");
	private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})");
	private static final Pattern SETEXT = Pattern.compile("^ {0,3}(=+|-+)[ \\t]*$");
	/** lines that start something other than a paragraph, so a --- below them is a rule, not a heading */
	private static final Pattern NOT_TEXT = Pattern.compile("^(?: {4}|\\t| {0,3}(?:[>|<]|[-*+](?:[ \\t]|$)|\\d{1,9}[.)](?:[ \\t]|$)|#{1,6}(?:[ \\t]|$)))");

	static List<Heading> of(String src) {
		String[] lines = src.split("\r?\n|\r", -1);
		List<Heading> out = new ArrayList<>();
		Map<String, Integer> seen = new HashMap<>();
		int i = frontMatterEnd(lines), para = -1;
		String fence = null;
		for (; i < lines.length; i++) {
			String l = lines[i];
			if (fence != null) { // inside ``` or ~~~ until a fence of the same kind, at least as long
				String t = l.strip();
				if (t.startsWith(fence) && t.replace(t.substring(0, 1), "").isEmpty()) fence = null;
				continue;
			}
			Matcher m = FENCE.matcher(l);
			if (m.find() && !(m.group(1).charAt(0) == '`' && l.substring(m.end()).contains("`"))) {
				fence = m.group(1);
				para = -1;
				continue;
			}
			if (l.isBlank()) {
				para = -1;
				continue;
			}
			m = SETEXT.matcher(l);
			if (para >= 0 && m.matches()) {
				StringBuilder t = new StringBuilder();
				for (int p = para; p < i; p++) t.append(p > para ? " " : "").append(lines[p].strip());
				out.add(heading(m.group(1).charAt(0) == '=' ? 1 : 2, t.toString(), para, seen));
				para = -1;
				continue;
			}
			m = ATX.matcher(l);
			if (m.matches()) {
				out.add(heading(m.group(1).length(), atxText(m.group(2)), i, seen));
				para = -1;
			} else if (para < 0) {
				para = NOT_TEXT.matcher(l).find() ? -1 : i;
			}
		}
		return out;
	}

	/** h2 under the h1 above it, and so on; a deeper level with no parent starts at the top. */
	static List<Node> tree(List<Heading> list) {
		List<Node> roots = new ArrayList<>();
		List<Node> stack = new ArrayList<>();
		for (Heading h : list) {
			while (!stack.isEmpty() && stack.get(stack.size() - 1).heading().level() >= h.level()) stack.remove(stack.size() - 1);
			Node n = new Node(h, new ArrayList<>());
			(stack.isEmpty() ? roots : stack.get(stack.size() - 1).children()).add(n);
			stack.add(n);
		}
		return roots;
	}

	/** Text after "# ": without trailing blanks and a closing # sequence that follows a blank or is all there is (CommonMark). */
	static String atxText(String t) {
		if (t == null) return "";
		int end = t.length();
		while (end > 0 && (t.charAt(end - 1) == ' ' || t.charAt(end - 1) == '\t')) end--;
		int h = end;
		while (h > 0 && t.charAt(h - 1) == '#') h--;
		if (h < end && (h == 0 || t.charAt(h - 1) == ' ' || t.charAt(h - 1) == '\t')) end = h;
		return t.substring(0, end);
	}

	/** First line after the front matter, as preview.js finds it: --- on line 1 up to the next --- or ... line. */
	static int frontMatterEnd(String[] lines) {
		if (lines.length == 0 || !lines[0].stripTrailing().equals("---")) return 0;
		for (int i = 1; i < lines.length; i++)
			if (lines[i].matches("(---|\\.\\.\\.)\\s*")) return i + 1;
		return 0;
	}

	private static Heading heading(int level, String text, int line, Map<String, Integer> seen) {
		String id = slug(text);
		int n = seen.merge(id, 1, Integer::sum);
		return new Heading(level, text.strip(), line, n > 1 ? id + "-" + (n - 1) : id);
	}

	/** The id preview.js gives a heading (before DOMPurify's user-content- prefix). */
	static String slug(String text) {
		return text.strip().toLowerCase(Locale.ROOT).replaceAll("(?U)[^\\p{L}\\p{N}\\s_-]", "").replaceAll("(?U)\\s", "-");
	}

	/** Heading text without the commonest inline markup, for labels. */
	static String label(String text) {
		return text.replaceAll("!?\\[([^\\[\\]]*)\\]\\([^()]*\\)", "$1").replaceAll("\\*\\*|__|`", "");
	}

	private Headings() {
	}
}
