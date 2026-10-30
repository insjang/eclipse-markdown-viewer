package io.github.insjang.mdviewer.editing;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The text changes behind Ctrl+B, Ctrl+I and Ctrl+K, as pure functions of the document text and the selection. */
final class MarkdownFormat {
	/** Replace {@code length} characters at {@code offset} by {@code text}. Edits of one result do not overlap and are in document order. */
	record Edit(int offset, int length, String text) {}

	/** What to change and where the selection goes afterwards (positions in the changed text). */
	record Result(List<Edit> edits, int selectionStart, int selectionEnd) {}

	static final Pattern URL = Pattern.compile("(?i)https?://\\S+");
	private static final Pattern LINK = Pattern.compile("\\[[^\\]\\n]*+\\]\\(([^)\\n]*+)\\)");
	private static final int MAX_LINK_LINE = 4000;

	private static boolean isWordChar(char c) {
		return Character.isLetterOrDigit(c);
	}

	private static int lineStart(String text, int offset) {
		return text.lastIndexOf('\n', offset - 1) + 1;
	}

	private static int lineEnd(String text, int offset) {
		int i = text.indexOf('\n', offset);
		return i < 0 ? text.length() : i;
	}

	/** Number of {@code c} in a row starting at {@code from}, going {@code step} (+1 or -1), not past [min, max). */
	private static int run(String text, char c, int from, int step, int min, int max) {
		int n = 0;
		for (int i = from; i >= min && i < max && text.charAt(i) == c; i += step) n++;
		return n;
	}

	/** The word around the caret as {start, end}, or null when the caret is not in or against a word. */
	private static int[] wordAt(String text, int caret) {
		int s = caret, e = caret;
		while (s > 0 && isWordChar(text.charAt(s - 1))) s--;
		while (e < text.length() && isWordChar(text.charAt(e))) e++;
		return s == e ? null : new int[] { s, e };
	}

	/** The marks a line starts with (heading, quote, list item, task) which formatting leaves alone when the whole line is selected. */
	private static final Pattern BLOCK_MARKER = Pattern.compile("(?:(?:#{1,6}|>+|[-+*]|\\d+[.)])[ \\t]+){1,16}(?:\\[[ xX]\\][ \\t]+)?"); // at most 16: a repeated group takes a stack frame per round

	/** The non-blank parts of the lines the range touches, as {start, end} pairs; a line's own block marks are not part of them. */
	private static List<int[]> segments(String text, int start, int end) {
		List<int[]> result = new ArrayList<>();
		int pos = start;
		while (pos <= end) {
			int first = lineStart(text, pos);
			while (first < lineEnd(text, pos) && (text.charAt(first) == ' ' || text.charAt(first) == '\t')) first++;
			int ls = Math.max(lineStart(text, pos), start), le = Math.min(lineEnd(text, pos), end);
			while (ls < le && Character.isWhitespace(text.charAt(ls))) ls++;
			while (le > ls && Character.isWhitespace(text.charAt(le - 1))) le--;
			if (ls == first && le > ls) {
				Matcher m = BLOCK_MARKER.matcher(text.substring(ls, le));
				if (m.lookingAt()) ls += m.end();
			}
			if (le > ls) result.add(new int[] { ls, le });
			pos = lineEnd(text, pos) + 1;
		}
		return result;
	}

	/**
	 * The runs of {@code c} in the segment that carry the marker, as {start, length}: bold (n=2) takes a run of two or more, italic a
	 * run of odd length. They are the marks that are already there.
	 */
	private static List<int[]> marks(String text, int s, int e, char c, int n) {
		List<int[]> result = new ArrayList<>();
		for (int i = s; i < e;) {
			if (text.charAt(i) != c) {
				i++;
				continue;
			}
			int m = run(text, c, i, 1, s, e);
			if (present(m, n)) result.add(new int[] { i, m });
			i += m;
		}
		return result;
	}

	/** The segment without its marks ({@code n} characters off each run of {@link #marks}). */
	private static String without(String text, int s, int e, List<int[]> marks, int n) {
		StringBuilder sb = new StringBuilder();
		int from = s;
		for (int[] m : marks) {
			sb.append(text, from, m[0]);
			from = m[0] + n;
		}
		return sb.append(text, from, e).toString();
	}

	/**
	 * Is the marker ({@code n} times {@code c}) around the segment? 0 = no, 1 = markers inside the segment, 2 = right outside it. Marks
	 * in the middle must pair up: {@code **a** b **c**} is bold as a whole only when the marks at its ends close each other, which an
	 * even number of marks says.
	 */
	private static int wrapped(String text, int s, int e, char c, int n) {
		int lead = run(text, c, s, 1, s, e), trail = run(text, c, e - 1, -1, s, e);
		List<int[]> marks = marks(text, s, e, c, n);
		if (lead + trail < e - s && present(Math.min(lead, trail), n) && marks.size() % 2 == 0) return 1;
		int line0 = lineStart(text, s), line1 = lineEnd(text, e);
		int before = run(text, c, s - 1, -1, line0, s), after = run(text, c, e, 1, e, line1);
		return marks.isEmpty() && present(Math.min(before, after), n) ? 2 : 0;
	}

	/** Is the line of the offset part of a fenced code block, its fences included? */
	private static boolean inFence(String[] lines, String text, int offset) {
		int line = 0;
		for (int i = text.indexOf('\n'); i >= 0 && i < offset; i = text.indexOf('\n', i + 1)) line++;
		return Fences.parse(lines[line]) != null || Fences.inside(l -> lines[l], 0, line, false);
	}

	/** Does the position (between two characters) lie inside an inline code span, not at its backticks' outer edge? */
	private static boolean inCodeSpan(String text, int offset) {
		int ls = lineStart(text, offset), le = lineEnd(text, offset);
		if (le - ls > MarkdownLinks.MAX_LINE || offset <= ls || offset >= le) return false;
		String line = text.substring(ls, le);
		return MarkdownLinks.inCodeSpan(line, offset - ls - 1) && MarkdownLinks.inCodeSpan(line, offset - ls);
	}

	/** An end of the selection (or the caret) in fenced code or inside an inline code span: the formatting keys leave code alone. */
	private static boolean inCode(String text, int start, int end) {
		String[] lines = text.split("\n", -1);
		return inFence(lines, text, start) || inFence(lines, text, end) || inCodeSpan(text, start) || inCodeSpan(text, end);
	}

	/**
	 * The text before the selection, on its line, ends inside the destination of a link: {@code [text](docs/a} or {@code [label]: docs/a}.
	 * A web address pasted over a destination, or Ctrl+K on it, would nest a second link there.
	 */
	static boolean inDestination(String before) {
		int open = before.lastIndexOf("](");
		if (open >= 0 && destinationSoFar(before, open + 2, true)) return true;
		int i = 0; // a reference definition: up to three blanks, [label]:, then the destination
		while (i < before.length() && i < 3 && before.charAt(i) == ' ') i++;
		if (i >= before.length() || before.charAt(i) != '[') return false;
		int close = before.indexOf("]:", i + 1);
		return close >= 0 && before.indexOf(']', i + 1) == close && destinationSoFar(before, close + 2, false);
	}

	/**
	 * The selection lies in the text of a link or image: an open [ before it on its line, and after it a ] (no [ in between) that a ( or
	 * [ follows: {@code [docs/a.md](x)}. A link made of the selection would end up inside that one.
	 */
	static boolean inLinkText(String before, String after) {
		if (before.lastIndexOf('[') <= before.lastIndexOf(']')) return false;
		int close = after.indexOf(']'), open = after.indexOf('[');
		return close >= 0 && (open < 0 || open > close) && close + 1 < after.length() && (after.charAt(close + 1) == '(' || after.charAt(close + 1) == '[');
	}

	/** From {@code from} on the text is blanks, an optional <, and a destination that has not ended yet (one in <> may hold blanks). */
	private static boolean destinationSoFar(String s, int from, boolean inline) {
		int i = from;
		while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
		boolean angle = i < s.length() && s.charAt(i) == '<';
		if (angle) i++;
		for (; i < s.length(); i++) {
			char c = s.charAt(i);
			if (angle ? c == '>' : Character.isWhitespace(c) || c == '>' || inline && c == ')') return false;
		}
		return true;
	}

	/** In a run of m markers around a text: bold (n=2) is there from two on, italic (n=1) when the count is odd (*** is both). */
	private static boolean present(int m, int n) {
		return n == 2 ? m >= 2 : m % 2 == 1;
	}

	/**
	 * Ctrl+B / Ctrl+I: marks the selection (or the word at the caret) with {@code n} times {@code c}, or takes the marks away when they are
	 * already there. Several lines are marked line by line. Without a selection and without a word the empty pair is inserted. Marking
	 * a text that already has marks inside takes those away first (one pair around the whole), and taking the marks away from a text
	 * whose inner marks pair up takes all of them away. In code (a fence, an inline span) nothing changes.
	 */
	static Result toggle(String text, int start, int end, char c, int n) {
		if (inCode(text, start, end)) return null;
		String marker = String.valueOf(c).repeat(n);
		boolean caretOnly = start == end;
		int caret = start;
		if (caretOnly) {
			int[] word = wordAt(text, caret);
			if (word == null) return new Result(List.of(new Edit(caret, 0, marker + marker)), caret + n, caret + n);
			start = word[0];
			end = word[1];
		}
		List<int[]> segs = segments(text, start, end);
		if (segs.isEmpty()) return null;
		boolean unwrap = true;
		for (int[] s : segs) unwrap &= wrapped(text, s[0], s[1], c, n) != 0;
		List<Edit> edits = new ArrayList<>();
		int delta = 0, newStart = 0, newEnd = 0;
		for (int i = 0; i < segs.size(); i++) {
			int s = segs.get(i)[0], e = segs.get(i)[1];
			int kind = wrapped(text, s, e, c, n), contentStart, contentEnd;
			List<int[]> inner = marks(text, s, e, c, n);
			if (!unwrap && kind == 0 && !inner.isEmpty()) { // mark it, the marks in it become one pair around the whole
				String replaced = marker + without(text, s, e, inner, n) + marker;
				edits.add(new Edit(s, e - s, replaced));
				contentStart = s + delta + n;
				contentEnd = s + delta + replaced.length() - n;
				delta += replaced.length() - (e - s);
			} else if (!unwrap && kind == 0) { // mark it
				edits.add(new Edit(s, 0, marker));
				edits.add(new Edit(e, 0, marker));
				contentStart = s + delta + n;
				contentEnd = e + delta + n;
				delta += 2 * n;
			} else if (unwrap && kind == 1 && inner.size() > 2) { // marks inside the segment, and pairs of them in between
				String replaced = without(text, s, e, inner, n);
				edits.add(new Edit(s, e - s, replaced));
				contentStart = s + delta;
				contentEnd = s + delta + replaced.length();
				delta += replaced.length() - (e - s);
			} else if (unwrap && kind == 1) { // marks inside the segment
				edits.add(new Edit(s, n, ""));
				edits.add(new Edit(e - n, n, ""));
				contentStart = s + delta;
				contentEnd = e + delta - 2 * n;
				delta -= 2 * n;
			} else if (unwrap) { // marks right outside it
				edits.add(new Edit(s - n, n, ""));
				edits.add(new Edit(e, n, ""));
				contentStart = s - n + delta;
				contentEnd = e - n + delta;
				delta -= 2 * n;
			} else { // already marked, others are not: leave it
				contentStart = s + delta;
				contentEnd = e + delta;
			}
			if (i == 0) newStart = contentStart;
			newEnd = contentEnd;
		}
		if (caretOnly) { // the caret stays where it was in the word
			int shift = unwrap ? -n : n;
			int moved = Math.max(newStart, Math.min(newEnd, caret + shift));
			return new Result(edits, moved, moved);
		}
		return new Result(edits, newStart, newEnd);
	}

	/**
	 * Ctrl+K: makes the selection (or the word at the caret) the text of a link. A web address in the selection becomes the address
	 * (the caret goes into the empty text), a web address on the clipboard becomes the address of selected text, otherwise the caret
	 * goes into the empty parentheses. In an existing link it selects the address instead. In code, and for a selection over the
	 * text or destination of a link, nothing changes.
	 */
	static Result link(String text, int start, int end, String clipboard) {
		if (inCode(text, start, end) || start != end && (inDestination(text.substring(lineStart(text, start), start))
				|| inLinkText(text.substring(lineStart(text, start), start), text.substring(end, lineEnd(text, end))))) return null; // code, or a link that a link would nest in
		if (start == end) {
			int ls = lineStart(text, start);
			if (lineEnd(text, start) - ls <= MAX_LINK_LINE) { // LINK starts at every [: on a crafted line of brackets that would take long
				Matcher m = LINK.matcher(text.substring(ls, lineEnd(text, start)));
				while (m.find()) {
					if (start >= ls + m.start() && start <= ls + m.end()) return new Result(List.of(), ls + m.start(1), ls + m.end(1));
				}
			}
			int[] word = wordAt(text, start);
			if (word == null) return new Result(List.of(new Edit(start, 0, "[]()")), start + 1, start + 1);
			start = word[0];
			end = word[1];
		}
		String selected = text.substring(start, end);
		if (selected.contains("\n\n") || selected.contains("\r\n\r\n")) return null;
		String trimmed = selected.trim();
		if (URL.matcher(trimmed).matches()) return new Result(List.of(new Edit(start, end - start, "[](" + trimmed + ")")), start + 1, start + 1);
		String clip = clipboard == null ? "" : clipboard.trim();
		if (URL.matcher(clip).matches()) {
			int after = start + selected.length() + 4 + clip.length();
			return new Result(List.of(new Edit(start, end - start, "[" + selected + "](" + clip + ")")), after, after);
		}
		int inside = start + selected.length() + 3;
		return new Result(List.of(new Edit(start, end - start, "[" + selected + "]()")), inside, inside);
	}
}
