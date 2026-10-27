package io.github.insjang.mdviewer.editing;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.regex.Pattern;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;

/** Where a Markdown link points to, and following it. */
final class LinkTarget {
	private static final Pattern MARKDOWN_FILE = Pattern.compile("(?i).*\\.(md|markdown|mdown|mkd)");
	private static final Pattern IMAGE_FILE = Pattern.compile("(?i).*\\.(png|jpe?g|gif|bmp|webp|svg)");
	private static final Pattern WEB = Pattern.compile("(?i)(https?|mailto):.*");
	private static final Pattern SCHEME = Pattern.compile("(?i)[a-z][a-z0-9+.-]+:.*");

	/** The local file the document is the buffer of, when it is a Markdown file; the link features are for those only. */
	static File markdownFile(IDocument doc) {
		ITextFileBuffer buffer = doc == null ? null : FileBuffers.getTextFileBufferManager().getTextFileBuffer(doc);
		IFileStore store = buffer == null ? null : buffer.getFileStore();
		if (store == null || !MARKDOWN_FILE.matcher(store.getName()).matches()) return null;
		try {
			return store.toLocalFile(EFS.NONE, null);
		} catch (Exception e) {
			return null;
		}
	}

	static boolean isMarkdown(File f) {
		return MARKDOWN_FILE.matcher(f.getName()).matches();
	}

	/** Destination text as written ({@code %20} for a space) to the plain path. */
	static String decode(String path) {
		try {
			return URLDecoder.decode(path.replace("+", "%2B"), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			return path; // a stray % is just a character
		}
	}

	/** A file name as it has to be written in a destination. */
	static String encode(String name) {
		StringBuilder sb = new StringBuilder();
		for (char c : name.toCharArray()) {
			if (" %#()".indexOf(c) >= 0) sb.append('%').append(String.format("%02X", (int) c));
			else sb.append(c);
		}
		return sb.toString();
	}

	/** The project folder of a file in the workspace, which a destination starting with / is relative to. */
	private static File projectRoot(File doc) {
		IFile[] files = ResourcesPlugin.getWorkspace().getRoot().findFilesForLocationURI(doc.toURI());
		return files.length == 0 || files[0].getProject().getLocation() == null ? null : files[0].getProject().getLocation().toFile();
	}

	/** The file or folder a path (without fragment, still encoded) names, seen from the document; null if it has no local meaning. */
	static File resolve(File doc, String path) {
		String p = decode(path);
		if (p.startsWith("/")) {
			File root = projectRoot(doc);
			return root == null ? null : new File(root, p.substring(1));
		}
		return new File(doc.getParentFile(), p);
	}

	static String read(File f) {
		try {
			return Files.readString(f.toPath());
		} catch (IOException | RuntimeException e) {
			return "";
		}
	}

	/** Selects a line start in the viewer and brings it into view. */
	private static void goTo(ITextViewer viewer, IDocument doc, int line) {
		try {
			int offset = doc.getLineOffset(line);
			viewer.setSelectedRange(offset, 0);
			viewer.revealRange(offset, 0);
		} catch (BadLocationException e) {
			// the document changed meanwhile: stay
		}
	}

	/** Follows a link of the document shown in the viewer. Web addresses go to the browser, files into editors, #anchors to the heading. */
	static void follow(ITextViewer viewer, File doc, MarkdownLinks.Outline outline, String target) {
		if (target.startsWith("#")) {
			MarkdownLinks.Heading h = outline.heading(decode(target.substring(1)).toLowerCase(Locale.ROOT));
			if (h != null) goTo(viewer, viewer.getDocument(), h.line());
		} else if (WEB.matcher(target).matches()) {
			Program.launch(target); // only these schemes: anything else could name an executable
		} else if (!SCHEME.matcher(target).matches()) {
			int hash = target.indexOf('#');
			File f = resolve(doc, hash < 0 ? target : target.substring(0, hash));
			String fragment = hash < 0 ? null : decode(target.substring(hash + 1)).toLowerCase(Locale.ROOT);
			if (f != null && f.isFile()) {
				Display display = viewer.getTextWidget().getDisplay();
				// after this event is handled: a new editor opening inside it (its Edge browser) hangs WebView2
				display.asyncExec(() -> open(f, fragment));
			}
		}
	}

	private static void open(File f, String fragment) {
		try {
			IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
			if (IMAGE_FILE.matcher(f.getName()).matches()) {
				Program.launch(f.getAbsolutePath()); // a picture: the viewer the system has for it
				return;
			}
			IContentType type = Platform.getContentTypeManager().findContentTypeFor(f.getName());
			boolean text = type != null && type.isKindOf(Platform.getContentTypeManager().getContentType("org.eclipse.core.runtime.text")); //$NON-NLS-1$
			if (!text && !isMarkdown(f)) return; // never hand other files to the system (executables, scripts)
			// a file of the workspace opens as the IFile, so that it is the editor (and the buffer) that is already open for it
			IFile[] inWorkspace = ResourcesPlugin.getWorkspace().getRoot().findFilesForLocationURI(f.toPath().normalize().toUri());
			IEditorPart part = inWorkspace.length > 0 ? IDE.openEditor(page, inWorkspace[0]) : IDE.openEditorOnFileStore(page, EFS.getLocalFileSystem().getStore(f.toURI()));
			ITextEditor editor = part instanceof ITextEditor te ? te : part == null ? null : part.getAdapter(ITextEditor.class);
			if (editor == null || fragment == null) return;
			MarkdownLinks.Heading h = MarkdownLinks.outline(read(f)).heading(fragment);
			if (h != null) {
				IDocument d = editor.getDocumentProvider().getDocument(editor.getEditorInput());
				editor.selectAndReveal(d.getLineOffset(h.line()), 0);
			}
		} catch (Exception e) {
			// not openable: nothing happens, like a link to nowhere
		}
	}
}
