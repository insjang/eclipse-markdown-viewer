package io.github.insjang.mdviewer.editing;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentCommand;
import org.eclipse.jface.text.IAutoEditStrategy;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.swt.graphics.Point;

/**
 * Tables while typing: Tab moves to the next cell (a new row after the last one) and lines up the columns of the whole
 * table; Enter at the end of a row starts a new row, Enter on an empty last row ends the table. Attached to the Generic
 * Editor through the autoEditStrategies extension point; outside a table, and inside fenced code, the keys do what they
 * did before.
 */
public class TableAutoEdit implements IAutoEditStrategy {
	private static final Pattern DELIMITER_CELL = Pattern.compile(":?-+:?"); //$NON-NLS-1$
	private static final Pattern FENCE = Pattern.compile("^[ \\t]{0,3}(`{3,}|~{3,})"); //$NON-NLS-1$

	@Override
	public void customizeDocumentCommand(IDocument doc, DocumentCommand cmd) {
		if (!cmd.doit || cmd.length != 0 || cmd.text == null) return;
		boolean tab = "\t".equals(cmd.text); //$NON-NLS-1$
		boolean enter = !tab && cmd.text.length() <= 2 && TextUtilities.endsWith(doc.getLegalLineDelimiters(), cmd.text) != -1;
		if (!tab && !enter) return;
		try {
			Table t = Table.find(doc, doc.getLineOfOffset(cmd.offset));
			if (t == null) return;
			if (tab) tab(doc, cmd, t);
			else enter(doc, cmd, t);
		} catch (BadLocationException e) {
			// leave the key as it is
		}
	}

	private static void tab(IDocument doc, DocumentCommand cmd, Table t) throws BadLocationException {
		int row = doc.getLineOfOffset(cmd.offset) - t.first;
		int col = t.cellAt(row, cmd.offset - doc.getLineOffset(t.first + row));
		int cols = t.columns();
		int toRow = row, toCol = col + 1;
		if (row == 1 || toCol >= cols) {
			toRow = row + 1;
			toCol = 0;
		}
		if (toRow == 1) toRow = 2; // the delimiter row has no cells to type in
		if (toRow >= t.rows.size()) t.rows.add(emptyRow(cols));
		apply(doc, cmd, t, toRow, toCol);
	}

	/**
	 * Shift+Tab: the table is lined up and the caret goes to the previous cell (the last one of the row above from the first one, the
	 * delimiter row has no cells). In the first cell it stays there. False when the caret is not in a table: the key does what it did.
	 */
	static boolean previousCell(IDocument doc, ITextViewer viewer) throws BadLocationException {
		Point selection = viewer.getSelectedRange();
		if (selection.y != 0) return false;
		int line = doc.getLineOfOffset(selection.x);
		Table t = Table.find(doc, line);
		if (t == null) return false;
		int row = line - t.first;
		int col = t.cellAt(row, selection.x - doc.getLineOffset(line));
		int toRow = row, toCol = col - 1;
		if (toCol < 0) {
			toRow = row - 1;
			toCol = t.columns() - 1;
		}
		if (toRow == 1) toRow = 0; // the delimiter row has no cells to type in
		if (toRow < 0) {
			toRow = 0;
			toCol = 0;
		}
		DocumentCommand cmd = new DocumentCommand() {
		};
		apply(doc, cmd, t, toRow, toCol);
		doc.replace(cmd.offset, cmd.length, cmd.text);
		viewer.setSelectedRange(cmd.caretOffset, 0);
		return true;
	}

	private static void enter(IDocument doc, DocumentCommand cmd, Table t) throws BadLocationException {
		int line = doc.getLineOfOffset(cmd.offset);
		int row = line - t.first;
		IRegion info = doc.getLineInformation(line);
		String text = doc.get(info.getOffset(), info.getLength());
		if (row < 2 || cmd.offset - info.getOffset() < text.stripTrailing().length()) return; // header, delimiter, or not at the end
		if (t.rows.get(row).stream().allMatch(String::isBlank)) {
			if (line != t.last) return;
			// an empty last row: the table ends here
			cmd.offset = info.getOffset();
			cmd.length = info.getLength();
			cmd.text = ""; //$NON-NLS-1$
			cmd.caretOffset = info.getOffset();
			cmd.shiftsCaret = false;
			return;
		}
		t.rows.add(row + 1, emptyRow(t.columns()));
		apply(doc, cmd, t, row + 1, 0);
	}

	private static List<String> emptyRow(int cols) {
		List<String> r = new ArrayList<>();
		for (int i = 0; i < cols; i++) r.add(""); //$NON-NLS-1$
		return r;
	}

	/** Replaces the table with its lined-up form and puts the caret into the given cell. */
	private static void apply(IDocument doc, DocumentCommand cmd, Table t, int row, int col) {
		String delimiter = TextUtilities.getDefaultLineDelimiter(doc);
		StringBuilder out = new StringBuilder();
		int caret = t.start;
		List<String> lines = t.format(delimiter);
		int[][] starts = t.cellStarts;
		for (int r = 0; r < lines.size(); r++) {
			if (r == row) caret = t.start + out.length() + starts[r][col];
			out.append(lines.get(r));
			if (r < lines.size() - 1) out.append(delimiter);
		}
		cmd.offset = t.start;
		cmd.length = t.end - t.start;
		cmd.text = out.toString();
		cmd.caretOffset = caret;
		cmd.shiftsCaret = false;
	}

	/** The rows of a table: a header, a delimiter row, and the body, as the lines of the document hold them. */
	private static final class Table {
		int first, last, start, end;
		String indent;
		final List<List<String>> rows = new ArrayList<>(); // the delimiter row (index 1) is kept as an empty list
		int[] align; // 0 left, 1 right, 2 center
		boolean[] explicitLeft;
		final List<List<int[]>> regions = new ArrayList<>();
		int[][] cellStarts;

		static Table find(IDocument doc, int line) throws BadLocationException {
			if (!isRow(text(doc, line))) return null;
			int n = doc.getNumberOfLines();
			int first = line, last = line;
			while (first > 0 && isRow(text(doc, first - 1))) first--;
			while (last < n - 1 && isRow(text(doc, last + 1))) last++;
			if (last - first < 1 || !isDelimiterRow(text(doc, first + 1)) || insideFence(doc, first)) return null;
			Table t = new Table();
			t.first = first;
			t.last = last;
			t.start = doc.getLineOffset(first);
			IRegion info = doc.getLineInformation(last);
			t.end = info.getOffset() + info.getLength();
			String firstLine = text(doc, first);
			t.indent = firstLine.substring(0, leadingWhitespace(firstLine));
			for (int l = first; l <= last; l++) {
				String s = text(doc, l);
				List<int[]> reg = regions(s);
				t.regions.add(reg);
				List<String> cells = new ArrayList<>();
				if (l != first + 1) for (int[] r : reg) cells.add(s.substring(r[0], r[1]).strip());
				t.rows.add(cells);
			}
			t.readAlignment(t.regions.get(1), text(doc, first + 1));
			return t;
		}

		void readAlignment(List<int[]> reg, String line) {
			int cols = Math.max(columns(), reg.size());
			align = new int[cols];
			explicitLeft = new boolean[cols];
			for (int c = 0; c < reg.size(); c++) {
				String d = line.substring(reg.get(c)[0], reg.get(c)[1]).strip();
				boolean l = d.startsWith(":"), r = d.endsWith(":"); //$NON-NLS-1$ //$NON-NLS-2$
				align[c] = l && r ? 2 : r ? 1 : 0;
				explicitLeft[c] = l && !r;
			}
		}

		int columns() {
			int c = 0;
			for (List<String> r : rows) c = Math.max(c, r.size());
			return Math.max(c, align == null ? 0 : align.length);
		}

		/** The cell of a row that the offset (in the line) is in; before the first pipe the first, after the last the last. */
		int cellAt(int row, int offset) {
			List<int[]> reg = regions.get(row);
			for (int c = 0; c < reg.size(); c++) if (offset <= reg.get(c)[1]) return c;
			return Math.max(0, reg.size() - 1);
		}

		/** The lines of the table with every column as wide (in display cells, see DisplayWidth) as its widest cell; fills cellStarts with where each cell's text begins. */
		List<String> format(String delimiter) {
			int cols = columns();
			int[] width = new int[cols];
			java.util.Arrays.fill(width, 3);
			for (int r = 0; r < rows.size(); r++) {
				if (r == 1) continue;
				List<String> cells = rows.get(r);
				for (int c = 0; c < cells.size(); c++) width[c] = Math.max(width[c], DisplayWidth.of(cells.get(c)));
			}
			if (align == null || align.length < cols) readAlignmentSize(cols);
			List<String> lines = new ArrayList<>();
			cellStarts = new int[rows.size()][cols];
			for (int r = 0; r < rows.size(); r++) {
				StringBuilder sb = new StringBuilder(indent).append('|');
				List<String> cells = rows.get(r);
				for (int c = 0; c < cols; c++) {
					sb.append(' ');
					cellStarts[r][c] = sb.length();
					String content = r == 1 ? delimiterCell(c, width[c]) : pad(c < cells.size() ? cells.get(c) : "", width[c], align[c]); //$NON-NLS-1$
					sb.append(content).append(" |"); //$NON-NLS-1$
				}
				lines.add(sb.toString());
			}
			return lines;
		}

		private void readAlignmentSize(int cols) {
			int[] a = new int[cols];
			boolean[] e = new boolean[cols];
			if (align != null) {
				System.arraycopy(align, 0, a, 0, align.length);
				System.arraycopy(explicitLeft, 0, e, 0, explicitLeft.length);
			}
			align = a;
			explicitLeft = e;
		}

		private String delimiterCell(int c, int width) {
			return switch (align[c]) {
			case 2 -> ":" + "-".repeat(width - 2) + ":"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			case 1 -> "-".repeat(width - 1) + ":"; //$NON-NLS-1$ //$NON-NLS-2$
			default -> explicitLeft[c] ? ":" + "-".repeat(width - 1) : "-".repeat(width); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			};
		}

		private static String pad(String s, int width, int align) {
			int gap = width - DisplayWidth.of(s);
			if (gap <= 0) return s;
			return switch (align) {
			case 1 -> " ".repeat(gap) + s; //$NON-NLS-1$
			case 2 -> " ".repeat(gap / 2) + s + " ".repeat(gap - gap / 2); //$NON-NLS-1$ //$NON-NLS-2$
			default -> s + " ".repeat(gap); //$NON-NLS-1$
			};
		}

		private static String text(IDocument doc, int line) throws BadLocationException {
			IRegion info = doc.getLineInformation(line);
			return doc.get(info.getOffset(), info.getLength());
		}

		private static boolean isRow(String s) {
			return !s.isBlank() && s.indexOf('|') >= 0;
		}

		private static boolean isDelimiterRow(String s) {
			List<int[]> reg = regions(s);
			if (reg.isEmpty()) return false;
			for (int[] r : reg) if (!DELIMITER_CELL.matcher(s.substring(r[0], r[1]).strip()).matches()) return false;
			return true;
		}

		/** The text between the pipes of a row (a pipe after a backslash is part of the text). */
		static List<int[]> regions(String line) {
			List<Integer> pipes = new ArrayList<>();
			for (int i = 0; i < line.length(); i++) {
				char ch = line.charAt(i);
				if (ch == '\\') i++;
				else if (ch == '|') pipes.add(i);
			}
			List<int[]> out = new ArrayList<>();
			if (pipes.isEmpty()) return out;
			boolean leading = line.substring(0, pipes.get(0)).isBlank();
			int start = leading ? pipes.get(0) + 1 : 0;
			for (int i = leading ? 1 : 0; i < pipes.size(); i++) {
				out.add(new int[] { start, pipes.get(i) });
				start = pipes.get(i) + 1;
			}
			if (start < line.length() && !line.substring(start).isBlank()) out.add(new int[] { start, line.length() });
			return out;
		}

		private static int leadingWhitespace(String s) {
			int i = 0;
			while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t')) i++;
			return i;
		}

		/** An odd number of fence lines above: a table-looking block in fenced code is just text. */
		private static boolean insideFence(IDocument doc, int line) throws BadLocationException {
			String open = null;
			for (int l = 0; l < line; l++) {
				Matcher f = FENCE.matcher(text(doc, l));
				if (!f.find()) continue;
				String ch = f.group(1).substring(0, 1);
				if (open == null) open = ch;
				else if (open.equals(ch)) open = null;
			}
			return open != null;
		}
	}
}
