package io.github.insjang.mdviewer;

import org.eclipse.jface.action.Action;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.editors.text.TextEditorActionContributor;
import org.eclipse.ui.part.MultiPageEditorActionBarContributor;

/** Routes undo/find/etc. to the Source page's text editor; on the Preview tab, Find opens the page's find bar. */
public class Contributor extends MultiPageEditorActionBarContributor {
	private final TextEditorActionContributor text = new TextEditorActionContributor();
	private MarkdownEditor editor;
	private final Action findInPreview = new Action() {
		@Override
		public void run() {
			if (editor != null) editor.findInPreview();
		}
	};

	@Override
	public void init(IActionBars bars, IWorkbenchPage page) {
		super.init(bars, page);
		text.init(bars, page);
	}

	@Override
	public void setActiveEditor(IEditorPart part) {
		if (part instanceof MarkdownEditor m) editor = m;
		super.setActiveEditor(part);
	}

	@Override
	public void setActivePage(IEditorPart part) {
		text.setActiveEditor(part); // Source: its own Find/Replace
		if (part == null) getActionBars().setGlobalActionHandler(ActionFactory.FIND.getId(), findInPreview);
		getActionBars().updateActionBars();
	}

	@Override
	public void dispose() {
		text.dispose();
		super.dispose();
	}
}
