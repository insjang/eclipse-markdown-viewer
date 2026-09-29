package local.mdpreview;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.util.List;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.internal.genericeditor.ExtensionBasedTextEditor;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;

/** Source (generic text editor) + Preview (Edge browser) tabs at the bottom of the same editor area. */
@SuppressWarnings("restriction")
public class MarkdownEditor extends MultiPageEditorPart {
	private static final int SOURCE = 0, PREVIEW = 1;
	private static final String EDGE = "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";

	private ITextEditor source;
	private Browser browser;
	private String pageUrl;
	private boolean loaded, stale = true;
	private final Runnable renderLater = () -> {
		if (getActivePage() == PREVIEW) render();
	};
	private final IDocumentListener docListener = new IDocumentListener() {
		@Override
		public void documentAboutToBeChanged(DocumentEvent e) {
		}

		@Override
		public void documentChanged(DocumentEvent e) {
			stale = true;
			Display.getCurrent().timerExec(300, renderLater); // external change while Preview is showing
		}
	};
	private final IPropertyChangeListener prefListener = e -> applyStyle();

	@Override
	protected void createPages() {
		source = new ExtensionBasedTextEditor();
		try {
			setPageText(addPage(source, getEditorInput()), "Source");
		} catch (PartInitException e) {
			throw new IllegalStateException(e);
		}
		setPartName(getEditorInput().getName());
		document().addDocumentListener(docListener);
		setPageText(addPage(createPreview(getContainer())), "Preview");
		Activator.prefs().addPropertyChangeListener(prefListener);
		if (Activator.prefs().getBoolean(PrefInit.OPEN_PREVIEW)) setActivePage(PREVIEW);
	}

	private Control createPreview(Composite parent) {
		Composite c = new Composite(parent, SWT.NONE);
		GridLayoutFactory.fillDefaults().spacing(0, 0).applyTo(c);
		ToolBar bar = new ToolBar(c, SWT.FLAT | SWT.RIGHT);
		GridDataFactory.fillDefaults().align(SWT.END, SWT.CENTER).applyTo(bar);
		item(bar, "PDF 내보내기", this::exportPdf);
		item(bar, "A-", () -> Activator.zoom(-1));
		item(bar, "A+", () -> Activator.zoom(1));
		item(bar, "글꼴 설정...", () -> PreferencesUtil
				.createPreferenceDialogOn(getSite().getShell(), MarkdownPreferencePage.ID, null, null).open());
		browser = new Browser(c, SWT.NONE);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(browser);
		browser.addProgressListener(ProgressListener.completedAdapter(e -> {
			loaded = true;
			applyStyle();
		}));
		browser.addLocationListener(LocationListener.changingAdapter(this::linkClicked));
		new BrowserFunction(browser, "mdPlantUml") { //$NON-NLS-1$
			@Override
			public Object function(Object[] args) {
				return args.length == 2 && args[0] instanceof String src && args[1] instanceof String style
						? PlantUml.svg(src, style)
						: ""; //$NON-NLS-1$
			}
		};
		try {
			pageUrl = fileUrl(new File(Activator.web(), "preview.html").toURI());
			browser.setUrl(pageUrl);
		} catch (Exception e) {
			browser.setText("<pre>Markdown Viewer: " + e + "</pre>");
		}
		return c;
	}

	private static void item(ToolBar bar, String text, Runnable action) {
		ToolItem i = new ToolItem(bar, SWT.PUSH);
		i.setText(text);
		i.addListener(SWT.Selection, e -> action.run());
	}

	@Override
	protected void pageChange(int page) {
		if (page == SOURCE && loaded) revealLine(((Number) browser.evaluate("return topLine();")).intValue());
		String target = page == PREVIEW ? previewTarget() : null;
		super.pageChange(page);
		if (page == PREVIEW && loaded) {
			if (stale) render();
			// once the browser is showing again; the page itself waits for its layout
			browser.getDisplay().asyncExec(() -> {
				if (!browser.isDisposed()) browser.execute("scrollToFraction(" + target + ")");
			});
		}
	}

	/**
	 * The editor's scroll position as a fraction of how far it can scroll, which the
	 * preview takes over: at the bottom of the source means at the bottom of the preview.
	 */
	private String previewTarget() {
		ITextViewer v = viewer();
		int top = v.getTopIndex(), bottom = v.getBottomIndex();
		int last = document().getNumberOfLines() - 1;
		int room = last - (bottom - top); // lines the editor can scroll through
		double f = bottom >= last ? 1 : room <= 0 ? 0 : Math.min(1, (double) top / room);
		return String.format(java.util.Locale.ROOT, "%.4f", f); //$NON-NLS-1$
	}

	private void render() {
		if (!loaded) return;
		stale = false;
		browser.execute("render(" + js(document().get()) + "," + js(baseUrl()) + ")");
	}

	private void applyStyle() {
		if (!loaded) return;
		IPreferenceStore s = Activator.prefs();
		FontData b = PreferenceConverter.getFontData(s, PrefInit.BODY_FONT);
		FontData c = PreferenceConverter.getFontData(s, PrefInit.CODE_FONT);
		browser.execute("applyStyle(" + js(b.getName()) + "," + b.getHeight() + "," + js(c.getName()) + ","
				+ c.getHeight() + "," + js(s.getString(PrefInit.LINE_HEIGHT)) + "," + js(s.getString(PrefInit.THEME)) + "," + s.getBoolean(PrefInit.BREAKS) + "," + Colors.json(s) + ",{\"node\":" + s.getInt(PrefInit.DIAGRAM_NODE_SPACING)
				+ ",\"rank\":" + s.getInt(PrefInit.DIAGRAM_RANK_SPACING) + ",\"pad\":" + s.getInt(PrefInit.DIAGRAM_PADDING) + "})");
		render(); // mermaid picks up the theme only on render
	}

	private void revealLine(int line) {
		IDocument d = document();
		line = Math.max(0, Math.min(line, d.getNumberOfLines() - 1));
		try {
			source.selectAndReveal(d.getLineOffset(line), 0);
		} catch (BadLocationException e) {
			return;
		}
		viewer().setTopIndex(line);
	}

	private void linkClicked(LocationEvent e) {
		String u = e.location;
		if (!loaded || u.startsWith(pageUrl) || u.startsWith("about:")) return; // page load, #anchors
		e.doit = false;
		try {
			if (u.matches("(?i)file:.*\\.(md|markdown)(#.*)?")) {
				URI uri = URI.create(u.replaceFirst("#.*", ""));
				IFile[] files = ResourcesPlugin.getWorkspace().getRoot().findFilesForLocationURI(uri);
				if (files.length > 0) IDE.openEditor(getSite().getPage(), files[0]);
				else IDE.openEditorOnFileStore(getSite().getPage(), EFS.getLocalFileSystem().getStore(uri));
			} else if (u.matches("(?i)(https?|mailto):.*")) {
				Program.launch(u); // never hand file:/other schemes to the shell (would run executables)
			}
		} catch (Exception x) {
			MessageDialog.openError(getSite().getShell(), "Markdown Viewer", x.toString());
		}
	}

	private void exportPdf() {
		if (!loaded) return;
		FileDialog fd = new FileDialog(getSite().getShell(), SWT.SAVE);
		fd.setFilterExtensions(new String[] { "*.pdf" });
		fd.setFileName(getEditorInput().getName().replaceFirst("\\.[^.]+$", "") + ".pdf");
		File doc = localFile();
		if (doc != null) fd.setFilterPath(doc.getParent());
		fd.setOverwrite(true);
		String out = fd.open();
		if (out == null) return;
		String html = (String) browser.evaluate("return exportDoc();");
		Display display = getSite().getShell().getDisplay();
		Job.create("Markdown PDF 내보내기", monitor -> {
			try {
				File web = Activator.web(), page = new File(web, "export.html");
				Files.writeString(page.toPath(), html);
				new File(out).delete();
				Process p = new ProcessBuilder(List.of(EDGE, "--headless=new", "--disable-gpu", "--no-pdf-header-footer",
						"--user-data-dir=" + new File(web.getParentFile(), "edge"), "--virtual-time-budget=15000",
						"--print-to-pdf=" + out, fileUrl(page.toURI()))).redirectErrorStream(true).start();
				p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
				p.waitFor();
				boolean ok = new File(out).length() > 0;
				display.asyncExec(() -> {
					if (!ok) MessageDialog.openError(display.getActiveShell(), "PDF 내보내기", "PDF 생성 실패: " + out);
					else if (MessageDialog.openQuestion(display.getActiveShell(), "PDF 내보내기", "저장했습니다.\n" + out + "\n\n열어볼까요?")) Program.launch(out);
				});
				return Status.OK_STATUS;
			} catch (Exception x) {
				return new Status(IStatus.ERROR, Activator.ID, "PDF 내보내기 실패", x);
			}
		}).schedule();
	}

	private IDocument document() {
		return source.getDocumentProvider().getDocument(getEditorInput());
	}

	private ITextViewer viewer() {
		return source.getAdapter(ITextViewer.class);
	}

	private File localFile() {
		IEditorInput in = getEditorInput();
		if (in instanceof IURIEditorInput u && "file".equals(u.getURI().getScheme())) return new File(u.getURI());
		return null;
	}

	/** Directory of the document as file:///C:/.../ so relative images and links resolve. */
	private String baseUrl() {
		File f = localFile();
		if (f == null) return "";
		String s = fileUrl(f.getParentFile().toURI());
		return s.endsWith("/") ? s : s + "/";
	}

	private static String fileUrl(URI uri) {
		return uri.toString().replaceFirst("^file:/+", "file:///");
	}

	/** Java string -> JavaScript string literal. */
	static String js(String s) {
		StringBuilder b = new StringBuilder(s.length() + 16).append('"');
		for (char ch : s.toCharArray()) {
			switch (ch) {
			case '"' -> b.append("\\\"");
			case '\\' -> b.append("\\\\");
			case '\n' -> b.append("\\n");
			case '\r' -> b.append("\\r");
			case '\u2028' -> b.append("\\u2028");
			case '\u2029' -> b.append("\\u2029");
			default -> {
				if (ch < 0x20) b.append(String.format("\\u%04x", (int) ch));
				else b.append(ch);
			}
			}
		}
		return b.append('"').toString();
	}

	@Override
	public void doSave(org.eclipse.core.runtime.IProgressMonitor monitor) {
		source.doSave(monitor);
	}

	@Override
	public void doSaveAs() {
		source.doSaveAs();
		setInput(source.getEditorInput());
		setPartName(getEditorInput().getName());
	}

	@Override
	public boolean isSaveAsAllowed() {
		return true;
	}

	@Override
	public void dispose() {
		Activator.prefs().removePropertyChangeListener(prefListener);
		if (source != null && source.getDocumentProvider() != null) {
			IDocument d = document();
			if (d != null) d.removeDocumentListener(docListener);
		}
		super.dispose();
	}
}
