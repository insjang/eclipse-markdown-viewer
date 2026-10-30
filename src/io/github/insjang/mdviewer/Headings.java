package io.github.insjang.mdviewer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Headings of a Markdown source for the outline and the link check: ATX (#) and setext (=== / ---), not in fenced code or front matter. */
public final class Headings {
	/** level 1-6, text as written (inline markup kept), line from 0 (= data-line in the preview), id as the preview makes it */
	public record Heading(int level, String text, int line, String id) {
	}

	record Node(Heading heading, List<Node> children) {
	}

	private static final Pattern SETEXT = Pattern.compile("^ {0,3}(=+|-+)[ \\t]*$");
	/** lines that start something other than a paragraph, so a --- below them is a rule, not a heading */
	private static final Pattern NOT_TEXT = Pattern.compile("^(?: {4}|\\t| {0,3}(?:[>|<]|[-*+](?:[ \\t]|$)|\\d{1,9}[.)](?:[ \\t]|$)|#{1,6}(?:[ \\t]|$)))");

	public static List<Heading> of(String src) {
		String[] lines = lines(src);
		boolean[] code = code(lines);
		List<Heading> out = new ArrayList<>();
		Map<String, Integer> seen = new HashMap<>();
		int para = -1;
		for (int i = 0; i < lines.length; i++) {
			String l = lines[i];
			Matcher m;
			if (code[i]) {
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
			int level = atxLevel(l);
			if (level > 0) {
				out.add(heading(level, atxText(l.substring(indent(l) + level)), i, seen));
				para = -1;
			} else if (rule(l)) {
				para = -1; // ---, ***, ___: a rule, also ends a paragraph (--- under text was taken as setext above)
			} else if (para < 0) {
				para = NOT_TEXT.matcher(l).find() ? -1 : i;
			}
		}
		return out;
	}

	static String[] lines(String src) {
		return src.split("\r?\n|\r", -1);
	}

	// headings, rules and fences are read by hand, not with regexes: a crafted line (spaces, then U+2028 or U+0085,
	// which Java's . and $ do not take) made the pattern backtrack quadratically on the UI thread

	/** spaces in front, up to 4 (4 = indented code) */
	private static int indent(String l) {
		int n = 0;
		while (n < l.length() && n < 4 && l.charAt(n) == ' ') n++;
		return n;
	}

	/** 1 to 6 for "# ..." to "###### ..." (up to 3 spaces before, a blank or the end after the #s), else 0 */
	static int atxLevel(String l) {
		int i = indent(l), n = 0;
		if (i > 3) return 0;
		while (i + n < l.length() && l.charAt(i + n) == '#') n++;
		if (n < 1 || n > 6) return 0;
		int after = i + n;
		return after == l.length() || l.charAt(after) == ' ' || l.charAt(after) == '\t' ? n : 0;
	}

	/** thematic break: three or more of the same -, * or _, blanks between allowed, up to 3 spaces before */
	static boolean rule(String l) {
		int i = indent(l), count = 0;
		if (i > 3) return false;
		char c = 0;
		for (; i < l.length(); i++) {
			char ch = l.charAt(i);
			if (ch == ' ' || ch == '\t') continue;
			if (ch != '-' && ch != '*' && ch != '_' || c != 0 && ch != c) return false;
			c = ch;
			count++;
		}
		return count >= 3;
	}

	/** ``` or ~~~ (3 or more) after up to 3 spaces: the fence string, or null */
	private static String fence(String l) {
		int i = indent(l);
		if (i > 3 || i >= l.length() || l.charAt(i) != '`' && l.charAt(i) != '~') return null;
		int n = i;
		while (n < l.length() && l.charAt(n) == l.charAt(i)) n++;
		return n - i >= 3 ? l.substring(i, n) : null;
	}

	/** Lines that are front matter, fenced code (fences included) or an HTML comment block: no headings or links there. */
	static boolean[] code(String[] lines) {
		boolean[] code = new boolean[lines.length];
		int start = frontMatterEnd(lines);
		java.util.Arrays.fill(code, 0, start, true);
		String fence = null;
		boolean comment = false;
		for (int i = start; i < lines.length; i++) {
			String l = lines[i];
			if (fence != null) { // inside ``` or ~~~ until a fence of the same kind, at least as long, nothing after it
				String f = fence(l);
				if (f != null && f.charAt(0) == fence.charAt(0) && f.length() >= fence.length() && l.substring(indent(l) + f.length()).isBlank()) fence = null;
				code[i] = true;
				continue;
			}
			if (comment) { // HTML comment block (as markdown-it): up to the line with -->
				code[i] = true;
				comment = !l.contains("-->");
				continue;
			}
			if (indent(l) <= 3 && l.startsWith("<!--", indent(l))) {
				code[i] = true;
				comment = !l.substring(indent(l) + 4).contains("-->");
				continue;
			}
			String f = fence(l);
			if (f != null && !(f.charAt(0) == '`' && l.substring(indent(l) + f.length()).contains("`"))) {
				fence = f;
				code[i] = true;
			}
		}
		return code;
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
	public static String slug(String text) {
		return text.strip().toLowerCase(Locale.ROOT).replaceAll("(?U)[^\\p{L}\\p{N}\\s_-]", "").replaceAll("(?U)\\s", "-");
	}

	/** Heading text without the commonest inline markup, for labels. */
	static String label(String text) {
		return text.replaceAll("!?\\[([^\\[\\]]*)\\]\\([^()]*\\)", "$1").replaceAll("\\*\\*|__|`", "");
	}

	private Headings() {
	}
}
