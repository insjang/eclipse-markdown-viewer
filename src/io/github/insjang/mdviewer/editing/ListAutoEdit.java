package io.github.insjang.mdviewer.editing;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentCommand;
import org.eclipse.jface.text.IAutoEditStrategy;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.TextUtilities;

/**
 * Lists and quotes while typing: Enter continues the item (next number, empty task box, same quote level), Enter on an
 * empty item ends the list (or moves a nested item one level out), and Tab at an item nests it. Attached to the Generic
 * Editor through the autoEditStrategies extension point; it only acts where it is sure, anywhere else the key does what
 * it did before.
 */
public class ListAutoEdit implements IAutoEditStrategy {
	/** indent and quote marks, more indent, then a bullet or a number, the space after it, an optional task box */
	// possessive: two neighbouring [ \t]* made a line of 20000 spaces take seconds to fail
	private static final Pattern ITEM = Pattern.compile("^([ \\t]*+(?:>[ \\t]?+)*+)([ \\t]*+)(?:([-*+])|(\\d{1,9}+)([.)]))([ \\t]++)(\\[[ xX]\\][ \\t]++)?"); //$NON-NLS-1$
	private static final Pattern QUOTE = Pattern.compile("^[ \\t]*(?:>[ \\t]?)+"); //$NON-NLS-1$
	private static final String INDENT = "    "; //$NON-NLS-1$

	@Override
	public void customizeDocumentCommand(IDocument doc, DocumentCommand cmd) {
		if (!cmd.doit || cmd.length != 0 || cmd.text == null) return;
		try {
			if (TextUtilities.endsWith(doc.getLegalLineDelimiters(), cmd.text) != -1 && cmd.text.length() <= 2) enter(doc, cmd);
			else if ("\t".equals(cmd.text)) tab(doc, cmd); //$NON-NLS-1$
		} catch (BadLocationException e) {
			// leave the key as it is
		}
	}

	private void enter(IDocument doc, DocumentCommand cmd) throws BadLocationException {
		int line = doc.getLineOfOffset(cmd.offset);
		IRegion info = doc.getLineInformation(line);
		String text = doc.get(info.getOffset(), info.getLength());
		int caret = cmd.offset - info.getOffset();
		if (DocumentFences.inside(doc, line)) return;
		Matcher m = ITEM.matcher(text);
		if (m.find()) {
			if (caret < m.end()) return; // inside the marker
			String lead = m.group(1) + m.group(2);
			if (text.substring(m.end()).isBlank() && caret == text.length()) {
				endItem(cmd, info, text, lead, m);
				return;
			}
			String marker;
			if (m.group(3) != null) marker = m.group(3);
			else {
				int n = Integer.parseInt(m.group(4));
				int next = constantNumbering(doc, line, lead, n) ? n : n + 1;
				marker = next + m.group(5);
				if (next != n) renumber(doc, cmd, line, lead, next);
			}
			splitLine(cmd, info, text, caret, lead + marker + m.group(6) + (m.group(7) == null ? "" : "[ ] ")); //$NON-NLS-1$ //$NON-NLS-2$
			return;
		}
		Matcher q = QUOTE.matcher(text);
		if (q.find() && caret >= q.end()) {
			if (text.substring(q.end()).isBlank() && caret == text.length()) {
				replaceLine(cmd, info, text.substring(0, q.start()).isBlank() ? "" : text.substring(0, q.start())); //$NON-NLS-1$
				return;
			}
			splitLine(cmd, info, text, caret, q.group());
		}
	}

	/**
	 * Breaks the line at the caret and starts the new line with the prefix. The line is replaced as a whole: the
	 * language configuration of TM4E has its own idea of what Enter does, and it leaves a command alone that already
	 * replaces something.
	 */
	private static void splitLine(DocumentCommand cmd, IRegion info, String text, int caret, String prefix) {
		String head = text.substring(0, caret) + cmd.text + prefix;
		replaceLine(cmd, info, head + text.substring(caret));
		cmd.caretOffset = info.getOffset() + head.length();
	}

	/** Enter on an empty item: a nested one moves one level out, a top level one is removed. */
	private static void endItem(DocumentCommand cmd, IRegion info, String text, String lead, Matcher m) {
		int quoteEnd = QUOTE.matcher(text).find() ? quoteEnd(text) : 0;
		String indent = text.substring(quoteEnd, quoteEnd + leadingWhitespace(text.substring(quoteEnd)));
		if (!indent.isEmpty()) {
			String out = indent.startsWith("\t") ? text.substring(0, quoteEnd) + text.substring(quoteEnd + 1) //$NON-NLS-1$
					: text.substring(0, quoteEnd) + text.substring(quoteEnd + Math.min(INDENT.length(), indent.length()));
			replaceLine(cmd, info, out);
		} else {
			replaceLine(cmd, info, text.substring(0, quoteEnd));
		}
	}

	private static int quoteEnd(String text) {
		Matcher q = QUOTE.matcher(text);
		return q.find() ? q.end() : 0;
	}

	private static int leadingWhitespace(String s) {
		int i = 0;
		while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t')) i++;
		return i;
	}

	private static void replaceLine(DocumentCommand cmd, IRegion info, String with) {
		cmd.offset = info.getOffset();
		cmd.length = info.getLength();
		cmd.text = with;
		cmd.caretOffset = info.getOffset() + with.length();
		cmd.shiftsCaret = false;
	}

	/** A list written as 1. 1. 1. keeps its number instead of counting up. */
	private boolean constantNumbering(IDocument doc, int line, String lead, int n) throws BadLocationException {
		if (line + 1 >= doc.getNumberOfLines()) return false;
		IRegion next = doc.getLineInformation(line + 1);
		Matcher m = ITEM.matcher(doc.get(next.getOffset(), next.getLength()));
		return m.find() && m.group(4) != null && (m.group(1) + m.group(2)).equals(lead) && Integer.parseInt(m.group(4)) == n;
	}

	/** The items after the new one count on from it, as far as they are numbered wrongly. */
	private void renumber(IDocument doc, DocumentCommand cmd, int line, String lead, int newNumber) throws BadLocationException {
		int expected = newNumber;
		for (int l = line + 1; l < doc.getNumberOfLines(); l++) {
			IRegion info = doc.getLineInformation(l);
			String text = doc.get(info.getOffset(), info.getLength());
			if (text.isBlank()) return;
			Matcher m = ITEM.matcher(text);
			if (m.find() && (m.group(1) + m.group(2)).equals(lead) && m.group(4) != null) {
				expected++;
				if (String.valueOf(expected).equals(m.group(4))) return; // from here on it is right already
				cmd.addCommand(info.getOffset() + m.start(4), m.group(4).length(), String.valueOf(expected), null);
			} else if (leadingWhitespace(text) <= lead.length()) {
				return; // something that is not part of the list
			}
		}
	}

	/** Tab in a list item nests the item instead of inserting a tab into its text. */
	private void tab(IDocument doc, DocumentCommand cmd) throws BadLocationException {
		int line = doc.getLineOfOffset(cmd.offset);
		IRegion info = doc.getLineInformation(line);
		if (DocumentFences.inside(doc, line) || !ITEM.matcher(doc.get(info.getOffset(), info.getLength())).find()) return;
		int caret = cmd.offset;
		cmd.offset = info.getOffset();
		cmd.text = INDENT;
		cmd.caretOffset = caret + INDENT.length();
		cmd.shiftsCaret = false;
	}
}
