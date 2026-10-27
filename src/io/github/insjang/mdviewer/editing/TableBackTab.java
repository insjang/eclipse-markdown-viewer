package io.github.insjang.mdviewer.editing;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITextViewerExtension;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.VerifyKeyListener;
import org.eclipse.swt.events.VerifyEvent;

/**
 * Shift+Tab in a table cell of the Source: to the previous cell. The viewer handles this key itself (Shift Left, there is no key
 * binding for it), before auto edit strategies see anything, so this is a key filter of the viewer; anywhere else it lets the key through.
 */
public class TableBackTab implements VerifyKeyListener {
	private final ITextViewer viewer;

	private TableBackTab(ITextViewer viewer) {
		this.viewer = viewer;
	}

	public static void install(ITextViewer viewer) {
		if (viewer instanceof ITextViewerExtension extension) extension.prependVerifyKeyListener(new TableBackTab(viewer));
	}

	@Override
	public void verifyKey(VerifyEvent e) {
		if (!e.doit || e.keyCode != SWT.TAB || (e.stateMask & (SWT.SHIFT | SWT.CTRL | SWT.ALT | SWT.COMMAND)) != SWT.SHIFT) return;
		try {
			if (viewer.isEditable() && TableAutoEdit.previousCell(viewer.getDocument(), viewer)) e.doit = false;
		} catch (BadLocationException x) {
			// leave the key as it is
		}
	}
}
