package io.github.insjang.mdviewer.editing;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentCommand;
import org.eclipse.jface.text.IAutoEditStrategy;
import org.eclipse.jface.text.IDocument;

/**
 * Pairs the language configuration of the Markdown editor does not close: the backtick of inline code (typing the closing one steps
 * over it, Backspace takes an empty pair away together), and the closing fence of a code block when Enter is pressed after its
 * opening line. Emphasis markers are left alone on purpose: {@code *} and {@code _} are also list bullets, multiplication and part of
 * names, so a pair that closes itself would be wrong too often (Ctrl+B and Ctrl+I mark a selection or word instead).
 */
public class PairAutoEdit implements IAutoEditStrategy {
	private static final Pattern FENCE = Pattern.compile("^([ \\t]*)(`{3,}|~{3,})([^`]*)$");
	private static final String CLOSERS = ")]}>,.;:!?\"'";

	/** The fence delimiter line {@code line} is, or null. A backtick fence takes no backtick in its info text. */
	private static Matcher fence(String line) {
		Matcher m = FENCE.matcher(line);
		return m.matches() ? m : null;
	}

	private static String line(IDocument doc, int line) throws BadLocationException {
		String s = doc.get(doc.getLineOffset(line), doc.getLineLength(line));
		return s.replaceAll("[\\r\\n]+$", ""); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/** Is a fenced block open when line {@code from} starts, going through the lines up to {@code to} (exclusive)? */
	private static boolean insideFence(IDocument doc, int from, int to, boolean inside) throws BadLocationException {
		char open = 0;
		int length = 0;
		boolean in = inside;
		for (int i = from; i < to; i++) {
			Matcher m = fence(line(doc, i));
			if (m == null) continue;
			char c = m.group(2).charAt(0);
			if (!in) {
				in = true;
				open = c;
				length = m.group(2).length();
			} else if (c == open && m.group(2).length() >= length && m.group(3).isBlank()) {
				in = false;
			}
		}
		return in;
	}

	/** The characters of the line before {@code offset}. */
	private static String before(IDocument doc, int offset) throws BadLocationException {
		int start = doc.getLineOffset(doc.getLineOfOffset(offset));
		return doc.get(start, offset - start);
	}

	private static int count(String s, char c) {
		int n = 0;
		for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
		return n;
	}

	private static char charAt(IDocument doc, int offset) throws BadLocationException {
		return offset < 0 || offset >= doc.getLength() ? 0 : doc.getChar(offset);
	}

	private static boolean lineBreak(char c) {
		return c == '\n' || c == '\r' || c == 0;
	}

	/** A backtick typed with nothing selected: step over a closing one, close an open span plainly, or open a pair. */
	private static void backtick(IDocument doc, DocumentCommand c) throws BadLocationException {
		int line = doc.getLineOfOffset(c.offset);
		if (insideFence(doc, 0, line, false)) return; // code is code
		String before = before(doc, c.offset);
		char prev = before.isEmpty() ? 0 : before.charAt(before.length() - 1), next = charAt(doc, c.offset);
		boolean open = count(before, '`') % 2 == 1; // inside a span that is not closed yet
		if (next == '`' && open) { // the closing backtick is already there
			c.text = ""; //$NON-NLS-1$
			c.shiftsCaret = false;
			c.caretOffset = c.offset + 1;
			return;
		}
		if (open || next == '`' || prev == '`' || Character.isLetterOrDigit(prev)) return; // closes a span, or makes ``, ``` or sits in a word
		if (!(lineBreak(next) || Character.isWhitespace(next) || CLOSERS.indexOf(next) >= 0)) return; // in front of text: not a pair
		c.text = "``"; //$NON-NLS-1$
		c.shiftsCaret = false;
		c.caretOffset = c.offset + 1;
	}

	/** Backspace in an empty pair {@code `|`}: both go. */
	private static void backspace(IDocument doc, DocumentCommand c) throws BadLocationException {
		if (charAt(doc, c.offset) != '`' || charAt(doc, c.offset + 1) != '`') return;
		if (count(before(doc, c.offset), '`') % 2 != 0) return; // the first one is a closing backtick
		if (insideFence(doc, 0, doc.getLineOfOffset(c.offset), false)) return;
		c.length = 2;
	}

	/** Enter at the end of an opening fence line with nothing closing it: the closing line is written too, the caret between. */
	private static void enter(IDocument doc, DocumentCommand c) throws BadLocationException {
		int line = doc.getLineOfOffset(c.offset);
		String text = line(doc, line);
		int start = doc.getLineOffset(line);
		if (c.offset != start + text.length()) return; // only at the end of the line
		Matcher m = fence(text);
		if (m == null || insideFence(doc, 0, line, false)) return; // not a fence, or the closing one of a block
		if (insideFence(doc, line + 1, doc.getNumberOfLines(), false)) return; // what follows is not balanced: it closes this one
		String indent = m.group(1), delimiter = c.text;
		String closing = indent + m.group(2);
		// the whole line is replaced: a command that only appends is overwritten by the editor's own handling of Enter
		c.offset = start;
		c.length = text.length();
		c.text = text + delimiter + indent + delimiter + closing;
		c.shiftsCaret = false;
		c.caretOffset = start + text.length() + delimiter.length() + indent.length();
	}

	@Override
	public void customizeDocumentCommand(IDocument doc, DocumentCommand c) {
		if (!c.doit || c.text == null || c.getCommandCount() > 1) return;
		try {
			if (c.length == 0 && "`".equals(c.text)) backtick(doc, c); //$NON-NLS-1$
			else if (c.length == 1 && c.text.isEmpty()) backspace(doc, c);
			else if (c.length == 0 && Arrays.asList(doc.getLegalLineDelimiters()).contains(c.text)) enter(doc, c);
		} catch (BadLocationException e) {
			// leave the key as it is
		}
	}
}
