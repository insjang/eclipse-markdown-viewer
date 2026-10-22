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
import org.eclipse.ui.texteditor.ITextEditorActionConstants;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.ui.texteditor.ITextEditorExtension2;
import org.eclipse.ui.texteditor.IUpdate;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.dnd.ImageTransfer;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.actions.ContributionItemFactory;
import org.eclipse.ui.part.ISetSelectionTarget;
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
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.HTMLTransfer;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.ide.IGotoMarker;
import org.eclipse.ui.internal.genericeditor.ExtensionBasedTextEditor;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

/** Source (generic text editor) + Preview (Edge or WebKitGTK browser) tabs at the bottom of the same editor area. */
@SuppressWarnings("restriction")
public class MarkdownEditor extends MultiPageEditorPart {
	private static final int SOURCE = 0, PREVIEW = 1;
	/** Chromium-based browser for the PDF export (headless print): Edge on Windows, Chrome or Chromium elsewhere; null hides the button */
	private static final String CHROME = printer(System.getenv("PATH"));

	/** path: the PATH variable (may be null); only absolute entries count, so nothing in Eclipse's working folder is run */
	static String printer(String path) {
		java.util.stream.Stream<String> candidates = File.separatorChar == '\\'
				? java.util.stream.Stream.of("C:\\Program Files (x86)", "C:\\Program Files").map(p -> p + "\\Microsoft\\Edge\\Application\\msedge.exe")
				: java.util.stream.Stream.concat(java.util.stream.Stream.of("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"), // macOS: untested
						java.util.stream.Stream.of("google-chrome", "google-chrome-stable", "chromium-browser", "chromium", "microsoft-edge")
								.flatMap(n -> java.util.Arrays.stream(path == null ? new String[0] : path.split(File.pathSeparator))
										.filter(d -> !d.isEmpty() && new File(d).isAbsolute()).map(d -> d + "/" + n)));
		return candidates.filter(p -> new File(p).isFile() && new File(p).canExecute()).findFirst().orElse(null);
	}

	private ITextEditor source;
	private Browser browser;
	private String pageUrl;
	private boolean loaded, stale = true, styling;
	private Outline outline;
	private Job linkCheck;
	private final Runnable renderLater = () -> {
		if (getActivePage() == PREVIEW) render();
		if (outline != null) outline.update(document().get());
		checkLinks();
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
		if (PrefInit.LINK_CHECK.equals(e.getProperty())) checkLinks();
		if (styling || browser == null || browser.isDisposed()) return;
		styling = true;
		browser.getDisplay().asyncExec(() -> {
			styling = false;
			if (!browser.isDisposed()) applyStyle();
		});
	};
	private Runnable stopThemeWatch;
	private String appliedTheme;

	@Override
	protected void createPages() {
		source = new ExtensionBasedTextEditor();
		try {
			setPageText(addPage(source, getEditorInput()), "Source");
		} catch (PartInitException e) {
			throw new IllegalStateException(e);
		}
		setPartName(getEditorInput().getName());
		IAction paste = source.getAction(ITextEditorActionConstants.PASTE);
		if (paste != null) source.setAction(ITextEditorActionConstants.PASTE, new ImagePaste(paste));
		document().addDocumentListener(docListener);
		setPageText(addPage(createPreview(getContainer())), "Preview");
		Activator.prefs().addPropertyChangeListener(prefListener);
		stopThemeWatch = ThemeMode.watch(this, this::themeMayHaveChanged);
		if (Activator.prefs().getBoolean(PrefInit.OPEN_PREVIEW)) setActivePage(PREVIEW);
		checkLinks();
	}

	/** Paste in Source: an image alone on the clipboard (a screenshot) goes to images/ next to the document as PNG and gets a link; the rest pastes as before. */
	private final class ImagePaste extends Action implements IUpdate {
		private final IAction paste;

		ImagePaste(IAction paste) {
			super(paste.getText());
			this.paste = paste;
			setActionDefinitionId(paste.getActionDefinitionId());
			setEnabled(paste.isEnabled());
			paste.addPropertyChangeListener(e -> {
				if (ENABLED.equals(e.getProperty())) setEnabled(paste.isEnabled());
			});
		}

		/** the editor asks its actions to update (read-only, selection): pass it on */
		@Override
		public void update() {
			if (paste instanceof IUpdate u) u.update();
			setEnabled(paste.isEnabled());
		}

		@Override
		public void run() {
			Clipboard c = new Clipboard(getSite().getShell().getDisplay());
			ImageData image;
			try { // text too (Excel, Word copy both): paste the text
				image = c.getContents(TextTransfer.getInstance()) == null ? (ImageData) c.getContents(ImageTransfer.getInstance()) : null;
			} finally {
				c.dispose();
			}
			File doc = localFile();
			if (image == null || doc == null) {
				paste.run();
				return;
			}
			// as the text paste does: read-only or checked-in files are refused (or made writable by the team provider)
			if (source instanceof ITextEditorExtension2 e && (!e.isEditorInputModifiable() || !e.validateEditorInputState())) return;
			try {
				File dir = new File(doc.getParentFile(), "images");
				dir.mkdirs();
				String name = "image-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date());
				File f = new File(dir, name + ".png");
				for (int n = 2; f.exists(); n++) f = new File(dir, name + "-" + n + ".png");
				ImageLoader l = new ImageLoader();
				l.data = new ImageData[] { image };
				l.save(f.getPath(), SWT.IMAGE_PNG);
				ITextSelection sel = (ITextSelection) source.getSelectionProvider().getSelection();
				String link = "![](images/" + f.getName() + ")";
				document().replace(sel.getOffset(), sel.getLength(), link);
				source.selectAndReveal(sel.getOffset() + link.length(), 0);
				// just images/ (the new file), not the whole folder tree on the UI thread
				if (getEditorInput() instanceof IFileEditorInput in) in.getFile().getParent().getFolder(IPath.fromPortableString("images")).refreshLocal(IResource.DEPTH_ONE, null);
			} catch (Exception x) {
				MessageDialog.openError(getSite().getShell(), "Markdown Viewer", NLS.bind(Messages.paste_failed, x));
			}
		}
	}

	/** Problems view warnings for broken relative links, workspace files only (markers need a resource). */
	private void checkLinks() {
		if (getEditorInput() instanceof IFileEditorInput in) linkCheck = LinkCheck.schedule(linkCheck, in.getFile(), document().get());
	}

	private Control createPreview(Composite parent) {
		Composite c = new Composite(parent, SWT.NONE);
		GridLayoutFactory.fillDefaults().spacing(0, 0).applyTo(c);
		Composite bar = new Composite(c, SWT.NONE);
		RowLayout row = new RowLayout();
		row.marginTop = row.marginBottom = 3;
		row.spacing = 4;
		bar.setLayout(row);
		GridDataFactory.fillDefaults().align(SWT.END, SWT.CENTER).applyTo(bar);
		item(bar, Messages.toolbar_showIn, this::showInExplorer);
		if (CHROME != null) item(bar, Messages.toolbar_pdf, this::exportPdf);
		item(bar, Messages.toolbar_html, this::exportHtml);
		item(bar, Messages.toolbar_copyHtml, this::copyHtml);
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
		browser.setMenu(previewMenu().createContextMenu(browser));
		new BrowserFunction(browser, "mdPlantUml") { //$NON-NLS-1$
			@Override
			public Object function(Object[] args) {
				return args.length == 2 && args[0] instanceof String src && args[1] instanceof String style
						? PlantUml.svg(src, style)
						: ""; //$NON-NLS-1$
			}
		};
		new BrowserFunction(browser, "mdSource") { //$NON-NLS-1$
			@Override
			public Object function(Object[] args) {
				if (args.length == 1 && args[0] instanceof Number n) browser.getDisplay().asyncExec(() -> {
					if (browser.isDisposed()) return;
					setActivePage(SOURCE);
					// after pageChange's own scroll to the preview's position, which is queued the same way
					browser.getDisplay().asyncExec(() -> {
						if (!browser.isDisposed()) reveal(n.intValue());
					});
				});
				return null;
			}
		};
		new BrowserFunction(browser, "mdCopy") { //$NON-NLS-1$
			@Override
			public Object function(Object[] args) {
				// after the callback, as with links (#1): nothing that pumps events inside a WebView2 callback
				if (args.length == 1 && args[0] instanceof String text && !text.isEmpty()) browser.getDisplay().asyncExec(() -> {
					if (!browser.isDisposed()) toClipboard(text);
				});
				return null;
			}
		};
		try {
			pageUrl = fileUrl(new File(Activator.web(), "preview.html").toURI());
			browser.setUrl(pageUrl + firstPaintQuery());
		} catch (Exception e) {
			browser.setText("<pre>Markdown Viewer: " + e + "</pre>");
		}
		return c;
	}

	/** Replaces the browser's own menu (Back, Refresh, Inspect...): copy, select all and the workbench Show In. */
	private MenuManager previewMenu() {
		MenuManager menu = new MenuManager();
		menu.add(new Action(Messages.menu_copy) {
			@Override
			public void run() {
				copyInPreview();
			}
		});
		menu.add(new Action(Messages.menu_copySource) {
			@Override
			public void run() {
				if (!document().get().isEmpty()) toClipboard(document().get());
			}
		});
		menu.add(new Action(Messages.menu_selectAll) {
			@Override
			public void run() {
				selectAllInPreview();
			}
		});
		menu.add(new Separator());
		MenuManager showIn = new MenuManager(Messages.menu_showIn);
		showIn.add(ContributionItemFactory.VIEWS_SHOW_IN.create(getSite().getWorkbenchWindow()));
		menu.add(showIn);
		return menu;
	}

	private void showInExplorer() {
		if (!(getEditorInput() instanceof IFileEditorInput in)) {
			MessageDialog.openInformation(getSite().getShell(), "Markdown Viewer", Messages.showIn_notWorkspace); //$NON-NLS-1$
			return;
		}
		try {
			if (getSite().getPage().showView(IPageLayout.ID_PROJECT_EXPLORER) instanceof ISetSelectionTarget t)
				t.selectReveal(new StructuredSelection(in.getFile()));
		} catch (PartInitException e) {
			MessageDialog.openError(getSite().getShell(), "Markdown Viewer", e.getMessage()); //$NON-NLS-1$
		}
	}

	private void toClipboard(String text) {
		Clipboard c = new Clipboard(browser.getDisplay());
		try {
			c.setContents(new Object[] { text }, new Transfer[] { TextTransfer.getInstance() });
		} finally {
			c.dispose();
		}
	}

	/** A button drawn as a rounded chip, its colors mixed from the bar's (theme) colors whenever it paints. */
	private static void item(Composite bar, String text, Runnable action) {
		Canvas b = new Canvas(bar, SWT.DOUBLE_BUFFERED);
		b.setFont(bar.getFont());
		b.setCursor(bar.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
		GC measure = new GC(b);
		Point size = measure.textExtent(text);
		measure.dispose();
		b.setLayoutData(new RowData(size.x + 16, size.y + 6));
		boolean[] hover = { false };
		b.addPaintListener(e -> {
			Color bg = bar.getBackground(), fg = bar.getForeground();
			Point s = b.getSize();
			e.gc.setAntialias(SWT.ON);
			e.gc.setBackground(bg);
			e.gc.fillRectangle(0, 0, s.x, s.y);
			e.gc.setBackground(mix(bg, fg, hover[0] ? 0.22 : 0.10));
			e.gc.fillRoundRectangle(0, 0, s.x, s.y, 8, 8);
			e.gc.setForeground(fg);
			e.gc.drawText(text, (s.x - size.x) / 2, (s.y - size.y) / 2, true);
		});
		b.addListener(SWT.MouseEnter, e -> { hover[0] = true; b.redraw(); });
		b.addListener(SWT.MouseExit, e -> { hover[0] = false; b.redraw(); });
		b.addListener(SWT.MouseUp, e -> {
			if (e.button == 1 && b.getClientArea().contains(e.x, e.y)) action.run();
		});
	}

	private static Color mix(Color a, Color b, double t) {
		return new Color((int) (a.getRed() * (1 - t) + b.getRed() * t), (int) (a.getGreen() * (1 - t) + b.getGreen() * t),
				(int) (a.getBlue() * (1 - t) + b.getBlue() * t));
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
		return "{\"frontMatter\":" + js(Messages.preview_frontMatter) + ",\"find\":" + js(Messages.find_find) + ",\"findNone\":" + js(Messages.find_none)
				+ ",\"findPrev\":" + js(Messages.find_previous) + ",\"findNext\":" + js(Messages.find_next) + ",\"findClose\":" + js(Messages.find_close) + ",\"copy\":" + js(Messages.preview_copy) + ",\"copied\":" + js(Messages.preview_copied)
				+ ",\"zoomReset\":" + js(Messages.preview_zoomReset) + "}";
	}

	private void render() {
		if (!loaded) return;
		stale = false;
		browser.execute("render(" + js(document().get()) + "," + js(baseUrl()) + ")");
	}

	/**
	 * Dark or light and the page color for the first paint (see preview.html): the page is dark by default, which showed as a dark flash
	 * in a light Eclipse before applyStyle had run. The Browser behind the page gets the same color for the moment before the page exists.
	 */
	private String firstPaintQuery() {
		IPreferenceStore s = Activator.prefs();
		String theme = ThemeMode.resolve(s.getString(PrefInit.THEME), themeReference());
		RGB c = PreferenceConverter.getColor(s, Colors.key(theme, "bg")); //$NON-NLS-1$
		browser.setBackground(new Color(c));
		return String.format("?theme=%s&bg=%%23%02x%02x%02x", theme, c.red, c.green, c.blue); //$NON-NLS-1$
	}

	/** A widget of the Source: what the Eclipse theme made of it says whether the editors are dark. */
	private Control themeReference() {
		ITextViewer v = viewer();
		return v == null ? null : v.getTextWidget();
	}

	/** The Eclipse theme may have been switched since the Preview was drawn: draw it again if dark or light is not what it was. */
	private void themeMayHaveChanged() {
		if (!loaded || browser == null || browser.isDisposed()) return;
		if (!ThemeMode.resolve(Activator.prefs().getString(PrefInit.THEME), themeReference()).equals(appliedTheme)) applyStyle();
	}

	private void applyStyle() {
		if (!loaded) return;
		IPreferenceStore s = Activator.prefs();
		FontData b = PreferenceConverter.getFontData(s, PrefInit.BODY_FONT);
		FontData c = PreferenceConverter.getFontData(s, PrefInit.CODE_FONT);
		appliedTheme = ThemeMode.resolve(s.getString(PrefInit.THEME), themeReference());
		browser.execute("applyStyle(" + js(b.getName()) + "," + b.getHeight() + "," + js(c.getName()) + ","
				+ c.getHeight() + "," + js(s.getString(PrefInit.LINE_HEIGHT)) + "," + js(appliedTheme) + "," + s.getBoolean(PrefInit.BREAKS) + "," + Colors.json(s) + ",{\"node\":" + s.getInt(PrefInit.DIAGRAM_NODE_SPACING)
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

	/** Ctrl+F (Find/Replace) while the Preview tab shows: the page's find bar. */
	/** Copy (Ctrl+C and the context menu) on the Preview tab: the selection as text, see plainText in preview.js. */
	void copyInPreview() {
		if (loaded && browser.evaluate("return copyText()") instanceof String text && !text.isEmpty()) toClipboard(text); //$NON-NLS-1$
	}

	void selectAllInPreview() {
		if (loaded) browser.execute("selectAll()"); //$NON-NLS-1$
	}

	void findInPreview() {
		if (!loaded) return;
		browser.setFocus();
		browser.execute("findOpen()");
	}

	@Override
	public <T> T getAdapter(Class<T> type) {
		if (type == IContentOutlinePage.class && source != null) {
			if (outline == null) outline = new Outline(this, document().get());
			return type.cast(outline);
		}
		// Problems view double-click: to Source, where the marker shows, then to the marker
		if (type == IGotoMarker.class && source != null) return type.cast((IGotoMarker) marker -> {
			setActivePage(SOURCE);
			IDE.gotoMarker(source, marker);
		});
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
				Process p = new ProcessBuilder(List.of(CHROME, "--headless=new", "--disable-gpu", "--no-pdf-header-footer",
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

	/** The preview as drawn, with computed styles inline, as HTML and plain text on the clipboard. */
	private void copyHtml() {
		if (!loaded) return; // the toolbar shows with the Preview, so the page is up to date
		if (!(browser.evaluate("return htmlFragment();") instanceof Object[] a) || a.length != 2 || !(a[0] instanceof String html)
				|| !(a[1] instanceof String text) || text.isEmpty()) return;
		Clipboard c = new Clipboard(browser.getDisplay());
		try {
			c.setContents(new Object[] { html, text }, new Transfer[] { HTMLTransfer.getInstance(), TextTransfer.getInstance() });
		} finally {
			c.dispose();
		}
	}

	/** One .html file: the preview as drawn (diagrams as SVG), CSS and KaTeX fonts inside, local images linked relative to it (HtmlExport). */
	private void exportHtml() {
		if (!loaded) return; // the toolbar shows with the Preview, so the page is up to date
		String name = getEditorInput().getName().replaceFirst("\\.[^.]+$", "");
		FileDialog fd = new FileDialog(getSite().getShell(), SWT.SAVE);
		fd.setFilterExtensions(new String[] { "*.html" });
		fd.setFileName(name + ".html");
		File doc = localFile();
		if (doc != null) fd.setFilterPath(doc.getParent());
		fd.setOverwrite(true);
		String out = fd.open();
		if (out == null) return;
		try {
			String html = (String) browser.evaluate("return htmlDoc(" + js(name) + ");");
			Files.writeString(new File(out).toPath(), HtmlExport.page(html, Activator.web(), ThemeMode.resolve(Activator.prefs().getString(PrefInit.THEME), themeReference()), new File(out).getAbsoluteFile().getParentFile()));
			if (MessageDialog.openQuestion(getSite().getShell(), Messages.html_title, NLS.bind(Messages.pdf_saved, out))) Program.launch(out);
		} catch (Exception x) {
			MessageDialog.openError(getSite().getShell(), Messages.html_title, NLS.bind(Messages.html_failed, out) + "\n" + x);
		}
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
		if (getEditorInput() instanceof IFileEditorInput in) LinkCheck.clear(linkCheck, in.getFile());
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
		if (getEditorInput() instanceof IFileEditorInput in) LinkCheck.clear(linkCheck, in.getFile()); // only open documents are checked
		else if (linkCheck != null) linkCheck.cancel();
		if (stopThemeWatch != null) stopThemeWatch.run();
		if (source != null && source.getDocumentProvider() != null) {
			IDocument d = document();
			if (d != null) d.removeDocumentListener(docListener);
		}
		super.dispose();
	}
}
