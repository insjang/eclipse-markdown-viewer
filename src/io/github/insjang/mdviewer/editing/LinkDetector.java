package io.github.insjang.mdviewer.editing;

import java.io.File;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.IHyperlink;

import io.github.insjang.mdviewer.Activator;
import io.github.insjang.mdviewer.Headings;
import io.github.insjang.mdviewer.Messages;

/** Ctrl+click (the editor's hyperlink key) on a link in the Source of a Markdown file: inline, reference, autolinks, bare addresses, wikilinks. */
public class LinkDetector extends AbstractHyperlinkDetector {
	@Override
	public IHyperlink[] detectHyperlinks(ITextViewer viewer, IRegion region, boolean canShowMultipleHyperlinks) {
		IDocument doc = viewer.getDocument();
		File file = LinkTarget.markdownFile(doc);
		if (file == null) return null;
		try {
			int line = doc.getLineOfOffset(region.getOffset());
			int base = doc.getLineOffset(line);
			String text = doc.get(base, doc.getLineLength(line));
			if (!MarkdownLinks.mayHaveLink(text)) return null; // this runs on every mouse move: the document is only parsed for a line with a link
			if (ServerCheck.documentLinks(doc) && !text.contains("[[")) return null; // a language server shows the other links itself: not twice
			MarkdownLinks.Outline outline = MarkdownLinks.outline(doc.get());
			if (outline.isCode(line)) return null;
			// the Markdown language servers of Eclipse do not show wikilinks: these always come from here
			WikiLinks.Link w = WikiLinks.at(text, region.getOffset() - base);
			if (w != null) {
				String anchor = w.heading() == null ? null : Headings.slug(w.heading());
				Region r = new Region(base + w.start(), w.end() - w.start());
				if (w.target().isEmpty()) return new IHyperlink[] { new Hyperlink(viewer, file, outline, r, "#" + anchor, -1, null) };
				File to = Activator.vault(file).resolve(file.getParentFile(), w.target());
				return to == null ? null : new IHyperlink[] { new Hyperlink(viewer, file, outline, r, anchor, -1, to) };
			}
			if (ServerCheck.documentLinks(doc)) return null; // a language server shows the other links itself: not twice
			MarkdownLinks.Link link = MarkdownLinks.at(text, region.getOffset() - base, outline);
			if (link == null) return null;
			String target = link.target();
			int jumpLine = -1;
			if (link.reference()) {
				MarkdownLinks.Definition def = outline.definition(target);
				if (def == null) return null;
				target = def.destination();
				jumpLine = def.line();
			}
			return new IHyperlink[] { new Hyperlink(viewer, file, outline, new Region(base + link.start(), link.end() - link.start()), target, jumpLine, null) };
		} catch (BadLocationException e) {
			return null;
		}
	}

	/** One link. A reference link goes to its definition, which in turn is a link itself; a wikilink to its resolved file (target: the anchor or null). */
	private static final class Hyperlink implements IHyperlink {
		private final ITextViewer viewer;
		private final File file;
		private final MarkdownLinks.Outline outline;
		private final IRegion region;
		private final String target;
		private final int definitionLine;
		private final File wiki;

		Hyperlink(ITextViewer viewer, File file, MarkdownLinks.Outline outline, IRegion region, String target, int definitionLine, File wiki) {
			this.viewer = viewer;
			this.file = file;
			this.outline = outline;
			this.region = region;
			this.target = target;
			this.definitionLine = definitionLine;
			this.wiki = wiki;
		}

		@Override
		public IRegion getHyperlinkRegion() {
			return region;
		}

		@Override
		public String getTypeLabel() {
			return null;
		}

		@Override
		public String getHyperlinkText() {
			return definitionLine >= 0 ? Messages.source_goToDefinition : wiki != null ? wiki.getName() : target;
		}

		@Override
		public void open() {
			if (wiki != null) {
				LinkTarget.follow(viewer, wiki, target);
				return;
			} else if (definitionLine < 0) {
				LinkTarget.follow(viewer, file, outline, target);
				return;
			}
			try {
				int offset = viewer.getDocument().getLineOffset(definitionLine);
				viewer.setSelectedRange(offset, 0);
				viewer.revealRange(offset, 0);
			} catch (BadLocationException e) {
				// the document changed meanwhile: stay
			}
		}
	}
}
