package io.github.insjang.mdviewer.editing;

import java.net.URI;
import java.util.Collection;

import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.ContentTypeToLanguageServerDefinition;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4e.LanguageServiceAccessor;

/**
 * Whether a language server (LSP4E, for example Wild Web Developer's Markdown server) is already running for the document and
 * offers what a feature of this plug-in also offers: that feature then steps aside, so that proposals and links do not show twice.
 * Nothing is asked of a server unless one is set up for this kind of document, so that the check costs nothing without one.
 * LSP4E is an optional dependency: without it the answer is always no.
 */
public final class ServerCheck {
	private ServerCheck() {
	}

	/**
	 * A language server is set up for this kind of document: it brings its own folds (ours would show beside them) and, when it runs, its
	 * own completion and links. Decided by what is registered, not by what is running, so it holds while the server is still starting and
	 * never waits for it.
	 */
	public static boolean registered(IDocument doc) {
		try {
			return Servers.registered(doc);
		} catch (LinkageError | RuntimeException e) { // LSP4E is not installed, or does not answer
			return false;
		}
	}

	static boolean completion(IDocument doc) {
		try {
			return Servers.completion(doc);
		} catch (LinkageError | RuntimeException e) { // LSP4E is not installed, or does not answer
			return false;
		}
	}

	/** Ctrl+click: LSP4E's hyperlink detectors answer for a server with links in the document, and would show beside ours. */
	static boolean documentLinks(IDocument doc) {
		try {
			return Servers.documentLinks(doc);
		} catch (LinkageError | RuntimeException e) {
			return false;
		}
	}

	/** Every reference to LSP4E is in here, so that a missing bundle fails on this class only. */
	private static final class Servers {
		static boolean registered(IDocument doc) {
			Collection<IContentType> types = LSPEclipseUtils.getDocumentContentTypes(doc);
			URI uri = LSPEclipseUtils.toUri(doc);
			for (ContentTypeToLanguageServerDefinition d : LanguageServersRegistry.getInstance().getContentTypeToLSPExtensions()) {
				for (IContentType type : types) {
					if (type.isKindOf(d.getKey()) && d.isEnabled(uri)) return true;
				}
			}
			return false;
		}

		static boolean completion(IDocument doc) {
			return registered(doc) && LanguageServiceAccessor.hasActiveLanguageServers(doc, c -> c.getCompletionProvider() != null);
		}

		static boolean documentLinks(IDocument doc) {
			return registered(doc) && LanguageServiceAccessor.hasActiveLanguageServers(doc, c -> c.getDocumentLinkProvider() != null);
		}
	}
}
