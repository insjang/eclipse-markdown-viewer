package io.github.insjang.mdviewer;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewerExtension5;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.ScrollBar;

/**
 * The editor's scroll position as a (fractional) source line, and moving it to one. Folded lines and wrapped
 * lines are taken into account; what the Preview needs is "which line is at the top" in document terms.
 */
final class SourceScroll {
	private final ISourceViewer viewer;
	/** The top pixel after our own last move; for a moment, the scroll events at that pixel are ours, not the user's. */
	private int own = -1000; // no top pixel is negative
	private long ownUntil;

	SourceScroll(ISourceViewer viewer) {
		this.viewer = viewer;
	}

	private StyledText text() {
		return viewer.getTextWidget();
	}

	boolean isOwnMove() {
		StyledText t = text();
		return t != null && System.currentTimeMillis() < ownUntil && Math.abs(t.getTopPixel() - own) < 2;
	}

	/** The line at the top, plus the part of it that is scrolled out of view as a fraction (0 to 1). */
	double topLine() {
		StyledText t = text();
		int w = t.getTopIndex();
		return modelLine(w) + fraction(t, w, -t.getLinePixel(w));
	}

	/** The top line the editor has when it is scrolled all the way down. */
	double endLine() {
		StyledText t = text();
		int rel = maxTop(t) - t.getTopPixel();
		int w = Math.max(0, Math.min(t.getLineCount() - 1, t.getLineIndex(rel)));
		return modelLine(w) + fraction(t, w, rel - t.getLinePixel(w));
	}

	int visibleLines() {
		StyledText t = text();
		return Math.max(1, t.getClientArea().height / Math.max(1, t.getLineHeight()));
	}

	int lineCount() {
		IDocument d = viewer.getDocument();
		return d == null ? 0 : d.getNumberOfLines();
	}

	/** Scrolls so that the (fractional) line is at the top, or all the way down. */
	void scrollTo(double line, boolean end) {
		StyledText t = text();
		int max = maxTop(t);
		int target = max;
		if (!end) {
			int whole = (int) Math.floor(line);
			int w = widgetLine(whole);
			double frac = Math.max(0, Math.min(1, line - whole));
			target = t.getTopPixel() + t.getLinePixel(w) + (int) Math.round(frac * span(t, w));
		}
		target = Math.max(0, Math.min(max, target));
		own = target; // the scroll event can arrive before setTopPixel returns
		ownUntil = System.currentTimeMillis() + 300;
		t.setTopPixel(target);
		own = t.getTopPixel();
	}

	private static int maxTop(StyledText t) {
		ScrollBar bar = t.getVerticalBar();
		return bar == null ? 0 : Math.max(0, bar.getMaximum() - bar.getThumb());
	}

	/** Height in pixels of the widget line (all its wrapped parts). */
	private static int span(StyledText t, int widgetLine) {
		int h = widgetLine + 1 < t.getLineCount() ? t.getLinePixel(widgetLine + 1) - t.getLinePixel(widgetLine)
				: t.getLineHeight(t.getOffsetAtLine(widgetLine));
		return Math.max(1, h);
	}

	private static double fraction(StyledText t, int widgetLine, int pixelsIntoLine) {
		return Math.max(0, Math.min(1, pixelsIntoLine / (double) span(t, widgetLine)));
	}

	/** Document line of a widget line; they differ when lines are folded away. */
	private int modelLine(int widgetLine) {
		if (viewer instanceof ITextViewerExtension5 e) return Math.max(0, e.widgetLine2ModelLine(widgetLine));
		return widgetLine;
	}

	/** Widget line of a document line; a folded line is represented by the next one that is shown. */
	private int widgetLine(int modelLine) {
		IDocument d = viewer.getDocument();
		int last = Math.max(0, (d == null ? 1 : d.getNumberOfLines()) - 1);
		int line = Math.max(0, Math.min(last, modelLine));
		if (viewer instanceof ITextViewerExtension5 e) {
			for (int l = line; l <= last; l++) {
				int w = e.modelLine2WidgetLine(l);
				if (w >= 0) return w;
			}
			return Math.max(0, text().getLineCount() - 1);
		}
		return Math.min(line, Math.max(0, text().getLineCount() - 1));
	}
}
