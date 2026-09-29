package io.github.insjang.mdviewer;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.editors.text.TextEditorActionContributor;
import org.eclipse.ui.part.MultiPageEditorActionBarContributor;

/** Routes undo/find/etc. to the Source page's text editor. */
public class Contributor extends MultiPageEditorActionBarContributor {
	private final TextEditorActionContributor text = new TextEditorActionContributor();

	@Override
	public void init(IActionBars bars, IWorkbenchPage page) {
		super.init(bars, page);
		text.init(bars, page);
	}

	@Override
	public void setActivePage(IEditorPart part) {
		text.setActiveEditor(part);
		getActionBars().updateActionBars();
	}

	@Override
	public void dispose() {
		text.dispose();
		super.dispose();
	}
}
