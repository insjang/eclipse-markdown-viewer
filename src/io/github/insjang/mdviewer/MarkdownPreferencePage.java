package io.github.insjang.mdviewer;

import java.util.ArrayList;
import java.util.List;

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
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
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
		// line height and letter spacing on one line
		Composite text = new Composite(getFieldEditorParent(), SWT.NONE);
		wide.add(text);
		groups.add(text);
		addField(new StringFieldEditor(PrefInit.LINE_HEIGHT, Messages.prefs_lineHeight, 6, text));
		addField(new StringFieldEditor(PrefInit.LETTER_SPACING, Messages.prefs_letterSpacing, 8, text));
		GridLayoutFactory.fillDefaults().numColumns(4).spacing(10, 5).applyTo(text);
		addField(new RadioGroupFieldEditor(PrefInit.THEME, Messages.prefs_theme, 3,
				new String[][] { { Messages.prefs_themeAuto, ThemeMode.AUTO }, { Messages.prefs_themeDark, "dark" }, { Messages.prefs_themeLight, "light" } }, getFieldEditorParent(), true));
		addField(new BooleanFieldEditor(PrefInit.BREAKS, Messages.prefs_breaks, getFieldEditorParent()));
		addField(new BooleanFieldEditor(PrefInit.OPEN_PREVIEW, Messages.prefs_openPreview, getFieldEditorParent()));
		addField(new BooleanFieldEditor(PrefInit.LINK_CHECK, Messages.prefs_linkCheck, getFieldEditorParent()));
		addField(new BooleanFieldEditor(PrefInit.TAB_CLOSE, Messages.prefs_tabClose, getFieldEditorParent()));
		addField(new BooleanFieldEditor(PrefInit.BOOKMARKS, Messages.prefs_bookmarks, getFieldEditorParent()));
		addField(new BooleanFieldEditor(PrefInit.PLANTUML, Messages.prefs_plantuml, getFieldEditorParent()));
		// dark colors, light colors and diagram spacing side by side
		Composite row = new Composite(getFieldEditorParent(), SWT.NONE);
		GridDataFactory.fillDefaults().grab(true, false).applyTo(row);
		wide.add(row);
		GridLayoutFactory.fillDefaults().numColumns(3).applyTo(row);
		colorGroup(row, "dark", Messages.prefs_darkColors);
		colorGroup(row, "light", Messages.prefs_lightColors);
		Group g = group(row, Messages.prefs_spacing);
		GridDataFactory.fillDefaults().grab(true, false).align(SWT.FILL, SWT.BEGINNING).applyTo(g);
		spacing(g, PrefInit.DIAGRAM_NODE_SPACING, Messages.prefs_nodeSpacing);
		spacing(g, PrefInit.DIAGRAM_RANK_SPACING, Messages.prefs_rankSpacing);
		spacing(g, PrefInit.DIAGRAM_PADDING, Messages.prefs_padding);
		note(g, Messages.prefs_spacing_note, 2, 150);
		GridLayoutFactory.swtDefaults().numColumns(2).applyTo(g);
	}

	/** An explanation inside a box; width: where it wraps (SWT.DEFAULT: one line). */
	private static Label note(Composite parent, String text, int span, int width) {
		Label l = new Label(parent, SWT.WRAP);
		l.setText(text);
		l.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_WIDGET_DARK_SHADOW));
		GridDataFactory.fillDefaults().span(span, 1).grab(true, false).hint(width, SWT.DEFAULT).applyTo(l);
		return l;
	}

	/** Composites of fields laid out on their own, not in the page's columns */
	private final List<Composite> groups = new ArrayList<>();
	/** Controls across the whole page: as many columns as the font fields make it have. */
	private final List<Control> wide = new ArrayList<>();

	private Group group(Composite parent, String title) {
		Group g = new Group(parent, SWT.NONE);
		g.setText(title);
		groups.add(g);
		return g;
	}

	/**
	 * The page stretches every field's label over all its columns but one (3 for the font fields), which put each label in a group
	 * on a line of its own: one line a field there, the color button first.
	 */
	@Override
	protected void adjustGridLayout() {
		super.adjustGridLayout();
		for (Control k : wide) data(k).horizontalSpan = ((GridLayout) getFieldEditorParent().getLayout()).numColumns;
		for (Composite g : groups) {
			Control[] c = g.getChildren();
			for (Control k : c) if (!(k instanceof Label l && (l.getStyle() & SWT.WRAP) != 0)) data(k).horizontalSpan = 1;
			if (g.getLayout() instanceof GridLayout l && l.numColumns == 2 && c.length > 0 && c[1] instanceof Button) {
				for (int i = 0; i + 1 < c.length; i += 2) c[i + 1].moveAbove(c[i]); // [color] name
			}
		}
	}

	/** After the fields have loaded: a font field sizes its sample to the font when it loads. */
	@Override
	protected void initialize() {
		super.initialize();
		for (Control k : getFieldEditorParent().getChildren()) {
			if (k instanceof Text t && (t.getStyle() & SWT.READ_ONLY) != 0) {
				GridData d = data(t);
				d.heightHint = SWT.DEFAULT;
				d.verticalAlignment = SWT.CENTER;
				d.widthHint = 60; // takes what the row leaves instead of widening the page
				d.grabExcessHorizontalSpace = true;
				d.horizontalAlignment = SWT.FILL;
			}
		}
	}


	private static GridData data(Control c) {
		if (!(c.getLayoutData() instanceof GridData)) c.setLayoutData(new GridData());
		return (GridData) c.getLayoutData();
	}

	private void spacing(Group g, String key, String label) {
		IntegerFieldEditor f = new IntegerFieldEditor(key, label, g, 3);
		f.setValidRange(0, 200);
		addField(f);
	}

	/** Defaults are GitHub colors; "Restore Defaults" brings them back. */
	private void colorGroup(Composite parent, String theme, String title) {
		Group g = group(parent, title);
		GridDataFactory.fillDefaults().grab(true, false).applyTo(g);
		for (String[] i : Colors.ITEMS) addField(new ColorFieldEditor(Colors.key(theme, i[0]), i[1], g));
		if ("light".equals(theme)) note(g, Messages.prefs_lightColors_note, 2, SWT.DEFAULT);
		GridLayoutFactory.swtDefaults().numColumns(2).applyTo(g);
	}

	@Override
	public void init(IWorkbench workbench) {
	}
}
