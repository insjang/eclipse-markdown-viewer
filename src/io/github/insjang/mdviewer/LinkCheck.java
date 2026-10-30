package io.github.insjang.mdviewer;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.osgi.util.NLS;

import io.github.insjang.mdviewer.editing.WikiLinks;

/**
 * Broken document-relative links and #anchors as warnings in the Problems view. Only relative paths are looked at:
 * no scheme (http:, C:), no / or \ in front, so a link never makes Eclipse touch a network path. Wikilinks are looked up in the
 * vault (see WikiLinks), with the same rules.
 */
final class LinkCheck {
	static final String MARKER = Activator.ID + ".link";

	record Problem(int line, int start, int end, String message) {
	}

	private static final Pattern ID = Pattern.compile("(?i)\\b(?:id|name)\\s*=\\s*[\"']([^\"']*)[\"']");

	/** src: the document, dir: its folder, vault: where its wikilinks resolve (null: not checked) */
	static List<Problem> check(String src, File dir, WikiLinks.Vault vault) {
		String[] lines = Headings.lines(src);
		boolean[] code = Headings.code(lines);
		List<Problem> out = new ArrayList<>();
		Set<String> here = null;
		for (int i = 0, offset = 0; i < lines.length; offset = next(src, offset), i++) {
			if (code[i]) continue;
			String l = Backlinks.blankCodeSpans(lines[i]);
			for (int[] t : Backlinks.targets(l)) { // start, end in the line
				String u = l.substring(t[0], t[1]);
				String msg;
				if (u.startsWith("#")) {
					if (here == null) here = anchors(src);
					msg = here.contains(anchor(u.substring(1))) || u.length() == 1 || anchor(u.substring(1)).matches("fn(ref)?\\d+(:\\d+)?") ? null : NLS.bind(Messages.link_noAnchorHere, u);
				} else {
					msg = file(u, dir);
				}
				if (msg != null) out.add(new Problem(i, offset + t[0], offset + t[1], msg));
			}
			for (WikiLinks.Link w : vault == null ? List.<WikiLinks.Link>of() : WikiLinks.in(lines[i])) {
				String msg, anchor = w.heading() == null || w.heading().startsWith("^") ? null : Headings.slug(w.heading()); // ^block: not a heading
				if (w.target().isEmpty()) {
					if (here == null) here = anchors(src);
					msg = anchor == null || here.contains(anchor) ? null : NLS.bind(Messages.link_noAnchorHere, "#" + w.heading());
				} else {
					File f = vault.resolve(dir, w.target());
					msg = f == null ? NLS.bind(Messages.link_noWiki, w.target())
							: anchor != null && f.getName().matches("(?i).*\\.(md|markdown|mdown|mkd)") && lacks(f, anchor)
									? NLS.bind(Messages.link_noAnchor, "#" + w.heading(), w.target())
									: null;
				}
				if (msg != null) out.add(new Problem(i, offset + w.start(), offset + w.end(), msg));
			}
		}
		return out;
	}

	/** null when fine, or the warning for a relative path[#anchor] */
	private static String file(String u, File dir) {
		String path = Backlinks.path(u); // null: web, mail, drives, absolute and network paths
		if (path == null) return null;
		int hash = u.indexOf('#');
		File f = new File(dir, path);
		if (!f.exists()) return NLS.bind(Messages.link_noFile, path);
		if (hash < 0 || hash == u.length() - 1 || !f.isFile() || !f.getName().matches("(?i).*\\.(md|markdown|mdown|mkd)")) return null;
		return lacks(f, anchor(u.substring(hash + 1))) ? NLS.bind(Messages.link_noAnchor, u.substring(hash), path) : null;
	}

	/** whether the file has no such heading or anchor; unreadable: not this check's business */
	private static boolean lacks(File f, String anchor) {
		try {
			return !anchors(Files.readString(f.toPath())).contains(anchor);
		} catch (Exception e) {
			return false;
		}
	}

	/** heading ids as the preview makes them, and id="" / name="" written in raw HTML */
	static Set<String> anchors(String src) {
		Set<String> s = new HashSet<>();
		for (Headings.Heading h : Headings.of(src)) s.add(h.id());
		Matcher m = ID.matcher(src);
		while (m.find()) s.add(m.group(1));
		return s;
	}

	/** offset of the line after the one starting at i (\n, \r\n or \r) */
	private static int next(String src, int i) {
		while (i < src.length() && src.charAt(i) != '\n' && src.charAt(i) != '\r') i++;
		return i < src.length() && src.charAt(i) == '\r' && i + 1 < src.length() && src.charAt(i + 1) == '\n' ? i + 2 : i + 1;
	}

	private static String anchor(String a) {
		a = Backlinks.decode(a);
		return a.startsWith("user-content-") ? a.substring("user-content-".length()) : a;
	}

	/** Replaces this check's markers on the file, in the background. */
	static Job schedule(Job previous, IFile file, String src) {
		if (previous != null) previous.cancel();
		Job job = Job.create(Messages.link_job, monitor -> {
			File dir = file.getLocation() == null ? null : file.getLocation().toFile().getParentFile();
			File project = file.getProject().getLocation() == null ? null : file.getProject().getLocation().toFile();
			List<Problem> problems = dir == null || !Activator.prefs().getBoolean(PrefInit.LINK_CHECK) ? List.of()
					: check(src, dir, project == null || !src.contains("[[") ? null : WikiLinks.vault(project, true));
			if (monitor.isCanceled() || !file.exists()) return;
			file.getWorkspace().run(m -> {
				if (monitor.isCanceled()) return; // the editor closed meanwhile: it has cleared the markers
				file.deleteMarkers(MARKER, false, IResource.DEPTH_ZERO);
				for (Problem p : problems) {
					IMarker k = file.createMarker(MARKER);
					k.setAttributes(new String[] { IMarker.SEVERITY, IMarker.LINE_NUMBER, IMarker.CHAR_START, IMarker.CHAR_END, IMarker.MESSAGE },
							new Object[] { IMarker.SEVERITY_WARNING, p.line() + 1, p.start(), p.end(), p.message() });
				}
			}, file, 0, monitor);
		});
		job.setRule(file);
		job.setSystem(true);
		job.schedule(500);
		return job;
	}

	/** After the editor closed: stop a check under way first, so it cannot put the markers back. */
	static void clear(Job job, IFile file) {
		if (job != null) {
			job.cancel();
			try {
				job.join(1000, null);
			} catch (Exception e) {
				// clear anyway
			}
		}
		clear(file);
	}

	static void clear(IFile file) {
		try {
			if (file.exists()) file.deleteMarkers(MARKER, false, IResource.DEPTH_ZERO);
		} catch (CoreException e) {
			// gone with the editor anyway
		}
	}

	private LinkCheck() {
	}
}
