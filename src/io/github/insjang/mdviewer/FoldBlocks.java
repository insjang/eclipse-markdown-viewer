package io.github.insjang.mdviewer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** The blocks of a Markdown text that can be folded, by line: heading sections (the headings come from {@link Headings}), fenced code, front matter, HTML comments, tables, quotes and list items. */
final class FoldBlocks {
	/** A block that can be folded: from its first line (which stays visible) to its last, both inclusive. */
	record Fold(int startLine, int endLine) {}

	final List<Fold> folds = new ArrayList<>();

	private static final Pattern SETEXT = Pattern.compile("^ {0,3}(=+|-+)[ \\t]*$");
	private static final Pattern LIST_ITEM = Pattern.compile("^([ \\t]*)(?:[-+*]|\\d+[.)])[ \\t]+\\S.*$");

	// fences and delimiter rows are read by hand, not with regexes (as in Headings): a crafted line (spaces, then U+2028 or
	// U+0085, which Java's . and $ do not take) must not make a pattern backtrack on the UI thread

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

	/** A table delimiter row: cells of -, with optional : at either end, between pipes (the outer ones are optional). */
	private static boolean delimiterRow(String line) {
		String s = line.strip();
		if (s.isEmpty()) return false;
		String[] cells = s.split("\\|", -1);
		int from = s.startsWith("|") ? 1 : 0, to = s.endsWith("|") && cells.length > from ? cells.length - 1 : cells.length;
		if (to <= from) return false;
		for (int c = from; c < to; c++) {
			String cell = cells[c].strip();
			int a = cell.startsWith(":") ? 1 : 0, b = cell.length() - (cell.length() > a && cell.endsWith(":") ? 1 : 0);
			if (b <= a) return false;
			for (int k = a; k < b; k++) if (cell.charAt(k) != '-') return false;
		}
		return true;
	}

	private static boolean blank(String s) {
		return s.isBlank();
	}

	private static int indent(String s) {
		int n = 0;
		for (int i = 0; i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t'); i++) n += s.charAt(i) == '\t' ? 4 : 1;
		return n;
	}

	/** The last line before {@code end} (exclusive) that has text; trailing blank lines are not part of a fold. */
	private static int lastText(String[] lines, int start, int end) {
		int last = end - 1;
		while (last > start && blank(lines[last])) last--;
		return last;
	}

	private void add(int start, int end) {
		if (end > start) folds.add(new Fold(start, end));
	}

	/** Lines of the text that are inside fenced blocks or HTML comments (headings and lists there are not structure). */
	private static boolean[] literal(String[] lines, FoldBlocks s) {
		boolean[] literal = new boolean[lines.length];
		char fence = 0;
		int fenceLength = 0, fenceStart = 0;
		boolean comment = false;
		int commentStart = 0;
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			Fence f = fence(line);
			if (fence != 0) {
				literal[i] = true;
				if (f != null && f.mark() == fence && f.length() >= fenceLength && f.info().isBlank()) {
					s.add(fenceStart, i);
					fence = 0;
				}
				continue;
			}
			if (comment) {
				literal[i] = true;
				if (line.contains("-->")) {
					s.add(commentStart, i);
					comment = false;
				}
				continue;
			}
			if (f != null && !(f.mark() == '`' && f.info().indexOf('`') >= 0)) {
				fence = f.mark();
				fenceLength = f.length();
				fenceStart = i;
				literal[i] = true;
			} else if (line.contains("<!--") && line.indexOf("-->", line.indexOf("<!--")) < 0) {
				comment = true;
				commentStart = i;
				literal[i] = true;
			}
		}
		if (fence != 0) s.add(fenceStart, lastText(lines, fenceStart, lines.length)); // not closed: to the end
		if (comment) s.add(commentStart, lastText(lines, commentStart, lines.length));
		return literal;
	}

	static FoldBlocks parse(String text) {
		FoldBlocks s = new FoldBlocks();
		String[] lines = text.split("\\r\\n|\\r|\\n", -1);
		boolean[] literal = literal(lines, s);
		int firstContent = 0;
		if (lines.length > 2 && lines[0].stripTrailing().equals("---")) { // front matter
			for (int i = 1; i < lines.length; i++) {
				if (lines[i].stripTrailing().equals("---") || lines[i].stripTrailing().equals("...")) {
					s.add(0, i);
					firstContent = i + 1;
					break;
				}
			}
		}
		List<Headings.Heading> headings = Headings.of(text);
		for (int k = 0; k < headings.size(); k++) { // a section runs to the next heading of the same or a higher level
			Headings.Heading h = headings.get(k);
			int end = lines.length;
			for (int j = k + 1; j < headings.size(); j++) {
				if (headings.get(j).level() <= h.level()) {
					end = headings.get(j).line();
					break;
				}
			}
			int heading = h.line();
			if (Headings.atxLevel(lines[h.line()]) == 0) { // setext: the text lines, then the underline
				while (heading + 1 < lines.length && !SETEXT.matcher(lines[heading]).matches()) heading++;
			}
			int last = lastText(lines, h.line(), end);
			if (last > heading) s.add(h.line(), last);
		}
		for (int i = firstContent; i < lines.length; i++) {
			if (literal[i]) continue;
			if (lines[i].indexOf('|') >= 0 && i + 1 < lines.length && !literal[i + 1] && delimiterRow(lines[i + 1])) {
				int end = i + 1;
				while (end + 1 < lines.length && !literal[end + 1] && !blank(lines[end + 1]) && lines[end + 1].indexOf('|') >= 0) end++;
				if (end - i >= 2) s.add(i, end); // header, delimiter and at least one row
				i = end;
			} else if (lines[i].stripLeading().startsWith(">")) {
				int end = i;
				while (end + 1 < lines.length && !literal[end + 1] && lines[end + 1].stripLeading().startsWith(">")) end++;
				if (end - i >= 2) s.add(i, end);
				i = end;
			}
		}
		for (int i = firstContent; i < lines.length; i++) { // list items with something nested or continued under them
			if (literal[i] || !LIST_ITEM.matcher(lines[i]).matches()) continue;
			int own = indent(lines[i]), end = i;
			for (int j = i + 1; j < lines.length && !literal[j]; j++) {
				if (blank(lines[j])) continue;
				if (indent(lines[j]) <= own) break;
				end = j;
			}
			s.add(i, end);
		}
		return s;
	}
}
