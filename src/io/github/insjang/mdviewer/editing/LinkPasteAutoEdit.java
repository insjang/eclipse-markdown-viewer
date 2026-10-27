package io.github.insjang.mdviewer.editing;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentCommand;
import org.eclipse.jface.text.IAutoEditStrategy;
import org.eclipse.jface.text.IDocument;

/** A web address pasted (or dropped) over selected text turns the selection into a link: {@code [selected text](address)}. */
public class LinkPasteAutoEdit implements IAutoEditStrategy {
	@Override
	public void customizeDocumentCommand(IDocument doc, DocumentCommand command) {
		if (!command.doit || command.length <= 0 || command.text == null || command.getCommandCount() > 1) return;
		String address = command.text.trim();
		if (!MarkdownFormat.URL.matcher(address).matches()) return; // a typed character is never a whole address
		try {
			String selected = doc.get(command.offset, command.length);
			if (selected.isBlank() || selected.contains("\n") || selected.contains("\r") || selected.contains("[") || selected.contains("]") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					|| MarkdownFormat.URL.matcher(selected.trim()).matches() || DocumentFences.inside(doc, doc.getLineOfOffset(command.offset))) return;
			command.text = "[" + selected + "](" + address + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			command.shiftsCaret = false;
			command.caretOffset = command.offset + command.text.length();
		} catch (BadLocationException e) {
			// leave the paste as it is
		}
	}
}
