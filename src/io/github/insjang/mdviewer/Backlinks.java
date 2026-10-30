package io.github.insjang.mdviewer;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.insjang.mdviewer.editing.WikiLinks;

/**
 * The other Markdown files of a vault that link to a document, by wikilink (resolved as the Preview does) or by a relative link
 * {@code [x](../a.md)} (the link check's rules: no scheme, no / or \ in front, so never a network path). Also the link scan the link
 * check uses. No Eclipse here, so a check runs without it.
 */
final class Backlinks {
	/** A linking file: the name shown (the path from the vault root when the name is not unique), the file, the lines that link here. */
	record Entry(String name, File file, List<String> lines) {}

	static final int MAX_FILES = 5000, MAX_LINES = 3, MAX_EXCERPT = 200;
	static final long MAX_SIZE = 1 << 20;
	private static final Pattern MD = Pattern.compile("(?i)\\.(md|markdown)$");
	/** [text]: target at the start of a line, not a footnote [^1]: text */
	private static final Pattern REF = Pattern.compile("^ {0,3}\\[(?!\\^)[^\\]]+\\]:[ \\t]*(\\S+)");
	private static final Pattern SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:");

	/** The lines of a file that may link somewhere (not code), as read at that last-modified time and size. */
	private record Read(long modified, long size, List<String> lines) {}

	/** per vault root, per file (path from the root); a scan keeps only the files it saw */
	private static final Map<File, Map<String, Read>> CACHE = new ConcurrentHashMap<>();

	/** The files of the vault that link to the target, sorted by name; at most MAX_FILES Markdown files read, none over MAX_SIZE. */
	static List<Entry> of(File target, WikiLinks.Vault vault) {
		Path t = target.toPath().toAbsolutePath().normalize();
		Map<String, Read> old = CACHE.getOrDefault(vault.root(), Map.of()), now = new HashMap<>();
		List<Entry> out = new ArrayList<>();
		int n = 0;
		for (String rel : vault.files()) {
			if (!MD.matcher(rel).find()) continue;
			if (++n > MAX_FILES) break;
			File f = new File(vault.root(), rel);
			long modified = f.lastModified(), size = f.length();
			if (size > MAX_SIZE) continue;
			Read r = old.get(rel);
			if (r == null || r.modified() != modified || r.size() != size) r = new Read(modified, size, candidates(read(f)));
			now.put(rel, r);
			if (f.toPath().toAbsolutePath().normalize().equals(t)) continue;
			List<String> lines = new ArrayList<>();
			for (String l : r.lines()) {
				if (lines.size() == MAX_LINES) break;
				if (links(l, f.getParentFile(), t, vault)) lines.add(excerpt(l));
			}
			if (lines.isEmpty()) continue;
			String name = f.getName().replaceFirst("(?i)\\.(md|markdown)$", "");
			boolean unique = vault.byName().getOrDefault(f.getName().toLowerCase(Locale.ROOT), List.of()).size() < 2;
			out.add(new Entry(unique ? name : rel.replaceFirst("(?i)\\.(md|markdown)$", ""), f, lines));
		}
		CACHE.put(vault.root(), now);
		out.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Entry::name));
		return out;
	}

	private static String read(File f) {
		try {
			return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
		} catch (Exception e) {
			return ""; // unreadable: links nowhere
		}
	}

	/** The lines that are not code (front matter, fences, HTML comments) and have a link of some kind in them. */
	static List<String> candidates(String src) {
		String[] lines = Headings.lines(src);
		boolean[] code = Headings.code(lines);
		List<String> out = new ArrayList<>();
		for (int i = 0; i < lines.length; i++)
			if (!code[i] && (lines[i].contains("[[") || lines[i].contains("](") || lines[i].contains("]:"))) out.add(lines[i]);
		return out;
	}

	/** Whether a line (not code) of a file in dir links to the target (absolute, normalized). */
	static boolean links(String line, File dir, Path target, WikiLinks.Vault vault) {
		for (WikiLinks.Link w : WikiLinks.in(line)) {
			File f = w.target().isEmpty() ? null : vault.resolve(dir, w.target());
			if (f != null && f.toPath().toAbsolutePath().normalize().equals(target)) return true;
		}
		String l = blankCodeSpans(line);
		for (int[] r : targets(l)) {
			String p = path(l.substring(r[0], r[1]));
			try {
				if (p != null && dir.toPath().resolve(p).toAbsolutePath().normalize().equals(target)) return true;
			} catch (RuntimeException e) {
				// InvalidPathException: a name Windows does not take is no file
			}
		}
		return false;
	}

	private static String excerpt(String l) {
		String s = l.strip();
		return s.length() > MAX_EXCERPT ? s.substring(0, MAX_EXCERPT) + "…" : s;
	}

	/** Where the link targets of a line are ([x]: target, and every ](target)), start and end; code spans blanked out first. */
	static List<int[]> targets(String l) {
		List<int[]> targets = new ArrayList<>();
		Matcher m = REF.matcher(l);
		if (m.find()) targets.add(new int[] { m.start(1), m.end(1) });
		// each character is looked at once: the next search starts after the target just read
		for (int k = l.indexOf("]("), b; k >= 0; k = l.indexOf("](", Math.max(b, k + 2))) {
			int a = k + 2;
			while (a < l.length() && (l.charAt(a) == ' ' || l.charAt(a) == '\t')) a++;
			if (a < l.length() && l.charAt(a) == '<') {
				b = l.indexOf('>', ++a);
				if (b < 0) break; // no > further on either
			} else {
				b = a;
				while (b < l.length() && l.charAt(b) != ')' && l.charAt(b) != ' ' && l.charAt(b) != '\t') b++;
			}
			if (b > a) targets.add(new int[] { a, b });
		}
		return targets;
	}

	/**
	 * The file path of a relative link target, percent-decoded, without #anchor and ?query; null for web, mail, drives, absolute and
	 * network paths (before and after decoding: %5C%5Chost, %2F%2Fhost and file%3A//host are network paths as well) or no path.
	 */
	static String path(String u) {
		if (SCHEME.matcher(u).find() || u.startsWith("/") || u.startsWith("\\")) return null;
		int hash = u.indexOf('#');
		String raw = hash < 0 ? u : u.substring(0, hash);
		String path = decode(raw.indexOf('?') < 0 ? raw : raw.substring(0, raw.indexOf('?')));
		return path.isEmpty() || SCHEME.matcher(path).find() || path.startsWith("/") || path.startsWith("\\") ? null : path;
	}

	/** percent-decoded, + kept (as MarkdownEditor.decode, without loading UI classes) */
	static String decode(String s) {
		try {
			return java.net.URLDecoder.decode(s.replace("+", "%2B"), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			return s;
		}
	}

	/** `code` spans replaced by spaces, so links shown as code are not taken and columns stay */
	static String blankCodeSpans(String l) {
		StringBuilder b = new StringBuilder(l);
		int open = -1;
		for (int i = 0; i < b.length(); i++) {
			if (b.charAt(i) != '`') continue;
			if (open < 0) open = i;
			else {
				for (int j = open; j <= i; j++) b.setCharAt(j, ' ');
				open = -1;
			}
		}
		return b.toString();
	}

	private Backlinks() {
	}
}
