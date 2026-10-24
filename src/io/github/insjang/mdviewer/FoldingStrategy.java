package io.github.insjang.mdviewer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITextViewerLifecycle;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.reconciler.DirtyRegion;
import org.eclipse.jface.text.reconciler.IReconcilingStrategy;
import org.eclipse.jface.text.reconciler.IReconcilingStrategyExtension;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.projection.ProjectionAnnotation;
import org.eclipse.jface.text.source.projection.ProjectionAnnotationModel;
import org.eclipse.jface.text.source.projection.ProjectionViewer;
import org.eclipse.lsp4e.LanguageServiceAccessor;

/**
 * Folding for Markdown in the Generic Editor: heading sections, fenced code, front matter, HTML comments, tables, block quotes and
 * list items with something under them. Blocks that stay in place keep their folded state while the text is edited.
 */
public class FoldingStrategy implements IReconcilingStrategy, IReconcilingStrategyExtension, ITextViewerLifecycle {
	/** Ours, so that the folds of other strategies are left alone. */
	private static final class MarkdownFold extends ProjectionAnnotation {
	}

	private ProjectionViewer viewer;
	private IDocument document;

	@Override
	public void install(ITextViewer textViewer) {
		viewer = textViewer instanceof ProjectionViewer pv ? pv : null;
	}

	@Override
	public void uninstall() {
		viewer = null;
	}

	@Override
	public void setDocument(IDocument doc) {
		document = doc;
	}

	@Override
	public void setProgressMonitor(IProgressMonitor monitor) {
		// quick enough not to need it
	}

	@Override
	public void initialReconcile() {
		update();
	}

	@Override
	public void reconcile(IRegion partition) {
		update();
	}

	@Override
	public void reconcile(DirtyRegion dirtyRegion, IRegion subRegion) {
		update();
	}

	private void update() {
		ProjectionViewer v = viewer;
		IDocument doc = document;
		if (v == null || doc == null || v.getTextWidget() == null || v.getTextWidget().isDisposed()) return;
		// in the UI thread: the text and the annotation model are used there
		v.getTextWidget().getDisplay().asyncExec(() -> {
			if (v.getTextWidget().isDisposed() || v.getDocument() != doc) return;
			ProjectionAnnotationModel model = v.getProjectionAnnotationModel();
			if (model != null) apply(model, doc);
		});
	}

	/** The folds of the text as positions by start line. */
	private static Map<Integer, Position> positions(IDocument doc) {
		Map<Integer, Position> result = new HashMap<>();
		try {
			for (FoldBlocks.Fold f : FoldBlocks.parse(doc.get()).folds) {
				int start = doc.getLineOffset(f.startLine());
				int end = doc.getLineOffset(f.endLine()) + doc.getLineLength(f.endLine());
				result.putIfAbsent(f.startLine(), new Position(start, end - start));
			}
		} catch (BadLocationException e) {
			// text changed meanwhile: the next reconcile follows
		}
		return result;
	}

	/** A language server (LSP4E) that already folds this document: ours would show beside its folds, so ours are taken away. LSP4E is optional. */
	private static boolean serverFolds(IDocument doc) {
		try {
			return Servers.folds(doc);
		} catch (LinkageError | RuntimeException e) {
			return false;
		}
	}

	/** Every reference to LSP4E is in here, so that a missing bundle fails on this class only. */
	private static final class Servers {
		static boolean folds(IDocument doc) {
			return LanguageServiceAccessor.hasActiveLanguageServers(doc, c -> c.getFoldingRangeProvider() != null);
		}
	}

	private static void apply(ProjectionAnnotationModel model, IDocument doc) {
		Map<Integer, Position> wanted = serverFolds(doc) ? new HashMap<>() : positions(doc);
		List<Annotation> deletions = new ArrayList<>();
		Map<Annotation, Position> modifications = new HashMap<>();
		Map<Integer, Boolean> kept = new HashMap<>();
		for (Iterator<Annotation> it = model.getAnnotationIterator(); it.hasNext();) {
			Annotation a = it.next();
			if (!(a instanceof MarkdownFold)) continue;
			Position old = model.getPosition(a);
			try {
				int line = old == null ? -1 : doc.getLineOfOffset(old.offset);
				Position now = wanted.get(line);
				if (now == null || kept.containsKey(line)) {
					deletions.add(a);
				} else {
					kept.put(line, true);
					if (now.offset != old.offset || now.length != old.length) modifications.put(a, now); // typing at the start of the line moved it
				}
			} catch (BadLocationException e) {
				deletions.add(a);
			}
		}
		Map<Annotation, Position> additions = new HashMap<>();
		for (Map.Entry<Integer, Position> e : wanted.entrySet()) {
			if (!kept.containsKey(e.getKey())) additions.put(new MarkdownFold(), e.getValue());
		}
		for (Map.Entry<Annotation, Position> m : modifications.entrySet()) { // the new extent of a block that stayed
			model.modifyAnnotationPosition(m.getKey(), m.getValue());
		}
		if (!deletions.isEmpty() || !additions.isEmpty()) model.modifyAnnotations(deletions.toArray(new Annotation[0]), additions, null);
	}
}
