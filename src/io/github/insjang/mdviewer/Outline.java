package io.github.insjang.mdviewer;

import java.util.List;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.views.contentoutline.ContentOutlinePage;

/** Heading tree in the Outline view; a click moves the Source caret and the Preview to the heading. */
final class Outline extends ContentOutlinePage {
	private final MarkdownEditor editor;
	private String src;

	Outline(MarkdownEditor editor, String src) {
		this.editor = editor;
		this.src = src;
	}

	@Override
	public void createControl(Composite parent) {
		super.createControl(parent);
		TreeViewer v = getTreeViewer();
		v.setContentProvider(new ITreeContentProvider() {
			@Override
			public Object[] getElements(Object input) {
				return ((List<?>) input).toArray();
			}

			@Override
			public Object[] getChildren(Object e) {
				return ((Headings.Node) e).children().toArray();
			}

			@Override
			public Object getParent(Object e) {
				return null;
			}

			@Override
			public boolean hasChildren(Object e) {
				return !((Headings.Node) e).children().isEmpty();
			}
		});
		v.setLabelProvider(LabelProvider.createTextProvider(e -> Headings.label(((Headings.Node) e).heading().text())));
		update(src);
	}

	/** New source text: rebuild the tree (all open). */
	void update(String text) {
		src = text;
		TreeViewer v = getTreeViewer();
		if (v == null || v.getControl().isDisposed()) return;
		v.setInput(Headings.tree(Headings.of(text)));
		v.expandAll();
	}

	@Override
	public void selectionChanged(SelectionChangedEvent e) {
		super.selectionChanged(e);
		if (((IStructuredSelection) e.getSelection()).getFirstElement() instanceof Headings.Node n) editor.reveal(n.heading().line());
	}

	@Override
	public void dispose() {
		editor.outlineClosed(this);
		super.dispose();
	}
}
