package io.github.insjang.mdviewer.editing;

import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LanguageServiceAccessor;

/**
 * Whether a language server (LSP4E, for example Wild Web Developer's Markdown server) is already running for the document and
 * offers what a feature of this plug-in also offers: that feature then steps aside, so that proposals and links do not show twice.
 * LSP4E is an optional dependency: without it the answer is always no.
 */
final class ServerCheck {
	private ServerCheck() {
	}

	static boolean completion(IDocument doc) {
		try {
			return Servers.completion(doc);
		} catch (LinkageError | RuntimeException e) { // LSP4E is not installed, or does not answer
			return false;
		}
	}

	/** Every reference to LSP4E is in here, so that a missing bundle fails on this class only. */
	private static final class Servers {
		static boolean completion(IDocument doc) {
			return LanguageServiceAccessor.hasActiveLanguageServers(doc, c -> c.getCompletionProvider() != null);
		}
	}
}
