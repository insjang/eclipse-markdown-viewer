package io.github.insjang.mdviewer.editing;

import java.util.function.IntFunction;

/**
 * Fenced code blocks, read the same way by every typing aid (lists, tables, pairs, links): a block opens with three or more
 * backticks or tildes (a backtick fence takes no backtick in its info text) and closes with the same mark, at least as many of
 * them, and nothing after them. Lists, tables and the rest are just text inside one.
 */
final class Fences {
	/** A fence line: the blanks in front, the mark, how many of it, and the info text after it. */
	record Fence(String indent, char mark, int length, String info) {
	}

	private Fences() {
	}

	/** The fence a line is, or null. Read by hand, so that a crafted long line cannot make a pattern backtrack. */
	static Fence parse(String line) {
		int i = 0;
		while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) i++;
		if (i >= line.length()) return null;
		char mark = line.charAt(i);
		if (mark != '`' && mark != '~') return null;
		int n = 0;
		while (i + n < line.length() && line.charAt(i + n) == mark) n++;
		if (n < 3) return null;
		String info = line.substring(i + n);
		if (mark == '`' && info.indexOf('`') >= 0) return null;
		return new Fence(line.substring(0, i), mark, n, info);
	}

	/** Is a block open when line {@code to} starts, going through the lines from {@code from} up to it (exclusive), starting open or not? */
	static boolean inside(IntFunction<String> lineAt, int from, int to, boolean open) {
		char mark = 0;
		int length = 0;
		boolean in = open;
		for (int i = from; i < to; i++) {
			Fence f = parse(lineAt.apply(i));
			if (f == null) continue;
			if (!in) {
				in = true;
				mark = f.mark();
				length = f.length();
			} else if (f.mark() == mark && f.length() >= length && f.info().isBlank()) {
				in = false;
			}
		}
		return in;
	}
}
