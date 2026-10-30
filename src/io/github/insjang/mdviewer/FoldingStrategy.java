package io.github.insjang.mdviewer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension4;
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
import org.eclipse.swt.custom.StyledText;

import io.github.insjang.mdviewer.editing.ServerCheck;

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

	/** The reconciler thread works out where the folds are; the UI thread only brings the annotation model in line with that. */
	private void update() {
		ProjectionViewer v = viewer;
		IDocument doc = document;
		StyledText widget = v == null ? null : v.getTextWidget();
		if (doc == null || widget == null || widget.isDisposed()) return;
		long stamp = stamp(doc);
		Map<Integer, Position> wanted;
		try {
			wanted = ServerCheck.registered(doc) ? new HashMap<>() : positions(doc.get()); // a language server brings its own folds: ours would show beside them
		} catch (RuntimeException e) { // the text changed under us: the next reconcile follows
			return;
		}
		widget.getDisplay().asyncExec(() -> {
			if (widget.isDisposed() || v.getDocument() != doc || stamp(doc) != stamp) return; // edited meanwhile: the next reconcile follows
			ProjectionAnnotationModel model = v.getProjectionAnnotationModel();
			if (model != null) apply(model, doc, wanted);
		});
	}

	private static long stamp(IDocument doc) {
		return doc instanceof IDocumentExtension4 d ? d.getModificationStamp() : -1;
	}

	/** The folds of the text as positions by start line. */
	private static Map<Integer, Position> positions(String text) {
		List<Integer> starts = new ArrayList<>(); // where each line starts, as FoldBlocks cuts the lines
		starts.add(0);
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
			if (c == '\r' || c == '\n') starts.add(i + 1);
		}
		Map<Integer, Position> result = new HashMap<>();
		for (FoldBlocks.Fold f : FoldBlocks.parse(text).folds) {
			int start = starts.get(f.startLine()), end = f.endLine() + 1 < starts.size() ? starts.get(f.endLine() + 1) : text.length();
			result.putIfAbsent(f.startLine(), new Position(start, end - start));
		}
		return result;
	}

	private static void apply(ProjectionAnnotationModel model, IDocument doc, Map<Integer, Position> wanted) {
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
