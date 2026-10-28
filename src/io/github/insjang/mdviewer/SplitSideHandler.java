package io.github.insjang.mdviewer;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;

/** The toolbar button "Side by side": Source and Preview of the active Markdown editor next to each other, or back to one editor. */
public class SplitSideHandler extends AbstractHandler {
	@Override
	public Object execute(ExecutionEvent event) {
		IEditorPart editor = HandlerUtil.getActiveEditor(event);
		if (editor instanceof MarkdownEditor markdown) markdown.toggleSplit();
		return null;
	}
}
