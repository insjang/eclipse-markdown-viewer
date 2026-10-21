package io.github.insjang.mdviewer.editing;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.insjang.mdviewer.Headings;

/** What the link features need to know about Markdown text: where links are, the headings (anchors) and the reference definitions. */
final class MarkdownLinks {
	/** A heading with the anchor the preview gives it (same rule as the heading ids in web/preview.js). */
	record Heading(int line, String text, String anchor) {}

	/** A reference definition {@code [label]: destination}. */
	record Definition(int line, String destination) {}

	/** A link in one line: the whole construct (start inclusive, end exclusive) and where it goes. A reference link has the label as its target until resolved. */
	record Link(int start, int end, String target, boolean reference) {}

	/** The structure of a document: headings, definitions and which lines are fenced code. */
	static final class Outline {
		final List<Heading> headings = new ArrayList<>();
		final Map<String, Definition> definitions = new HashMap<>();
		private final BitSet code = new BitSet();
		private final BitSet opening = new BitSet();

		/** The line is part of a fenced code block, delimiters included. */
		boolean isCode(int line) {
			return code.get(line);
		}

		/** The line is the opening delimiter of a fenced code block (the one that takes a language). */
		boolean isOpeningFence(int line) {
			return opening.get(line);
		}

		Heading heading(String anchor) {
			for (Heading h : headings) if (h.anchor().equals(anchor)) return h;
			return null;
		}

		Definition definition(String label) {
			return definitions.get(normalize(label));
		}
	}

	/**
	 * Lines longer than this are not searched for links or offered proposals: the patterns below start at every bracket, so a crafted
	 * line of brackets could keep the UI thread busy. Their loops are written unrolled with possessive quantifiers because a repeated
	 * (a|b) takes a stack frame per character.
	 */
	static final int MAX_LINE = 4000;

	private static final Pattern DEFINITION = Pattern.compile("^ {0,3}\\[([^\\]]+)\\]:[ \\t]*(<[^>]*>|\\S+)");

	private static final Pattern INLINE = Pattern.compile(
			"!?\\[[^\\]\\\\]*+(?:\\\\.[^\\]\\\\]*+)*+\\]\\(\\s*+(<[^>]*+>|[^\\s()]*+(?:\\([^\\s()]*+\\)[^\\s()]*+)*+)(?:\\s++(?:\"[^\"]*+\"|'[^']*+'))?\\s*+\\)");
	private static final Pattern REFERENCE = Pattern.compile("!?\\[([^\\]\\\\]*+(?:\\\\.[^\\]\\\\]*+)*+)\\]\\[([^\\]]*+)\\]");
	private static final Pattern SHORTCUT = Pattern.compile("(?<![\\]\\\\])\\[([^\\[\\]]+)\\](?![(\\[:])");
	private static final Pattern AUTOLINK = Pattern.compile("<((?:https?|mailto):[^>\\s]+)>");
	private static final Pattern BARE_URL = Pattern.compile("\\bhttps?://[^\\s<>\\])]+");

	private static String normalize(String label) {
		return label.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	/** A destination as written in the link: without the angle brackets, backslash escapes undone. */
	static String clean(String destination) {
		String d = destination.startsWith("<") && destination.endsWith(">") ? destination.substring(1, destination.length() - 1) : destination;
		return d.replaceAll("\\\\([\\\\!-/:-@\\[-`{-~])", "$1");
	}

	/** An opening or closing fence line: up to 3 spaces, then 3 or more backticks or tildes, then the info text. */
	private record Fence(char mark, int length, String info) {}

	private static Fence fence(String line) {
		int i = 0;
		while (i < line.length() && i < 4 && line.charAt(i) == ' ') i++;
		if (i > 3 || i >= line.length()) return null;
		char c = line.charAt(i);
		if (c != '`' && c != '~') return null;
		int n = 0;
		while (i + n < line.length() && line.charAt(i + n) == c) n++;
		return n < 3 ? null : new Fence(c, n, line.substring(i + n));
	}

	/** The headings come from {@link Headings}, the same code the Outline view and the link check use, so the anchors are the preview's. */
	static Outline outline(String text) {
		Outline o = new Outline();
		String[] lines = text.split("\\r\\n|\\r|\\n", -1);
		BitSet headingLine = new BitSet();
		for (Headings.Heading h : Headings.of(text)) {
			o.headings.add(new Heading(h.line(), h.text().trim(), h.id()));
			headingLine.set(h.line());
		}
		char fenceChar = 0;
		int fenceLength = 0;
		for (int i = 0; i < lines.length; i++) {
			String s = lines[i];
			Fence f = fence(s);
			if (fenceChar != 0) {
				o.code.set(i);
				if (f != null && f.mark() == fenceChar && f.length() >= fenceLength && f.info().isBlank()) fenceChar = 0;
				continue;
			}
			if (f != null && !(f.mark() == '`' && f.info().indexOf('`') >= 0)) {
				fenceChar = f.mark();
				fenceLength = f.length();
				o.code.set(i);
				o.opening.set(i);
				continue;
			}
			if (headingLine.get(i)) continue;
			Matcher d = DEFINITION.matcher(s);
			if (d.find()) o.definitions.putIfAbsent(normalize(d.group(1)), new Definition(i, clean(d.group(2))));
		}
		return o;
	}

	/** Whether the index lies in an inline code span: a run of n backticks up to the next run of exactly n. */
	private static boolean inCodeSpan(String line, int index) {
		int i = 0;
		while (i < line.length()) {
			if (line.charAt(i) != '`') {
				i++;
				continue;
			}
			int n = run(line, i), j = i + n, close = -1;
			while (j < line.length()) {
				if (line.charAt(j) != '`') {
					j++;
					continue;
				}
				int m = run(line, j);
				if (m == n) {
					close = j + m;
					break;
				}
				j += m;
			}
			if (close < 0) {
				i += n;
				continue;
			}
			if (index >= i && index < close) return true;
			i = close;
		}
		return false;
	}

	private static int run(String line, int from) {
		int n = 0;
		while (from + n < line.length() && line.charAt(from + n) == '`') n++;
		return n;
	}

	private static Link match(Matcher m, String line, int col, int group, boolean reference) {
		while (m.find()) {
			if (col < m.start() || col >= m.end() || inCodeSpan(line, m.start())) continue;
			return new Link(m.start(), m.end(), reference ? referenceLabel(m) : clean(m.group(group)), reference);
		}
		return null;
	}

	/** The label of a reference link: the second bracket, or the text itself when it is empty ({@code [text][]}) or there is none ({@code [text]}). */
	private static String referenceLabel(Matcher m) {
		String label = m.groupCount() >= 2 ? m.group(2) : m.group(1);
		return label.isBlank() ? m.group(1) : label;
	}

	/** The link of the line which covers the column; the line must not be fenced code. Reference links are only found when the label is defined. */
	static Link at(String line, int col, Outline outline) {
		if (line.length() > MAX_LINE) return null;
		Matcher d = DEFINITION.matcher(line);
		if (d.find() && col >= d.start(2) && col < d.end(2)) return new Link(d.start(2), d.end(2), clean(d.group(2)), false);
		Link l = match(INLINE.matcher(line), line, col, 1, false);
		if (l != null) return l;
		l = match(REFERENCE.matcher(line), line, col, 2, true);
		if (l == null) l = match(SHORTCUT.matcher(line), line, col, 1, true);
		if (l != null) return outline.definition(l.target()) == null ? null : l;
		l = match(AUTOLINK.matcher(line), line, col, 1, false);
		if (l != null) return l;
		Matcher b = BARE_URL.matcher(line);
		while (b.find()) {
			int end = b.end();
			while (end > b.start() && ".,;:!?".indexOf(line.charAt(end - 1)) >= 0) end--;
			if (col >= b.start() && col < end && !inCodeSpan(line, b.start())) return new Link(b.start(), end, line.substring(b.start(), end), false);
		}
		return null;
	}
}
