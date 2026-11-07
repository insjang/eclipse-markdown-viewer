package io.github.insjang.mdviewer.editing;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Obsidian wikilinks: {@code [[target]]}, {@code [[target#heading|label]]}, {@code [[#heading]]}, embeds {@code ![[image.png]]}. A target
 * resolves within a vault, the project of the document (see Activator.vault): a path with / from the vault or the document's folder,
 * a bare name anywhere in it ("shortest path": the document's folder first, then the shortest path). No Eclipse here, so a check runs
 * without it.
 */
public final class WikiLinks {
	/** A wikilink in a line: the whole construct ({@code !} of an embed included, end exclusive), the target ("" for this document), the heading or null. */
	public record Link(int start, int end, String target, String heading) {}

	/** A folder walk stops here, so a huge project cannot keep the UI waiting. */
	public static final int MAX_FILES = 20000;
	private static final Pattern SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:");

	/** The links of a line that are not in a code span; none in a line longer than {@link MarkdownLinks#MAX_LINE}. */
	public static List<Link> in(String line) {
		List<Link> out = new ArrayList<>();
		if (line.length() > MarkdownLinks.MAX_LINE || !line.contains("[[")) return out;
		BitSet code = MarkdownLinks.codeSpans(line);
		// each ]] with the [[ closest before it ([[a [[b]] is a link to b, as the Preview reads it), searched back only to the ]] before:
		// every character is looked at once
		for (int from = 0, j = line.indexOf("]]"), i; j >= 0; from = j + 2, j = line.indexOf("]]", from)) {
			for (i = j - 2; i >= from && !line.startsWith("[[", i); i--);
			if (i < from) continue;
			String s = line.substring(i + 2, j);
			if (s.indexOf('[') >= 0 || s.indexOf(']') >= 0 || i > 0 && line.charAt(i - 1) == '\\' || code.get(i)) continue;
			int bar = s.indexOf('|');
			String link = bar < 0 ? s : s.substring(0, bar);
			int hash = link.indexOf('#');
			String target = (hash < 0 ? link : link.substring(0, hash)).strip(), heading = hash < 0 ? null : link.substring(hash + 1).strip();
			if (heading != null && heading.isEmpty()) heading = null;
			if (target.isEmpty() && heading == null) continue;
			out.add(new Link(i > 0 && line.charAt(i - 1) == '!' ? i - 1 : i, j + 2, target, heading));
		}
		return out;
	}

	/** The link that covers the column, or null. */
	public static Link at(String line, int col) {
		for (Link l : in(line)) if (col >= l.start() && col < l.end()) return l;
		return null;
	}

	/** The files of a vault, as paths from its root with /, and the same by lower-case file name. */
	public record Vault(File root, List<String> files, Map<String, List<String>> byName) {
		/**
		 * The file a target names, seen from the document's folder; null when there is none, or the target has a scheme, starts with / or \
		 * or leaves the vault (never a network path). A name without extension is tried as a note (.md) first.
		 */
		public File resolve(File dir, String target) {
			String t = target.strip();
			if (t.isEmpty() || SCHEME.matcher(t).find() || t.startsWith("/") || t.startsWith("\\")) return null;
			try {
				Path r = root.toPath().toAbsolutePath().normalize(), d = dir.toPath().toAbsolutePath().normalize();
				for (String name : new String[] { t + ".md", t }) {
					if (t.indexOf('/') >= 0) { // from the vault, else from the document's folder; a file of the list, so not a hidden one either
						for (Path base : new Path[] { r, d }) {
							Path p = base.resolve(name).normalize();
							if (!p.startsWith(r) || p.equals(r)) continue;
							String rel = r.relativize(p).toString().replace('\\', '/');
							for (String f : byName.getOrDefault(p.getFileName().toString().toLowerCase(Locale.ROOT), List.of()))
								if (f.equalsIgnoreCase(rel)) return inside(r.resolve(f).toFile());
						}
						continue;
					}
					String here = d.startsWith(r) ? r.relativize(d).toString().replace('\\', '/') : null, best = null;
					if (here != null && !here.isEmpty()) here += "/";
					for (String f : byName.getOrDefault(name.toLowerCase(Locale.ROOT), List.of())) {
						if (here != null && f.length() - name.length() == here.length() && f.startsWith(here)) return inside(r.resolve(f).toFile());
						if (best == null || f.length() < best.length() || f.length() == best.length() && f.compareTo(best) < 0) best = f;
					}
					if (best != null) return inside(r.resolve(best).toFile());
				}
			} catch (RuntimeException e) {
				// InvalidPathException: a name Windows does not take (*, ?, :) is no file
			}
			return null;
		}

		/**
		 * The file, if it is in the vault also after links are followed, else null. A list from the workspace has links in it (Eclipse
		 * follows them), and what a resolved target names is read (headings, backlinks).
		 */
		public File inside(File f) {
			try {
				return f != null && f.toPath().toRealPath().startsWith(root.toPath().toRealPath()) ? f : null;
			} catch (IOException | RuntimeException e) {
				return null;
			}
		}
	}

	private record Cached(long time, boolean watched, Vault vault) {}

	private static final Map<File, Cached> CACHE = new ConcurrentHashMap<>();

	/**
	 * The vault of a project from the workspace's list of its files (in memory, so no disk is read), kept and patched by
	 * {@link #update} until {@link #forget(File)}.
	 */
	public static Vault vault(File root, Supplier<Collection<String>> files) {
		return CACHE.compute(root, (r, c) -> c != null && c.watched() ? c : new Cached(System.currentTimeMillis(), true, of(r, files.get()))).vault();
	}

	/** Files added to and removed from a project: its kept vault takes them in, if there is one; the next vault() builds it otherwise. */
	public static void update(File root, Collection<String> added, Collection<String> removed) {
		CACHE.computeIfPresent(root, (r, c) -> {
			if (!c.watched()) return c;
			LinkedHashSet<String> files = new LinkedHashSet<>(c.vault().files());
			files.removeAll(removed);
			files.addAll(added);
			return new Cached(c.time(), true, of(r, files));
		});
	}

	/**
	 * The vault at a folder walked on disk, its file list kept until {@link #forget(File)} (watched) or for 10 s (a folder outside the
	 * workspace).
	 */
	public static Vault vault(File root, boolean watched) {
		Cached c = CACHE.get(root);
		if (c == null || !c.watched() && System.currentTimeMillis() - c.time() > 10_000) {
			c = new Cached(System.currentTimeMillis(), watched, walk(root));
			CACHE.put(root, c);
		}
		return c.vault();
	}

	/** Files were added to or removed from the root (null: anywhere): its list is read again when next needed. */
	public static void forget(File root) {
		if (root == null) CACHE.clear();
		else CACHE.remove(root);
	}

	/** The regular files under the root, hidden ones (.git, .obsidian, .metadata...) and links left out, at most MAX_FILES. */
	static Vault walk(File root) {
		Path r = root.toPath();
		List<String> files = new ArrayList<>();
		try {
			Files.walkFileTree(r, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
					return d.equals(r) || !d.getFileName().toString().startsWith(".") ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
				}

				@Override
				public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
					String name = f.getFileName().toString();
					// a link (its attributes are the link's own) could point out of the vault, to a network share or a pipe that blocks a read
					if (name.startsWith(".") || !a.isRegularFile()) return FileVisitResult.CONTINUE;
					String rel = r.relativize(f).toString().replace('\\', '/');
					files.add(rel);
					return files.size() >= MAX_FILES ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path f, IOException e) {
					return FileVisitResult.CONTINUE; // unreadable: left out
				}
			});
		} catch (IOException e) {
			// what was read so far
		}
		return of(root, files);
	}

	/** The vault of these files, paths from the root with /. */
	static Vault of(File root, Collection<String> files) {
		Map<String, List<String>> byName = new HashMap<>();
		for (String rel : files) byName.computeIfAbsent(rel.substring(rel.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(rel);
		return new Vault(root, List.copyOf(files), byName);
	}

	private WikiLinks() {
	}
}
