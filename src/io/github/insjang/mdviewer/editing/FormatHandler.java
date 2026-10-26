package io.github.insjang.mdviewer.editing;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITextViewerExtension6;
import org.eclipse.jface.text.IUndoManager;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;

/** Ctrl+B (bold), Ctrl+I (italic) and Ctrl+K (link) in the Source of the Markdown editor. */
public class FormatHandler extends AbstractHandler {
	/** Active while a Markdown editor is: more specific than the text editor scope, so these keys win over Find Next (Ctrl+K) and the like. */
	public static final String CONTEXT = "io.github.insjang.mdviewer.markdownEditing"; //$NON-NLS-1$

	private static String clipboardText(Display display) {
		Clipboard clipboard = new Clipboard(display);
		try {
			Object text = clipboard.getContents(TextTransfer.getInstance());
			return text instanceof String s ? s : null;
		} finally {
			clipboard.dispose();
		}
	}

	private static MarkdownFormat.Result compute(String id, String text, int start, int end, Display display) {
		if (id.endsWith(".bold")) return MarkdownFormat.toggle(text, start, end, '*', 2); //$NON-NLS-1$
		if (id.endsWith(".italic")) return MarkdownFormat.toggle(text, start, end, '*', 1); //$NON-NLS-1$
		return MarkdownFormat.link(text, start, end, clipboardText(display));
	}

	/** The change as one step for Undo; later edits first so that the offsets stay valid. */
	private static void apply(ITextViewer viewer, IDocument doc, MarkdownFormat.Result result) throws BadLocationException {
		IUndoManager undo = viewer instanceof ITextViewerExtension6 v ? v.getUndoManager() : null;
		if (undo != null) undo.beginCompoundChange();
		try {
			for (int i = result.edits().size() - 1; i >= 0; i--) {
				MarkdownFormat.Edit e = result.edits().get(i);
				doc.replace(e.offset(), e.length(), e.text());
			}
		} finally {
			if (undo != null) undo.endCompoundChange();
		}
		viewer.setSelectedRange(result.selectionStart(), result.selectionEnd() - result.selectionStart());
		viewer.revealRange(result.selectionStart(), result.selectionEnd() - result.selectionStart());
	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IEditorPart editor = HandlerUtil.getActiveEditor(event);
		ITextViewer viewer = editor == null ? null : editor.getAdapter(ITextViewer.class);
		if (viewer == null || viewer.getTextWidget() == null || !viewer.getTextWidget().isFocusControl()) return null; // Preview showing: not for it
		IDocument doc = viewer.getDocument();
		org.eclipse.swt.graphics.Point selection = viewer.getSelectedRange();
		MarkdownFormat.Result result = compute(event.getCommand().getId(), doc.get(), selection.x, selection.x + selection.y, viewer.getTextWidget().getDisplay());
		if (result == null) return null;
		try {
			apply(viewer, doc, result);
		} catch (BadLocationException e) {
			throw new ExecutionException("Markdown formatting", e); //$NON-NLS-1$
		}
		return null;
	}
}
