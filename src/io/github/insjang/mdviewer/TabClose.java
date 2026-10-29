package io.github.insjang.mdviewer;

import java.util.List;

import org.eclipse.e4.ui.model.application.ui.basic.MCompositePart;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.workbench.modeling.EModelService;
import org.eclipse.e4.ui.workbench.modeling.EPartService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.PlatformUI;

/**
 * Optional (off by default): a double click on any editor tab closes the editor instead of maximizing its stack
 * (Ctrl+M still maximizes). A dirty editor asks to save first, like its close button.
 */
public class TabClose implements IStartup {

	@Override
	public void earlyStartup() {
		Display display = PlatformUI.getWorkbench().getDisplay();
		display.asyncExec(() -> display.addFilter(SWT.MouseDoubleClick, TabClose::doubleClick));
	}

	private static void doubleClick(Event event) {
		if (event.button != 1 || !(event.widget instanceof CTabFolder folder) || !Activator.prefs().getBoolean(PrefInit.TAB_CLOSE)) return;
		CTabItem item = folder.getItem(new Point(event.x, event.y));
		// E4 keeps the part of a stack tab under "modelElement"; only editors, view tabs keep maximizing
		if (item == null || !(item.getData("modelElement") instanceof MPart part) || editors(part).isEmpty()) return; //$NON-NLS-1$
		event.type = SWT.None; // swallow the event: the stack must not maximize
		folder.getDisplay().asyncExec(() -> close(part));
	}

	/** The editor of a tab; a split editor (Ctrl+_) is one tab holding the editor and its clone. */
	private static List<MPart> editors(MPart part) {
		if (part instanceof MCompositePart split) {
			// the editor and its clone sit in a sash inside the split
			return PlatformUI.getWorkbench().getService(EModelService.class).findElements(split, null, MPart.class, List.of("Editor")); //$NON-NLS-1$
		}
		return part.getTags().contains("Editor") ? List.of(part) : List.of(); //$NON-NLS-1$
	}

	private static void close(MPart part) {
		List<MPart> editors = editors(part);
		MPart owner = part.getContext() != null ? part : editors.get(0); // a split may have no context of its own
		EPartService service = owner.getContext() != null ? owner.getContext().get(EPartService.class) : null;
		if (service == null) return;
		for (MPart editor : editors) {
			if (!service.savePart(editor, true)) return;
		}
		service.hidePart(part);
	}
}
