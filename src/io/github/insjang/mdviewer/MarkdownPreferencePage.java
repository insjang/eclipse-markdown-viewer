package io.github.insjang.mdviewer;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.ColorFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.FontFieldEditor;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.RadioGroupFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

public class MarkdownPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {
	static final String ID = "io.github.insjang.mdviewer.prefs";

	public MarkdownPreferencePage() {
		super(GRID);
		setPreferenceStore(Activator.prefs());
		setDescription(Messages.prefs_description);
	}

	@Override
	protected void createFieldEditors() {
		addField(new FontFieldEditor(PrefInit.BODY_FONT, Messages.prefs_bodyFont, Messages.prefs_fontSample, getFieldEditorParent()));
		addField(new FontFieldEditor(PrefInit.CODE_FONT, Messages.prefs_codeFont, Messages.prefs_codeSample, getFieldEditorParent()));
		addField(new StringFieldEditor(PrefInit.LINE_HEIGHT, Messages.prefs_lineHeight, getFieldEditorParent()));
		addField(new RadioGroupFieldEditor(PrefInit.THEME, Messages.prefs_theme, 2,
				new String[][] { { Messages.prefs_themeDark, "dark" }, { Messages.prefs_themeLight, "light" } }, getFieldEditorParent(), true));
		addField(new BooleanFieldEditor(PrefInit.BREAKS, Messages.prefs_breaks, getFieldEditorParent()));
		addField(new BooleanFieldEditor(PrefInit.OPEN_PREVIEW, Messages.prefs_openPreview, getFieldEditorParent()));
		Composite row = new Composite(getFieldEditorParent(), SWT.NONE);
		GridDataFactory.fillDefaults().span(2, 1).grab(true, false).applyTo(row);
		GridLayoutFactory.fillDefaults().numColumns(2).equalWidth(true).applyTo(row);
		colorGroup(row, "dark", Messages.prefs_darkColors);
		colorGroup(row, "light", Messages.prefs_lightColors);
		Group g = new Group(getFieldEditorParent(), SWT.NONE);
		g.setText(Messages.prefs_spacing);
		GridDataFactory.fillDefaults().span(2, 1).grab(true, false).applyTo(g);
		spacing(g, PrefInit.DIAGRAM_NODE_SPACING, Messages.prefs_nodeSpacing);
		spacing(g, PrefInit.DIAGRAM_RANK_SPACING, Messages.prefs_rankSpacing);
		spacing(g, PrefInit.DIAGRAM_PADDING, Messages.prefs_padding);
		GridLayoutFactory.swtDefaults().numColumns(2).applyTo(g);
	}

	private void spacing(Group g, String key, String label) {
		IntegerFieldEditor f = new IntegerFieldEditor(key, label, g, 3);
		f.setValidRange(0, 200);
		addField(f);
	}

	/** Defaults are GitHub colors; "Restore Defaults" brings them back. */
	private void colorGroup(Composite parent, String theme, String title) {
		Group g = new Group(parent, SWT.NONE);
		g.setText(title);
		GridDataFactory.fillDefaults().grab(true, false).applyTo(g);
		for (String[] i : Colors.ITEMS) addField(new ColorFieldEditor(Colors.key(theme, i[0]), i[1] + ":", g));
		GridLayoutFactory.swtDefaults().numColumns(2).applyTo(g);
	}

	@Override
	public void init(IWorkbench workbench) {
	}
}
