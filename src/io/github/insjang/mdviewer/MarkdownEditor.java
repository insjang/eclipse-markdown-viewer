package io.github.insjang.mdviewer;

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
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.ScrollBar;
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
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

/** Source (generic text editor) + Preview (Edge browser) tabs at the bottom of the same editor area. */
@SuppressWarnings("restriction")
public class MarkdownEditor extends MultiPageEditorPart {
	private static final int SOURCE = 0, PREVIEW = 1;
	/** Edge for PDF export (headless print), null when not installed: Windows only */
	private static final String EDGE = java.util.stream.Stream.of("C:\\Program Files (x86)", "C:\\Program Files")
			.map(p -> p + "\\Microsoft\\Edge\\Application\\msedge.exe").filter(p -> new File(p).isFile()).findFirst().orElse(null);

	private ITextEditor source;
	private Browser browser;
	private String pageUrl;
	private boolean loaded, stale = true, styling;
	private Outline outline;
	private final Runnable renderLater = () -> {
		if (getActivePage() == PREVIEW) render();
		if (outline != null) outline.update(document().get());
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
	// OK or Restore Defaults changes dozens of keys at once: one restyle per editor afterwards
	private final IPropertyChangeListener prefListener = e -> {
		if (styling || browser == null || browser.isDisposed()) return;
		styling = true;
		browser.getDisplay().asyncExec(() -> {
			styling = false;
			if (!browser.isDisposed()) applyStyle();
		});
	};

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
		if (EDGE != null) item(bar, Messages.toolbar_pdf, this::exportPdf);
		item(bar, "A-", () -> Activator.zoom(-1));
		item(bar, "A+", () -> Activator.zoom(1));
		item(bar, Messages.toolbar_fonts, () -> PreferencesUtil
				.createPreferenceDialogOn(getSite().getShell(), MarkdownPreferencePage.ID, null, null).open());
		browser = new Browser(c, SWT.NONE);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(browser);
		browser.addProgressListener(ProgressListener.completedAdapter(e -> {
			loaded = true;
			browser.execute("setLabels(" + labels() + ")");
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
		// both ways by the scrollbar: the position, as a fraction, is read from the page
		// being left and applied to the one shown, once it is showing and laid out
		double previewAt = page == SOURCE && loaded ? ((Number) browser.evaluate("return scrollFraction();")).doubleValue() : 0;
		String target = page == PREVIEW ? previewTarget() : null;
		super.pageChange(page);
		if (page == SOURCE && loaded) {
			browser.getDisplay().asyncExec(() -> revealFraction(previewAt));
		}
		if (page == PREVIEW && loaded) {
			if (stale) render();
			// once the browser is showing again; the page itself waits for its layout
			browser.getDisplay().asyncExec(() -> {
				if (!browser.isDisposed()) browser.execute("scrollToFraction(" + target + ")");
			});
		}
	}

	/** How far down the editor is, 0 to 1, from its scrollbar in pixels (wrapped and folded lines count as shown). */
	private String previewTarget() {
		ScrollBar bar = viewer().getTextWidget().getVerticalBar();
		int range = bar == null ? 0 : bar.getMaximum() - bar.getThumb();
		double f = range <= 0 ? 0 : Math.min(1, (double) bar.getSelection() / range);
		return String.format(java.util.Locale.ROOT, "%.4f", f); //$NON-NLS-1$
	}

	/** Text shown inside the page, from messages*.properties. */
	private static String labels() {
		return "{\"frontMatter\":" + js(Messages.preview_frontMatter) + "}";
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

	/** Scrolls the editor to the fraction of its scroll range in pixels, 1 being the end, caret on the top line. */
	private void revealFraction(double f) {
		ITextViewer v = viewer();
		StyledText text = v == null ? null : v.getTextWidget();
		if (text == null || text.isDisposed() || text.getVerticalBar() == null) return;
		ScrollBar bar = text.getVerticalBar();
		text.setTopPixel((int) Math.round(Math.max(0, Math.min(1, f)) * Math.max(0, bar.getMaximum() - bar.getThumb())));
		try {
			v.setSelectedRange(document().getLineOffset(v.getTopIndex()), 0);
		} catch (BadLocationException e) {
			// keep the caret where it was
		}
	}

	@Override
	public <T> T getAdapter(Class<T> type) {
		if (type == IContentOutlinePage.class && source != null) {
			if (outline == null) outline = new Outline(this, document().get());
			return type.cast(outline);
		}
		return super.getAdapter(type);
	}

	void outlineClosed(Outline o) {
		if (outline == o) outline = null;
	}

	/** Caret to the start of the line (from 0) in Source, and the Preview, when showing, to the block from that line. */
	void reveal(int line) {
		try {
			source.selectAndReveal(document().getLineOffset(line), 0);
		} catch (BadLocationException e) {
			return;
		}
		if (loaded && getActivePage() == PREVIEW) browser.execute("scrollToLine(" + line + ")");
	}

	private void linkClicked(LocationEvent e) {
		String u = e.location;
		// page load, #anchors; Edge percent-encodes non-ASCII (Korean) folder names, File.toURI() does not
		if (!loaded || decode(u).startsWith(decode(pageUrl)) || u.startsWith("about:")) return;
		e.doit = false;
		// open after this callback returns: a new editor's Edge browser created inside it hangs WebView2 (#1)
		browser.getDisplay().asyncExec(() -> {
			if (!browser.isDisposed()) openLink(u);
		});
	}

	private void openLink(String u) {
		try {
			if (u.matches("(?i)file:.*\\.(md|markdown|mdown|mkd)(#.*)?")) { // the extensions in plugin.xml
				URI uri = URI.create(u.replaceFirst("#.*", ""));
				if (uri.getAuthority() != null || uri.getPath().matches("(?s)[/\\\\]{2}.*")) return; // network path: no SMB from a link
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
		Job.create(Messages.pdf_job, monitor -> {
			try {
				File web = Activator.web(), page = new File(web, "export.html"), pdf = new File(web.getParentFile(), "export.pdf");
				Files.writeString(page.toPath(), html);
				pdf.delete(); // printed next to the page, then moved: an existing target stays until there is a new PDF
				Process p = new ProcessBuilder(List.of(EDGE, "--headless=new", "--disable-gpu", "--no-pdf-header-footer",
						"--user-data-dir=" + new File(web.getParentFile(), "edge"), "--virtual-time-budget=15000",
						"--print-to-pdf=" + pdf, fileUrl(page.toURI()))).redirectErrorStream(true).start();
				p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
				p.waitFor();
				boolean ok = pdf.length() > 0;
				if (ok) Files.move(pdf.toPath(), new File(out).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				display.asyncExec(() -> {
					if (!ok) MessageDialog.openError(display.getActiveShell(), Messages.pdf_title, NLS.bind(Messages.pdf_failed, out));
					else if (MessageDialog.openQuestion(display.getActiveShell(), Messages.pdf_title, NLS.bind(Messages.pdf_saved, out))) Program.launch(out);
				});
				return Status.OK_STATUS;
			} catch (Exception x) {
				return new Status(IStatus.ERROR, Activator.ID, Messages.pdf_error, x);
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

	/** Percent-decoded URL, to compare Java's and Edge's spelling of the same one. */
	static String decode(String url) {
		try {
			return java.net.URLDecoder.decode(url.replace("+", "%2B"), java.nio.charset.StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			return url;
		}
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
		// the new file gets a new document: move the listener there so live preview and reload follow it
		IDocument old = document();
		source.doSaveAs();
		if (old != null) old.removeDocumentListener(docListener);
		setInput(source.getEditorInput());
		setPartName(getEditorInput().getName());
		document().addDocumentListener(docListener);
		stale = true;
		renderLater.run(); // base folder for relative images may have changed too
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
