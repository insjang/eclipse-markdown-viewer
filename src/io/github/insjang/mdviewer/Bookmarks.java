package io.github.insjang.mdviewer;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.Position;
import org.eclipse.core.expressions.EvaluationResult;
import org.eclipse.core.expressions.Expression;
import org.eclipse.core.expressions.ExpressionInfo;
import org.eclipse.core.expressions.IEvaluationContext;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ISources;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.contexts.IContextActivation;
import org.eclipse.ui.contexts.IContextService;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.AbstractMarkerAnnotationModel;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * Bookmarks in any text editor, the Markdown Viewer too: toggle on the current line, next and previous in the file,
 * and quick bookmarks 0 to 9 that jump across files. They are Eclipse's own bookmarks, so the Bookmarks view lists them.
 * Optional (off by default): while off, the context of the keys is not active, so F2 and Shift+F2 do what Eclipse made them do.
 */
public class Bookmarks extends AbstractHandler implements IStartup {
	private static final String QUICK = "io.github.insjang.mdviewer.quickBookmark"; //$NON-NLS-1$
	private static final String CONTEXT = "io.github.insjang.mdviewer.bookmarks"; //$NON-NLS-1$
	private static IContextActivation activation;

	/** The keys' context: on with the preference, and then only while the active part is a text editor. */
	@Override
	public void earlyStartup() {
		Display display = PlatformUI.getWorkbench().getDisplay();
		display.asyncExec(Bookmarks::keys);
		Activator.prefs().addPropertyChangeListener(e -> {
			if (PrefInit.BOOKMARKS.equals(e.getProperty())) display.asyncExec(Bookmarks::keys);
		});
	}

	private static void keys() {
		IContextService contexts = PlatformUI.getWorkbench().getService(IContextService.class);
		boolean on = Activator.prefs().getBoolean(PrefInit.BOOKMARKS);
		if (on && activation == null) activation = contexts.activateContext(CONTEXT, new Expression() {
			@Override
			public EvaluationResult evaluate(IEvaluationContext context) {
				return EvaluationResult.valueOf(context.getVariable(ISources.ACTIVE_PART_NAME) instanceof IEditorPart e && Adapters.adapt(e, ITextEditor.class) != null);
			}

			@Override
			public void collectExpressionInfo(ExpressionInfo info) {
				info.addVariableNameAccess(ISources.ACTIVE_PART_NAME);
			}
		});
		if (!on && activation != null) {
			contexts.deactivateContext(activation);
			activation = null;
		}
	}

	@Override
	public boolean isEnabled() {
		return Activator.prefs().getBoolean(PrefInit.BOOKMARKS);
	}

	@Override
	public Object execute(ExecutionEvent event) {
		IEditorPart part = HandlerUtil.getActiveEditor(event);
		ITextEditor editor = part == null ? null : Adapters.adapt(part, ITextEditor.class);
		String id = event.getCommand().getId();
		try {
			if (id.endsWith(".goto")) { //$NON-NLS-1$
				IMarker m = quick(event.getParameter("number")); //$NON-NLS-1$
				if (m != null) IDE.openEditor(HandlerUtil.getActiveWorkbenchWindow(event).getActivePage(), m);
				return null;
			}
			IResource file = editor == null ? null : editor.getEditorInput().getAdapter(IResource.class);
			if (file == null) {
				if (part != null) part.getEditorSite().getActionBars().getStatusLineManager().setMessage(Messages.bookmark_notWorkspace);
				return null;
			}
			IDocument doc = editor.getDocumentProvider().getDocument(editor.getEditorInput());
			MarkdownEditor preview = part instanceof MarkdownEditor m && m.previewShowing() ? m : null; // the same keys on the Preview
			int line = preview != null ? preview.previewTopLine() : doc.getLineOfOffset(((ITextSelection) editor.getSelectionProvider().getSelection()).getOffset());
			IMarker[] marks = Arrays.stream(file.findMarkers(IMarker.BOOKMARK, true, IResource.DEPTH_ZERO))
					.sorted(Comparator.comparingInt(m -> line(editor, doc, m))).toArray(IMarker[]::new);
			IMarker here = Arrays.stream(marks).filter(m -> line(editor, doc, m) == line).findFirst().orElse(null);
			if (id.endsWith(".toggle")) { //$NON-NLS-1$
				if (here != null) here.delete();
				else add(file, doc, line, null);
			} else if (id.endsWith(".set")) { //$NON-NLS-1$
				String n = event.getParameter("number"); //$NON-NLS-1$
				IMarker old = quick(n);
				if (old != null && old != here) old.delete();
				if (here != null) here.delete(); // one bookmark a line: the quick one takes its place
				add(file, doc, line, n);
			} else if (preview != null) {
				preview.stepBookmark(id.endsWith(".next")); //$NON-NLS-1$
			} else if (marks.length > 0) { // next, previous: around the file
				boolean next = id.endsWith(".next"); //$NON-NLS-1$
				IMarker target = next ? marks[0] : marks[marks.length - 1];
				for (int i = 0; i < marks.length; i++) {
					IMarker m = marks[next ? i : marks.length - 1 - i];
					int l = line(editor, doc, m);
					if (next ? l > line : l < line) {
						target = m;
						break;
					}
				}
				IDE.gotoMarker(part, target);
			}
		} catch (CoreException | BadLocationException e) {
			ILog.of(Bookmarks.class).error("bookmark", e); //$NON-NLS-1$
		}
		return null;
	}

	private static void add(IResource file, IDocument doc, int line, String quick) throws CoreException, BadLocationException {
		String text = doc.get(doc.getLineOffset(line), doc.getLineLength(line)).strip();
		Map<String, Object> a = new HashMap<>();
		a.put(IMarker.LINE_NUMBER, line + 1);
		a.put(IMarker.MESSAGE, quick == null ? text : "[" + quick + "] " + text); //$NON-NLS-1$ //$NON-NLS-2$
		if (quick != null) a.put(QUICK, quick);
		file.createMarker(IMarker.BOOKMARK, a);
	}

	/** The quick bookmark with this number, in any file of the workspace. */
	private static IMarker quick(String n) throws CoreException {
		for (IMarker m : ResourcesPlugin.getWorkspace().getRoot().findMarkers(IMarker.BOOKMARK, true, IResource.DEPTH_INFINITE)) {
			if (n != null && n.equals(m.getAttribute(QUICK, null))) return m;
		}
		return null;
	}

	static boolean isBookmark(IMarker m) {
		try {
			return m.isSubtypeOf(IMarker.BOOKMARK);
		} catch (CoreException e) {
			return false;
		}
	}

	/** The bookmarks of the editor's file for the Preview, as [{line, quick}] (lines from 0); none while the feature is off. */
	static String json(ITextEditor editor) {
		IResource file = editor.getEditorInput().getAdapter(IResource.class);
		if (file == null || !Activator.prefs().getBoolean(PrefInit.BOOKMARKS)) return "[]"; //$NON-NLS-1$
		try {
			IDocument doc = editor.getDocumentProvider().getDocument(editor.getEditorInput());
			StringBuilder b = new StringBuilder("["); //$NON-NLS-1$
			for (IMarker m : file.findMarkers(IMarker.BOOKMARK, true, IResource.DEPTH_ZERO)) {
				if (b.length() > 1) b.append(',');
				Object q = m.getAttribute(QUICK);
				b.append("{\"line\":").append(line(editor, doc, m)).append(q == null ? "" : ",\"quick\":\"" + q + "\"").append('}'); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}
			return b.append(']').toString();
		} catch (CoreException e) {
			return "[]"; //$NON-NLS-1$
		}
	}

	/** The line (from 0) where the marker is now: the open editor moves it as the text changes, the file keeps the saved line. */
	static int line(ITextEditor editor, IDocument doc, IMarker m) {
		if (editor.getDocumentProvider().getAnnotationModel(editor.getEditorInput()) instanceof AbstractMarkerAnnotationModel model) {
			Position p = model.getMarkerPosition(m);
			if (p != null && !p.isDeleted()) {
				try {
					return doc.getLineOfOffset(p.getOffset());
				} catch (BadLocationException e) {
					// fall through to the saved line
				}
			}
		}
		return m.getAttribute(IMarker.LINE_NUMBER, 0) - 1;
	}
}
