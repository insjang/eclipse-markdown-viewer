package io.github.insjang.mdviewer;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.core.commands.ParameterizedCommand;
import org.eclipse.core.commands.Command;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.texteditor.ITextEditorActionConstants;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
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
import org.eclipse.jface.text.IViewportListener;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.jface.resource.ResourceLocator;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.HTMLTransfer;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.core.runtime.ILog;
import org.eclipse.jface.bindings.Binding;
import org.eclipse.jface.bindings.keys.KeySequence;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.keys.IBindingService;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.contexts.IContextService;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.ide.IGotoMarker;
import org.eclipse.ui.internal.genericeditor.ExtensionBasedTextEditor;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

import io.github.insjang.mdviewer.editing.FormatHandler;
import io.github.insjang.mdviewer.editing.TableBackTab;
import io.github.insjang.mdviewer.editing.WikiLinks;

/** Source (generic text editor) + Preview (Edge or WebKitGTK browser) tabs at the bottom of the same editor area. */
@SuppressWarnings("restriction")
public class MarkdownEditor extends MultiPageEditorPart implements ITextEditor {
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

	/**
	 * The editors that are open at the same time, for example the two halves of Eclipse's split editor: one can show the Source and
	 * another the Preview of the same document, and then they follow each other by source line.
	 */
	private static final List<MarkdownEditor> OPEN = new java.util.ArrayList<>();
	private ITextEditor source;
	private SourceScroll scroll;
	private int lastTop = -1;
	private final IViewportListener viewportListener = offset -> sourceScrolled();
	private Browser browser;
	private String pageUrl;
	private boolean loaded, stale = true, styling;
	private Outline outline;
	private Job linkCheck, backlinks;
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
			Display.getCurrent().timerExec(renderDelay(e.getDocument().getLength()), renderLater); // external change while Preview is showing
		}
	};
	// OK or Restore Defaults changes dozens of keys at once: one restyle per editor afterwards
	private final IPropertyChangeListener prefListener = e -> {
		if (PrefInit.LINK_CHECK.equals(e.getProperty())) checkLinks();
		if (PrefInit.BOOKMARKS.equals(e.getProperty())) Display.getDefault().asyncExec(this::showBookmarks);
		if (PrefInit.BACKLINKS.equals(e.getProperty())) Display.getDefault().asyncExec(this::findBacklinks);
		if (styling || browser == null || browser.isDisposed()) return;
		styling = true;
		browser.getDisplay().asyncExec(() -> {
			styling = false;
			if (!browser.isDisposed()) applyStyle();
		});
	};
	// bookmarks added or removed in this file, from the keys or the Bookmarks view: the Preview's ribbons follow
	private final IResourceChangeListener markerListener = e -> {
		if (getEditorInput() instanceof IFileEditorInput in
				&& java.util.Arrays.stream(e.findMarkerDeltas(IMarker.BOOKMARK, true)).anyMatch(d -> d.getResource().equals(in.getFile()))) {
			Display.getDefault().asyncExec(this::showBookmarks);
		}
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
		// on the Source page's own site, so that the context is active only while the Source tab shows: on the Preview Ctrl+B is Eclipse's again
		source.getSite().getService(IContextService.class).activateContext(FormatHandler.CONTEXT); // Ctrl+B, Ctrl+I, Ctrl+K
		setPartName(getEditorInput().getName());
		IAction paste = source.getAction(ITextEditorActionConstants.PASTE);
		if (paste != null) source.setAction(ITextEditorActionConstants.PASTE, new ImagePaste(paste));
		document().addDocumentListener(docListener);
		TableBackTab.install(viewer()); // Shift+Tab in a table cell
		if (viewer() instanceof ISourceViewer sv) {
			scroll = new SourceScroll(sv);
			sv.addViewportListener(viewportListener);
			// the viewer does not announce every kind of scrolling (wheel, keys): a repaint always follows, and tells it
			sv.getTextWidget().addListener(SWT.Paint, e -> sourceScrolled());
		}
		OPEN.add(this);
		setPageText(addPage(createPreview(getContainer())), "Preview");
		Activator.prefs().addPropertyChangeListener(prefListener);
		ResourcesPlugin.getWorkspace().addResourceChangeListener(markerListener, IResourceChangeEvent.POST_CHANGE);
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
		// the buttons sit at the right end of the Source / Preview tab row
		Composite bar = new Composite(parent, SWT.NONE);
		RowLayout row = new RowLayout();
		row.marginTop = row.marginBottom = 1;
		row.spacing = 4;
		bar.setLayout(row);
		if (parent instanceof CTabFolder tabs) {
			tabs.setTopRight(bar, SWT.RIGHT | SWT.WRAP); // below the tabs when the row is too narrow for both
			tabs.setMinimumCharacters(20); // "Source" and "Preview" are never cut short
			// the theme paints the tab row in a color no API returns: take it from the painted row, just left of the buttons
			tabs.addListener(SWT.Paint, e -> {
				Rectangle r = bar.getBounds();
				int x = r.x - 2, y = r.y + r.height / 2;
				if (x < e.x || x >= e.x + e.width || y < e.y || y >= e.y + e.height) return;
				Image px = new Image(e.display, 1, 1);
				e.gc.copyArea(px, x, y);
				ImageData d = px.getImageData();
				px.dispose();
				RGB rgb = d.palette.getRGB(d.getPixel(0, 0));
				if (bar.getData() instanceof Color old && old.getRGB().equals(rgb)) return;
				bar.setData(new Color(rgb));
				bar.redraw(0, 0, r.width, r.height, true);
			});
			bar.addPaintListener(e -> {
				if (bar.getData() instanceof Color tabRow) {
					e.gc.setBackground(tabRow);
					e.gc.fillRectangle(bar.getClientArea());
				}
			});
		}
		item(bar, "org.eclipse.ui.ide.application/icons/full/elcl16/split_vertical.png", "", Messages.toolbar_split, this::toggleSplit);
		item(bar, "org.eclipse.ui/icons/full/obj16/fldr_obj.png", "", Messages.toolbar_showIn, this::showInExplorer);
		Canvas export = item(bar, "org.eclipse.ui/icons/full/etool16/export_wiz.png", DROPDOWN, Messages.toolbar_export, null);
		export.addListener(SWT.MouseUp, e -> {
			if (e.button == 1 && export.getClientArea().contains(e.x, e.y)) exportMenu(bar, export);
		});
		item(bar, "org.eclipse.ui.workbench.texteditor/icons/full/etool16/zoomOut.png", "", Messages.toolbar_smaller, () -> Activator.zoom(-1));
		item(bar, "org.eclipse.ui.workbench.texteditor/icons/full/etool16/zoomIn.png", "", Messages.toolbar_larger, () -> Activator.zoom(1));
		item(bar, "org.eclipse.ui.ide/icons/full/elcl16/configs.png", "", Messages.toolbar_fonts, () -> PreferencesUtil
				.createPreferenceDialogOn(getSite().getShell(), MarkdownPreferencePage.ID, null, null).open());
		browser = new Browser(c, SWT.NONE);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(browser);
		browser.addProgressListener(ProgressListener.completedAdapter(e -> {
			loaded = true;
			browser.execute("setLabels(" + labels() + ")");
			applyStyle();
			updateSync();
			findBacklinks();
		}));
		browser.addLocationListener(LocationListener.changingAdapter(this::linkClicked));
		browser.setMenu(previewMenu().createContextMenu(browser));
		new BrowserFunction(browser, "mdPlantUml") { //$NON-NLS-1$
			@Override
			public Object function(Object[] args) {
				return args.length == 2 && args[0] instanceof String src && args[1] instanceof String style && Activator.prefs().getBoolean(PrefInit.PLANTUML)
						? PlantUml.svg(src, style)
						: ""; //$NON-NLS-1$
			}
		};
		new BrowserFunction(browser, "mdScroll") { //$NON-NLS-1$
			@Override
			public Object function(Object[] args) {
				// the Preview was scrolled by the user (a line, and whether it is at its end): the Source beside it follows
				if (args.length == 2 && args[0] instanceof Number line) {
					MarkdownEditor beside = sourceBeside();
					if (beside != null) beside.scroll.scrollTo(line.doubleValue(), Boolean.TRUE.equals(args[1]));
				}
				return null;
			}
		};
		new BrowserFunction(browser, "mdSource") { //$NON-NLS-1$
			@Override
			public Object function(Object[] args) {
				if (args.length == 1 && args[0] instanceof Number n) browser.getDisplay().asyncExec(() -> {
					if (browser.isDisposed()) return;
					MarkdownEditor beside = sourceBeside();
					if (beside != null) { // the Source of this document shows in another half: go there, the Preview stays
						beside.showLine(n.intValue(), true);
						return;
					}
					setActivePage(SOURCE);
					// after pageChange's own scroll to the preview's position, which is queued the same way
					browser.getDisplay().asyncExec(() -> {
						if (!browser.isDisposed()) reveal(n.intValue());
					});
				});
				return null;
			}
		};
		// keys the browser would keep for itself (Ctrl+_ zooms out): run what Eclipse binds to them. Only these keys:
		// the page must not be able to run any command it names
		new BrowserFunction(browser, "mdKey") { //$NON-NLS-1$
			@Override
			public Object function(Object[] args) {
				if (args.length == 1 && "M1+M2+-".equals(args[0]) && args[0] instanceof String key) browser.getDisplay().asyncExec(() -> { //$NON-NLS-1$
					if (browser.isDisposed()) return;
					try {
						Binding b = getSite().getService(IBindingService.class).getPerfectMatch(KeySequence.getInstance(key));
						if (b != null && b.getParameterizedCommand() != null) getSite().getService(IHandlerService.class).executeCommand(b.getParameterizedCommand(), null);
					} catch (Exception e) {
						ILog.of(MarkdownEditor.class).warn("key " + key, e); //$NON-NLS-1$
					}
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
			// SWT's Edge browser registers a BrowserFunction created inside another WebView2 callback later, through asyncExec, and the
			// registration only applies to the next document: the page is loaded after it, the same way
			browser.getDisplay().asyncExec(() -> {
				if (!browser.isDisposed()) browser.setUrl(pageUrl + firstPaintQuery());
			});
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
		menu.add(new Action(Messages.toolbar_copyHtml) {
			@Override
			public void run() {
				copyHtml();
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
	/** Label of a toolbar button that opens a menu: drawn as a small triangle, which every font can show. */
	private static final String DROPDOWN = "\u25BE"; //$NON-NLS-1$

	/** The Export menu, drawn like the buttons in the tab row's colors (a system menu keeps the system's), opening upwards from the button since the row is at the bottom. */
	private void exportMenu(Composite bar, Control button) {
		Color row = bar.getData() instanceof Color c ? c : bar.getParent().getBackground();
		Shell pop = new Shell(button.getShell(), SWT.NO_TRIM | SWT.ON_TOP);
		pop.setBackground(mix(row, bar.getDisplay().getSystemColor(SWT.COLOR_BLACK), 0.22));
		RowLayout column = new RowLayout(SWT.VERTICAL);
		column.fill = true;
		column.marginWidth = column.marginHeight = 4;
		column.spacing = 0;
		pop.setLayout(column);
		if (CHROME != null) entry(pop, bar, Messages.toolbar_pdf, this::exportPdf);
		entry(pop, bar, Messages.toolbar_html, this::exportHtml);
		entry(pop, bar, Messages.toolbar_copyHtml, this::copyHtml);
		pop.pack();
		Point p = button.toDisplay(0, 0);
		pop.setLocation(p.x, p.y - pop.getSize().y - 2);
		pop.addListener(SWT.Deactivate, e -> pop.getDisplay().asyncExec(pop::dispose)); // a click elsewhere closes it
		pop.open();
	}

	private static void entry(Shell pop, Composite bar, String text, Runnable action) {
		Canvas r = new Canvas(pop, SWT.DOUBLE_BUFFERED);
		r.setFont(bar.getFont());
		r.setCursor(bar.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
		GC measure = new GC(r);
		Point size = measure.textExtent(text);
		measure.dispose();
		r.setLayoutData(new RowData(size.x + 24, size.y + 8));
		boolean[] hover = { false };
		r.addPaintListener(e -> {
			Color bg = pop.getBackground();
			RGB b = bg.getRGB();
			// the row's text color is too dim on the darker menu: pull it towards white or black
			Color fg = mix(bar.getForeground(), bar.getDisplay().getSystemColor(
					b.red * 299 + b.green * 587 + b.blue * 114 < 128000 ? SWT.COLOR_WHITE : SWT.COLOR_BLACK), 0.6);
			Point s = r.getSize();
			e.gc.setAntialias(SWT.ON);
			e.gc.setBackground(bg);
			e.gc.fillRectangle(0, 0, s.x, s.y);
			if (hover[0]) {
				e.gc.setBackground(mix(bg, fg, 0.18));
				e.gc.fillRoundRectangle(0, 0, s.x, s.y, 6, 6);
			}
			e.gc.setForeground(fg);
			e.gc.drawText(text, 12, (s.y - size.y) / 2, true);
		});
		r.addListener(SWT.MouseEnter, e -> { hover[0] = true; r.redraw(); });
		r.addListener(SWT.MouseExit, e -> { hover[0] = false; r.redraw(); });
		r.addListener(SWT.Traverse, e -> {
			if (e.detail == SWT.TRAVERSE_ESCAPE) pop.dispose();
		});
		r.addListener(SWT.MouseUp, e -> {
			if (e.button != 1 || !r.getClientArea().contains(e.x, e.y)) return;
			pop.dispose();
			action.run();
		});
	}

	/** A toolbar button: a platform icon (bundle/path) and a label, either may be empty; the tip names it. The action may be null when the caller listens itself. */
	private static Canvas item(Composite bar, String icon, String label, String tip, Runnable action) {
		Canvas b = new Canvas(bar, SWT.DOUBLE_BUFFERED);
		b.setToolTipText(tip);
		b.setFont(bar.getFont());
		b.setCursor(bar.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
		int slash = icon.indexOf('/');
		Image img = ResourceLocator.imageDescriptorFromBundle(icon.substring(0, slash), icon.substring(slash + 1))
				.map(d -> d.createImage(false)).orElse(null);
		if (img != null) b.addDisposeListener(e -> img.dispose());
		// without its icon the button shows its name
		String text = img == null && label.isEmpty() ? tip : label;
		GC measure = new GC(b);
		boolean arrow = text == DROPDOWN;
		Point size = text.isEmpty() || arrow ? new Point(arrow ? 7 : 0, measure.getFontMetrics().getHeight()) : measure.textExtent(text);
		measure.dispose();
		Rectangle ib = img == null ? new Rectangle(0, 0, 0, 0) : img.getBounds();
		int iw = img == null ? 0 : ib.width + (text.isEmpty() ? 0 : 4);
		b.setLayoutData(new RowData(size.x + iw + 16, Math.max(size.y, ib.height) + 6));
		boolean[] hover = { false };
		b.addPaintListener(e -> {
			Color bg = bar.getData() instanceof Color c ? c : bar.getParent().getBackground(), fg = bar.getForeground();
			Point s = b.getSize();
			e.gc.setAntialias(SWT.ON);
			e.gc.setBackground(bg);
			e.gc.fillRectangle(0, 0, s.x, s.y);
			// a little darker than the row, on dark and light themes alike
			e.gc.setBackground(mix(bg, bar.getDisplay().getSystemColor(SWT.COLOR_BLACK), hover[0] ? 0.38 : 0.22));
			e.gc.fillRoundRectangle(0, 0, s.x, s.y, 8, 8);
			e.gc.setForeground(fg);
			int x = (s.x - size.x - iw) / 2;
			if (img != null) e.gc.drawImage(img, x, (s.y - ib.height) / 2);
			if (arrow) {
				int ax = x + iw, ay = s.y / 2 - 1;
				e.gc.setBackground(fg);
				e.gc.fillPolygon(new int[] { ax, ay - 2, ax + 7, ay - 2, ax + 3, ay + 2 });
			} else e.gc.drawText(text, x + iw, (s.y - size.y) / 2, true);
		});
		b.addListener(SWT.MouseEnter, e -> { hover[0] = true; b.redraw(); });
		b.addListener(SWT.MouseExit, e -> { hover[0] = false; b.redraw(); });
		if (action != null) b.addListener(SWT.MouseUp, e -> {
			if (e.button == 1 && b.getClientArea().contains(e.x, e.y)) action.run();
		});
		return b;
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
			findBacklinks();
			// once the browser is showing again; the page itself waits for its layout
			browser.getDisplay().asyncExec(() -> {
				if (!browser.isDisposed()) browser.execute("scrollToFraction(" + target + ")");
			});
		}
		Display.getDefault().asyncExec(MarkdownEditor::refreshSync);
	}

	/** The other editors open on the same document. */
	private List<MarkdownEditor> partners() {
		IDocument d = source == null ? null : document();
		List<MarkdownEditor> list = new java.util.ArrayList<>();
		for (MarkdownEditor e : OPEN) if (e != this && d != null && e.source != null && e.document() == d) list.add(e);
		return list;
	}

	/**
	 * Source and Preview of this document side by side, the Source on the left: Eclipse's split editor (the same command as Ctrl+{), the
	 * original editor on its Source tab and the new half on its Preview tab. When it is split already, the second half goes away again,
	 * and the editor that is left shows the tab of the half the button was pressed in (the buttons of the Preview are on its tab).
	 */
	void toggleSplit() {
		IWorkbenchPage page = getSite().getPage();
		List<MarkdownEditor> halves = new java.util.ArrayList<>(partners());
		boolean wasSplit = !halves.isEmpty();
		halves.add(this);
		int shown = getActivePage();
		page.activate(this); // the command splits the active editor
		try {
			Command split = PlatformUI.getWorkbench().getService(ICommandService.class).getCommand("org.eclipse.ui.window.splitEditor"); //$NON-NLS-1$
			// Splitter.isHorizontal=false: the halves next to each other (the command's default is one above the other)
			ParameterizedCommand side = ParameterizedCommand.generateCommand(split, java.util.Map.of("Splitter.isHorizontal", "false")); //$NON-NLS-1$ //$NON-NLS-2$
			PlatformUI.getWorkbench().getService(IHandlerService.class).executeCommand(side, null);
		} catch (Exception e) {
			org.eclipse.core.runtime.Platform.getLog(MarkdownEditor.class).log(new Status(IStatus.WARNING, Activator.ID, "Split editor: " + e, e)); //$NON-NLS-1$
			return;
		}
		if (wasSplit) {
			Display.getDefault().asyncExec(() -> {
				for (MarkdownEditor e : halves) {
					if (!OPEN.contains(e)) continue; // closed with the second half
					e.setActivePage(shown);
					page.activate(e);
				}
			});
			return;
		}
		Display.getDefault().asyncExec(() -> {
			List<MarkdownEditor> others = partners();
			if (others.isEmpty() || getActivePage() < 0) return;
			setActivePage(SOURCE);
			others.get(others.size() - 1).setActivePage(PREVIEW);
			page.activate(this);
		});
	}

	/** An editor of the same document that shows its Source, or null. */
	private MarkdownEditor sourceBeside() {
		for (MarkdownEditor e : partners()) if (e.scroll != null && e.getActivePage() == SOURCE) return e;
		return null;
	}

	/** Whether this editor shows its Preview (and the page is there to be told something). */
	private boolean previewShows() {
		return loaded && browser != null && !browser.isDisposed() && getActivePage() == PREVIEW;
	}

	/** Every open editor looks again at who shows what: a Preview follows the Source beside it, and only then reports its own scrolling. */
	private static void refreshSync() {
		for (MarkdownEditor e : new java.util.ArrayList<>(OPEN)) e.updateSync();
	}

	/** Tells the page whether it follows a Source beside it, and if so lines it up with that Source right away. */
	private void updateSync() {
		if (!previewShows()) return;
		MarkdownEditor beside = sourceBeside();
		browser.execute("setSync(" + (beside != null) + ")");
		if (beside != null) pushToPreview(beside.scroll);
	}

	/** Called on viewport changes and on every paint of the Source: only a changed top pixel is a scroll. */
	private void sourceScrolled() {
		StyledText text = viewer() == null ? null : viewer().getTextWidget();
		if (text == null || text.isDisposed()) return;
		int top = text.getTopPixel();
		if (top == lastTop) return;
		lastTop = top;
		if (scroll == null || getActivePage() != SOURCE || scroll.isOwnMove()) return;
		for (MarkdownEditor e : partners()) if (e.previewShows()) e.pushToPreview(scroll);
	}

	/** The Preview follows a Source: its top line, and the top line it has at its end, tell the page where to be. */
	private void pushToPreview(SourceScroll from) {
		browser.execute(String.format(java.util.Locale.ROOT, "syncTo(%.3f,%.3f,%d,%d)", from.topLine(), from.endLine(), from.visibleLines(), from.lineCount()));
	}

	/** The caret to the start of the line, and what shows (the Source of this editor, or its Preview) there; the editor gets the focus when asked. */
	private void showLine(int line, boolean activate) {
		try {
			source.selectAndReveal(document().getLineOffset(line), 0);
		} catch (BadLocationException e) {
			return;
		}
		if (previewShows()) browser.execute("scrollToLine(" + line + ")");
		if (activate) getSite().getPage().activate(this);
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
				+ ",\"zoomReset\":" + js(Messages.preview_zoomReset) + ",\"backlinks\":" + js(Messages.preview_backlinks) + "}";
	}

	private void render() {
		if (!loaded) return;
		stale = false;
		String src = document().get();
		browser.execute("render(" + js(src) + "," + js(baseUrl()) + "," + wikiLinks(src) + ")");
		showBookmarks();
	}

	/** The wikilink targets the document uses, resolved: {target: file URL or null}, for the Preview (see WikiLinks). */
	private String wikiLinks(String src) {
		File doc = localFile();
		if (doc == null || !src.contains("[[")) return "null";
		WikiLinks.Vault vault = Activator.vault(doc);
		Map<String, String> urls = new LinkedHashMap<>();
		for (String line : Headings.lines(src)) {
			for (WikiLinks.Link w : WikiLinks.in(line)) {
				if (w.target().isEmpty() || urls.containsKey(w.target())) continue;
				File f = vault.resolve(doc.getParentFile(), w.target());
				urls.put(w.target(), f == null ? "null" : js(fileUrl(f.toURI())));
			}
		}
		StringBuilder b = new StringBuilder("{");
		urls.forEach((k, v) -> b.append(b.length() > 1 ? "," : "").append(js(k)).append(':').append(v));
		return b.append('}').toString();
	}

	/** The Preview's Backlinks section (see Backlinks), found in the background; none when switched off. */
	private void findBacklinks() {
		File doc = localFile();
		if (!loaded || browser.isDisposed() || doc == null) return;
		if (backlinks != null) backlinks.cancel();
		boolean on = Activator.prefs().getBoolean(PrefInit.BACKLINKS);
		Display display = browser.getDisplay();
		backlinks = Job.create(Messages.backlinks_job, monitor -> {
			StringBuilder b = new StringBuilder("[");
			for (Backlinks.Entry e : on ? Backlinks.of(doc, Activator.vault(doc)) : List.<Backlinks.Entry>of()) {
				b.append(b.length() > 1 ? "," : "").append("{\"name\":").append(js(e.name())).append(",\"url\":").append(js(fileUrl(e.file().toURI()))).append(",\"lines\":[");
				for (int i = 0; i < e.lines().size(); i++) b.append(i > 0 ? "," : "").append(js(e.lines().get(i)));
				b.append("]}");
			}
			if (monitor.isCanceled()) return;
			display.asyncExec(() -> {
				if (!browser.isDisposed()) browser.execute("setBacklinks(" + b + "])");
			});
		});
		backlinks.setSystem(true);
		backlinks.schedule();
	}

	/** A Markdown file of these projects was saved, added or removed: the Previews showing a document in one find their backlinks again. */
	static void backlinksChanged(java.util.Set<File> projects) {
		Display.getDefault().asyncExec(() -> {
			for (MarkdownEditor e : OPEN)
				if (e.previewShowing() && e.getEditorInput() instanceof IFileEditorInput in && in.getFile().getProject().getLocation() != null
						&& projects.contains(in.getFile().getProject().getLocation().toFile())) e.findBacklinks();
		});
	}

	private void showBookmarks() {
		if (loaded && !browser.isDisposed() && source.getDocumentProvider() != null) browser.execute("setBookmarks(" + Bookmarks.json(this) + ")");
	}

	boolean previewShowing() {
		return loaded && getActivePage() == PREVIEW;
	}

	/** The source line (from 0) of the first block that starts in view on the Preview. */
	int previewTopLine() {
		return browser.evaluate("return topLine();") instanceof Number n ? n.intValue() : 0;
	}

	void stepBookmark(boolean next) {
		browser.execute("markStep(" + (next ? 1 : -1) + ")");
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
				+ ",\"rank\":" + s.getInt(PrefInit.DIAGRAM_RANK_SPACING) + ",\"pad\":" + s.getInt(PrefInit.DIAGRAM_PADDING) + "}," + s.getBoolean(PrefInit.PLANTUML) + "," + js(s.getString(PrefInit.LETTER_SPACING)) + ")");
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
		// Problems view double-click: to Source, where the marker shows, then to the marker; a bookmark keeps the Preview if it shows
		if (type == IGotoMarker.class && source != null) return type.cast((IGotoMarker) marker -> {
			if (previewShowing() && marker.getAttribute(IMarker.LINE_NUMBER, 0) > 0 && Bookmarks.isBookmark(marker)) {
				reveal(Bookmarks.line(this, document(), marker));
				return;
			}
			if (getActivePage() == SOURCE) {
				IDE.gotoMarker(source, marker);
				return;
			}
			setActivePage(SOURCE);
			Display.getCurrent().asyncExec(() -> IDE.gotoMarker(source, marker)); // after pageChange's scroll to the preview's position
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
		for (MarkdownEditor e : partners()) e.showLine(line, false); // the other halves of this document show the same line
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

	/**
	 * The pause after the last change before the Preview is brought up to date: 300 ms, and for a large document longer, as the page
	 * needs about 4 ms per KB to render (measured: 2 s for 500 KB) and cannot scroll or answer while it does. A document of
	 * 100 KB waits 470 ms, one of 500 KB 2.5 s.
	 */
	private static int renderDelay(int length) {
		return 300 + Math.max(0, length - 65536) / 200;
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

	// ITextEditor: plugins that cast the active editor to a text editor (bookmarks, Alt+F2 and the like) work on the Source
	@Override
	public IDocumentProvider getDocumentProvider() {
		return source.getDocumentProvider();
	}

	@Override
	public void close(boolean save) {
		getSite().getPage().closeEditor(this, save);
	}

	@Override
	public boolean isEditable() {
		return source.isEditable();
	}

	@Override
	public void doRevertToSaved() {
		source.doRevertToSaved();
	}

	@Override
	public void setAction(String actionID, IAction action) {
		source.setAction(actionID, action);
	}

	@Override
	public IAction getAction(String actionId) {
		return source.getAction(actionId);
	}

	@Override
	public void setActionActivationCode(String actionId, char activationCharacter, int activationKeyCode, int activationStateMask) {
		source.setActionActivationCode(actionId, activationCharacter, activationKeyCode, activationStateMask);
	}

	@Override
	public void removeActionActivationCode(String actionId) {
		source.removeActionActivationCode(actionId);
	}

	@Override
	public boolean showsHighlightRangeOnly() {
		return source.showsHighlightRangeOnly();
	}

	@Override
	public void showHighlightRangeOnly(boolean showHighlightRangeOnly) {
		source.showHighlightRangeOnly(showHighlightRangeOnly);
	}

	@Override
	public void setHighlightRange(int offset, int length, boolean moveCursor) {
		source.setHighlightRange(offset, length, moveCursor);
	}

	@Override
	public IRegion getHighlightRange() {
		return source.getHighlightRange();
	}

	@Override
	public void resetHighlightRange() {
		source.resetHighlightRange();
	}

	@Override
	public ISelectionProvider getSelectionProvider() {
		return source.getSelectionProvider();
	}

	@Override
	public void selectAndReveal(int offset, int length) {
		if (getActivePage() == SOURCE) {
			source.selectAndReveal(offset, length);
			return;
		}
		setActivePage(SOURCE); // a jump to a place in the text shows the text
		Display.getCurrent().asyncExec(() -> source.selectAndReveal(offset, length)); // after pageChange's scroll to the preview's position
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
		findBacklinks();
	}

	@Override
	public boolean isSaveAsAllowed() {
		return true;
	}

	@Override
	public void dispose() {
		Activator.prefs().removePropertyChangeListener(prefListener);
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(markerListener);
		if (getEditorInput() instanceof IFileEditorInput in) LinkCheck.clear(linkCheck, in.getFile()); // only open documents are checked
		else if (linkCheck != null) linkCheck.cancel();
		if (backlinks != null) backlinks.cancel();
		if (stopThemeWatch != null) stopThemeWatch.run();
		OPEN.remove(this);
		if (viewer() instanceof ISourceViewer sv) sv.removeViewportListener(viewportListener);
		if (source != null && source.getDocumentProvider() != null) {
			IDocument d = document();
			if (d != null) d.removeDocumentListener(docListener);
		}
		super.dispose();
		Display.getDefault().asyncExec(MarkdownEditor::refreshSync);
	}
}
