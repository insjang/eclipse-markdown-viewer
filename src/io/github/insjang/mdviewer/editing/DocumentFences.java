package io.github.insjang.mdviewer.editing;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;

/** {@link Fences} on the lines of a document. */
final class DocumentFences {
	private DocumentFences() {
	}

	/** Is the start of the line inside a fenced block? Where lists and tables are just text. */
	static boolean inside(IDocument doc, int line) throws BadLocationException {
		return inside(doc, 0, line, false);
	}

	/** Is a block open when line {@code to} starts, going through the lines from {@code from} up to it (exclusive), starting open or not? */
	static boolean inside(IDocument doc, int from, int to, boolean open) throws BadLocationException {
		try {
			return Fences.inside(l -> {
				try {
					IRegion info = doc.getLineInformation(l);
					return doc.get(info.getOffset(), info.getLength());
				} catch (BadLocationException e) {
					throw new IllegalStateException(e);
				}
			}, from, to, open);
		} catch (IllegalStateException e) {
			if (e.getCause() instanceof BadLocationException b) throw b;
			throw e;
		}
	}
}
