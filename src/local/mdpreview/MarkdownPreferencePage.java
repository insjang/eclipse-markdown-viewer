package local.mdpreview;

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
	static final String ID = "local.mdpreview.prefs";

	public MarkdownPreferencePage() {
		super(GRID);
		setPreferenceStore(Activator.prefs());
		setDescription("Markdown Viewer Preview 탭의 표시 설정 (PDF 내보내기에도 같은 글꼴 사용)");
	}

	@Override
	protected void createFieldEditors() {
		addField(new FontFieldEditor(PrefInit.BODY_FONT, "본문 글꼴:", "가나다 ABC abc 123", getFieldEditorParent()));
		addField(new FontFieldEditor(PrefInit.CODE_FONT, "코드 글꼴:", "code() 0O 1lI", getFieldEditorParent()));
		addField(new StringFieldEditor(PrefInit.LINE_HEIGHT, "줄 간격 (배수):", getFieldEditorParent()));
		addField(new RadioGroupFieldEditor(PrefInit.THEME, "테마", 2,
				new String[][] { { "어둡게", "dark" }, { "밝게", "light" } }, getFieldEditorParent(), true));
		addField(new BooleanFieldEditor(PrefInit.BREAKS, "원문 줄바꿈을 그대로 표시 (끄면 표준 Markdown처럼 한 문단으로 합침)", getFieldEditorParent()));
		addField(new BooleanFieldEditor(PrefInit.OPEN_PREVIEW, "파일을 열 때 Preview 탭으로 시작", getFieldEditorParent()));
		Composite row = new Composite(getFieldEditorParent(), SWT.NONE);
		GridDataFactory.fillDefaults().span(2, 1).grab(true, false).applyTo(row);
		GridLayoutFactory.fillDefaults().numColumns(2).equalWidth(true).applyTo(row);
		colorGroup(row, "dark", "다크 테마 색");
		colorGroup(row, "light", "라이트 테마 색 (PDF에도 사용)");
		Group g = new Group(getFieldEditorParent(), SWT.NONE);
		g.setText("Mermaid 다이어그램 간격 (px, 블록 안 %%{init}%%가 우선)");
		GridDataFactory.fillDefaults().span(2, 1).grab(true, false).applyTo(g);
		spacing(g, PrefInit.DIAGRAM_NODE_SPACING, "노드 사이 간격 (Mermaid 기본 50):");
		spacing(g, PrefInit.DIAGRAM_RANK_SPACING, "단계 사이 간격 (Mermaid 기본 50):");
		spacing(g, PrefInit.DIAGRAM_PADDING, "상자 안 여백 (Mermaid 기본 15):");
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
