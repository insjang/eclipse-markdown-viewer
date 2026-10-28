package io.github.insjang.mdviewer.editing;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;

/**
 * Content assist for Markdown: languages after the opening of a fenced code block, files and folders and #anchors inside
 * {@code ](...)} and after {@code [label]:}, labels inside {@code ][...}.
 */
public class LinkAssist implements IContentAssistProcessor {
	/** What the preview can highlight, plus the diagram languages it draws. */
	private static final String[] LANGUAGES = { "bash", "batch", "c", "cpp", "csharp", "css", "diff", "dockerfile", "go", "gradle", "groovy", "html", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$ //$NON-NLS-12$
			"ini", "java", "javascript", "json", "kotlin", "makefile", "markdown", "mermaid", "php", "plantuml", "powershell", "properties", "python", "ruby", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$ //$NON-NLS-12$ //$NON-NLS-13$ //$NON-NLS-14$
			"rust", "scss", "shell", "sql", "swift", "text", "typescript", "xml", "yaml" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$

	private static final Pattern FENCE_OPENING = Pattern.compile("^ {0,3}(?:`{3,}|~{3,})([^\\s`]*)$");
	private static final Pattern DEFINITION_DESTINATION = Pattern.compile("^ {0,3}\\[[^\\]]+\\]:[ \\t]*<?([^\\s>]*)$");
	private static final Pattern INLINE_DESTINATION = Pattern.compile("\\]\\(\\s*<?([^\\s)>]*)$");
	private static final Pattern REFERENCE_LABEL = Pattern.compile("\\]\\[([^\\]]*)$");
	private static final Pattern SCHEME = Pattern.compile("(?i)[a-z][a-z0-9+.-]+:.*");

	@Override
	public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
		IDocument doc = viewer.getDocument();
		File file = LinkTarget.markdownFile(doc);
		if (file == null) return null;
		try {
			int line = doc.getLineOfOffset(offset);
			int base = doc.getLineOffset(line);
			if (doc.getLineLength(line) > MarkdownLinks.MAX_LINE) return null;
			String before = doc.get(base, offset - base);
			MarkdownLinks.Outline outline = MarkdownLinks.outline(doc.get());
			List<ICompletionProposal> result = new ArrayList<>();
			if (outline.isOpeningFence(line)) {
				Matcher m = FENCE_OPENING.matcher(before);
				if (m.matches()) languages(m.group(1), offset, result);
			} else if (!outline.isCode(line) && !ServerCheck.completion(doc)) { // a language server offers these itself: not twice
				Matcher m = INLINE_DESTINATION.matcher(before);
				if (m.find() || (m = DEFINITION_DESTINATION.matcher(before)).find()) {
					destinations(m.group(1), offset, file, outline, result);
				} else if ((m = REFERENCE_LABEL.matcher(before)).find()) {
					labels(m.group(1), offset, outline, result);
				}
			}
			return result.isEmpty() ? null : result.toArray(new ICompletionProposal[0]);
		} catch (BadLocationException e) {
			return null;
		}
	}

	private static boolean startsWith(String s, String prefix) {
		return s.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT));
	}

	private static void languages(String prefix, int offset, List<ICompletionProposal> result) {
		for (String lang : LANGUAGES) {
			if (startsWith(lang, prefix)) result.add(new CompletionProposal(lang, offset - prefix.length(), prefix.length(), lang.length(), null, lang, null, null));
		}
	}

	private static void labels(String prefix, int offset, MarkdownLinks.Outline outline, List<ICompletionProposal> result) {
		outline.definitions.keySet().stream().filter(label -> startsWith(label, prefix)).sorted().forEach(label -> result
				.add(new CompletionProposal(label, offset - prefix.length(), prefix.length(), label.length(), null, label, null, null)));
	}

	private static void anchors(String prefix, int offset, MarkdownLinks.Outline outline, List<ICompletionProposal> result) {
		for (MarkdownLinks.Heading h : outline.headings) {
			if (!startsWith(h.anchor(), prefix)) continue;
			String shown = "#" + h.anchor() + (h.text().isEmpty() ? "" : "  –  " + h.text()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			result.add(new CompletionProposal(h.anchor(), offset - prefix.length(), prefix.length(), h.anchor().length(), null, shown, null, null));
		}
	}

	private static void destinations(String prefix, int offset, File doc, MarkdownLinks.Outline outline, List<ICompletionProposal> result) {
		if (prefix.startsWith("#")) { //$NON-NLS-1$
			anchors(prefix.substring(1), offset, outline, result);
			return;
		}
		if (SCHEME.matcher(prefix).matches()) return;
		int hash = prefix.indexOf('#');
		if (hash >= 0) {
			File f = LinkTarget.resolve(doc, prefix.substring(0, hash));
			if (f != null && f.isFile() && LinkTarget.isMarkdown(f)) anchors(prefix.substring(hash + 1), offset, MarkdownLinks.outline(LinkTarget.read(f)), result);
			return;
		}
		int slash = prefix.lastIndexOf('/') + 1;
		String typed = prefix.substring(slash);
		File dir = LinkTarget.resolve(doc, slash == 0 ? "" : prefix.substring(0, slash)); //$NON-NLS-1$
		File[] children = dir == null ? null : dir.listFiles();
		if (children == null) return;
		String wanted = LinkTarget.decode(typed);
		if (wanted.isEmpty() && dir.getParentFile() != null && !prefix.startsWith("/")) { //$NON-NLS-1$
			result.add(new CompletionProposal("../", offset, 0, 3, null, "../", null, null)); //$NON-NLS-1$ //$NON-NLS-2$
		}
		Arrays.sort(children, Comparator.comparing((File c) -> !c.isDirectory()).thenComparing(c -> c.getName().toLowerCase(Locale.ROOT)));
		for (File c : children) {
			String name = c.getName();
			if ((name.startsWith(".") && !wanted.startsWith(".")) || !startsWith(name, wanted)) continue; //$NON-NLS-1$ //$NON-NLS-2$
			String text = LinkTarget.encode(name) + (c.isDirectory() ? "/" : ""); //$NON-NLS-1$ //$NON-NLS-2$
			result.add(new CompletionProposal(text, offset - typed.length(), typed.length(), text.length(), null, c.isDirectory() ? name + "/" : name, null, null)); //$NON-NLS-1$
		}
	}

	@Override
	public IContextInformation[] computeContextInformation(ITextViewer viewer, int offset) {
		return null;
	}

	@Override
	public char[] getCompletionProposalAutoActivationCharacters() {
		return new char[] { '(', '/', '#', '[', '`', '~' };
	}

	@Override
	public char[] getContextInformationAutoActivationCharacters() {
		return null;
	}

	@Override
	public String getErrorMessage() {
		return null;
	}

	@Override
	public IContextInformationValidator getContextInformationValidator() {
		return null;
	}
}
