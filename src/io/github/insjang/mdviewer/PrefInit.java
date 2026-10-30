package io.github.insjang.mdviewer;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.FontData;

public class PrefInit extends AbstractPreferenceInitializer {
	static final String BODY_FONT = "bodyFont", CODE_FONT = "codeFont", LINE_HEIGHT = "lineHeight", LETTER_SPACING = "letterSpacing", THEME = "theme",
			OPEN_PREVIEW = "openPreview", LINK_CHECK = "linkCheck", BREAKS = "breaks", TAB_CLOSE = "tabClose", BOOKMARKS = "bookmarks", PLANTUML = "plantuml", BACKLINKS = "backlinks",
			DIAGRAM_NODE_SPACING = "diagramNodeSpacing", DIAGRAM_RANK_SPACING = "diagramRankSpacing", DIAGRAM_PADDING = "diagramPadding";

	@Override
	public void initializeDefaultPreferences() {
		IPreferenceStore s = Activator.prefs();
		// Eclipse's own dialog and text fonts (Segoe UI and Consolas on Windows), so the defaults exist everywhere
		PreferenceConverter.setDefault(s, BODY_FONT, new FontData(JFaceResources.getFontRegistry().getFontData(JFaceResources.DIALOG_FONT)[0].getName(), 11, SWT.NORMAL));
		PreferenceConverter.setDefault(s, CODE_FONT, new FontData(JFaceResources.getFontRegistry().getFontData(JFaceResources.TEXT_FONT)[0].getName(), 10, SWT.NORMAL));
		s.setDefault(LINE_HEIGHT, "1.6");
		s.setDefault(LETTER_SPACING, "normal");
		s.setDefault(THEME, ThemeMode.AUTO); // as the Eclipse theme
		s.setDefault(OPEN_PREVIEW, true);
		s.setDefault(BREAKS, true);
		s.setDefault(TAB_CLOSE, false);
		s.setDefault(BOOKMARKS, false);
		s.setDefault(LINK_CHECK, true);
		s.setDefault(PLANTUML, true);
		s.setDefault(BACKLINKS, true);
		Colors.setDefaults(s);
		// tighter than Mermaid's own 50 / 50 / 15
		s.setDefault(DIAGRAM_NODE_SPACING, 25);
		s.setDefault(DIAGRAM_RANK_SPACING, 30);
		s.setDefault(DIAGRAM_PADDING, 6);
	}
}
