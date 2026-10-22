package io.github.insjang.mdviewer;

import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;

/** Dark or light for the Preview: as the preference says, or, on "auto", as the Eclipse theme shows in its editors. */
final class ThemeMode {
	static final String AUTO = "auto"; //$NON-NLS-1$

	private ThemeMode() {
	}

	/** "dark" or "light". {@code reference} is a widget of the editor: its background is what the Eclipse theme made of it. */
	static String resolve(String preference, Control reference) {
		if (!AUTO.equals(preference)) return "light".equals(preference) ? "light" : "dark"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		if (reference == null || reference.isDisposed()) return "light"; //$NON-NLS-1$
		RGB bg = reference.getBackground().getRGB();
		return (bg.red * 299 + bg.green * 587 + bg.blue * 114) / 1000 < 128 ? "dark" : "light"; //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * Calls {@code check} whenever the part is activated, at once and again a little later (the new theme reaches the widgets after
	 * the dialog that switched it has closed): the Eclipse theme is switched while no editor is in front, and there is no event that
	 * reaches an editor reliably. Returns what to call to stop.
	 */
	static Runnable watch(IWorkbenchPart part, Runnable check) {
		IWorkbenchPage page = part.getSite().getPage();
		IPartListener2 listener = new IPartListener2() {
			@Override
			public void partActivated(IWorkbenchPartReference ref) {
				if (ref.getPart(false) != part) return;
				check.run();
				Display.getDefault().timerExec(600, check);
			}
		};
		page.addPartListener(listener);
		return () -> page.removePartListener(listener);
	}
}
