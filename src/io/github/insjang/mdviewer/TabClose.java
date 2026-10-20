package io.github.insjang.mdviewer;

import org.eclipse.e4.ui.model.application.ui.basic.MPart;
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
		if (item == null || !(item.getData("modelElement") instanceof MPart part) || !part.getTags().contains("Editor")) return; //$NON-NLS-1$ //$NON-NLS-2$
		event.type = SWT.None; // swallow the event: the stack must not maximize
		folder.getDisplay().asyncExec(() -> close(part));
	}

	private static void close(MPart part) {
		EPartService service = part.getContext() != null ? part.getContext().get(EPartService.class) : null;
		if (service != null && service.savePart(part, true)) service.hidePart(part);
	}
}
