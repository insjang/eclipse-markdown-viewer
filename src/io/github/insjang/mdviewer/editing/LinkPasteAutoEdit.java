package io.github.insjang.mdviewer.editing;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentCommand;
import org.eclipse.jface.text.IAutoEditStrategy;
import org.eclipse.jface.text.IDocument;

/** A web address pasted (or dropped) over selected text turns the selection into a link: {@code [selected text](address)}. */
public class LinkPasteAutoEdit implements IAutoEditStrategy {
	private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,}).*$");

	/** Is the offset inside a fenced code block? */
	private static boolean insideFence(IDocument doc, int offset) throws BadLocationException {
		boolean inside = false;
		char open = 0;
		for (int line = 0; line < doc.getLineOfOffset(offset); line++) {
			String s = doc.get(doc.getLineOffset(line), doc.getLineLength(line)).stripTrailing();
			Matcher m = FENCE.matcher(s);
			if (!m.matches()) continue;
			char c = m.group(1).charAt(0);
			if (!inside) {
				inside = true;
				open = c;
			} else if (c == open) {
				inside = false;
			}
		}
		return inside;
	}

	@Override
	public void customizeDocumentCommand(IDocument doc, DocumentCommand command) {
		if (!command.doit || command.length <= 0 || command.text == null || command.getCommandCount() > 1) return;
		String address = command.text.trim();
		if (!MarkdownFormat.URL.matcher(address).matches()) return; // a typed character is never a whole address
		try {
			String selected = doc.get(command.offset, command.length);
			if (selected.isBlank() || selected.contains("\n") || selected.contains("\r") || selected.contains("[") || selected.contains("]") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					|| MarkdownFormat.URL.matcher(selected.trim()).matches() || insideFence(doc, command.offset)) return;
			command.text = "[" + selected + "](" + address + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			command.shiftsCaret = false;
			command.caretOffset = command.offset + command.text.length();
		} catch (BadLocationException e) {
			// leave the paste as it is
		}
	}
}
