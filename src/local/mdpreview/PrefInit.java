package local.mdpreview;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.FontData;

public class PrefInit extends AbstractPreferenceInitializer {
	static final String BODY_FONT = "bodyFont", CODE_FONT = "codeFont", LINE_HEIGHT = "lineHeight", THEME = "theme",
			OPEN_PREVIEW = "openPreview", BREAKS = "breaks";

	@Override
	public void initializeDefaultPreferences() {
		IPreferenceStore s = Activator.prefs();
		PreferenceConverter.setDefault(s, BODY_FONT, new FontData("Malgun Gothic", 11, SWT.NORMAL));
		PreferenceConverter.setDefault(s, CODE_FONT, new FontData("D2Coding ligature", 10, SWT.NORMAL));
		s.setDefault(LINE_HEIGHT, "1.6");
		s.setDefault(THEME, "dark");
		s.setDefault(OPEN_PREVIEW, true);
		s.setDefault(BREAKS, true);
		Colors.setDefaults(s);
	}
}
