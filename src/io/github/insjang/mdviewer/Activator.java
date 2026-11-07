package io.github.insjang.mdviewer;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import io.github.insjang.mdviewer.editing.WikiLinks;

public class Activator extends AbstractUIPlugin {
	static final String ID = "io.github.insjang.mdviewer";
	private static Activator plugin;
	/** Folders of packages and caches, never notes (as in Foam's default excludes); hidden ones (.venv, .git) are left out anyway. */
	private static final Set<String> NOT_NOTES = Set.of("node_modules", "__pycache__", "venv", "site-packages");

	/**
	 * Files added to or removed from a project go into or out of its wikilink file list (moves are both), so the project is not listed
	 * again. What the list leaves out (see {@link #listed}), such as build output, is not looked into. That, or a Markdown file saved,
	 * also makes the open Previews of the project find their backlinks again. A project opened, closed or moved is listed again.
	 */
	private static final IResourceChangeListener WIKI_FILES = e -> {
		Set<File> changed = new HashSet<>();
		Map<File, List<String>> added = new HashMap<>(), removed = new HashMap<>();
		try {
			if (e.getDelta() != null) e.getDelta().accept(d -> {
				IResource r = d.getResource();
				if (r.getProject() == null) return true; // the workspace root
				IPath location = r.getProject().getLocation();
				if (location == null) return false;
				File project = location.toFile();
				if (r.getType() == IResource.PROJECT && (d.getKind() != IResourceDelta.CHANGED || (d.getFlags() & (IResourceDelta.OPEN | IResourceDelta.DESCRIPTION)) != 0)) {
					WikiLinks.forget(project);
					changed.add(project);
					return false;
				}
				// what the list leaves out, new or not, with all that is in it: a file added to bin/ or node_modules/ stays out too
				if (r.getType() != IResource.PROJECT && d.getKind() != IResourceDelta.REMOVED && !listed(r.createProxy())) return false;
				switch (d.getKind()) {
				case IResourceDelta.ADDED -> {
					if (r.getType() == IResource.FILE) added.computeIfAbsent(project, k -> new ArrayList<>()).add(r.getProjectRelativePath().toString());
					changed.add(project);
				}
				case IResourceDelta.REMOVED -> {
					if (r.getType() == IResource.FILE) removed.computeIfAbsent(project, k -> new ArrayList<>()).add(r.getProjectRelativePath().toString());
					changed.add(project);
				}
				default -> {
					if ((d.getFlags() & IResourceDelta.CONTENT) != 0 && r.getType() == IResource.FILE && r.getName().matches("(?i).*\\.(md|markdown)")) changed.add(project);
				}
				}
				return true;
			});
		} catch (CoreException x) {
			WikiLinks.forget(null);
		}
		for (File p : changed) WikiLinks.update(p, added.getOrDefault(p, List.of()), removed.getOrDefault(p, List.of()));
		if (!changed.isEmpty()) MarkdownEditor.backlinksChanged(changed);
	};

	/** Whether a resource is in a project's wikilink file list: not hidden, derived (build output), team private, linked or a package folder. */
	private static boolean listed(IResourceProxy r) {
		return !r.getName().startsWith(".") && !r.isDerived() && !r.isTeamPrivateMember() && !r.isHidden() && !r.isLinked()
				&& !(r.getType() == IResource.FOLDER && NOT_NOTES.contains(r.getName()));
	}

	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
		ResourcesPlugin.getWorkspace().addResourceChangeListener(WIKI_FILES, IResourceChangeEvent.POST_CHANGE);
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(WIKI_FILES);
		plugin = null;
		super.stop(context);
	}

	static IPreferenceStore prefs() {
		return plugin.getPreferenceStore();
	}

	/** Web assets extracted to the state location (re-extracted when the bundle changes). */
	static synchronized File web() throws Exception {
		File dir = plugin.getStateLocation().append("web").toFile();
		File stamp = new File(dir, ".stamp");
		String version = String.valueOf(plugin.getBundle().getLastModified());
		if (stamp.isFile() && Files.readString(stamp.toPath()).equals(version)) return dir;
		dir.mkdirs();
		// everything under web/, subfolders too (KaTeX fonts/)
		for (URL u : Collections.list(plugin.getBundle().findEntries("web", "*", true))) {
			String name = u.getPath().substring("/web/".length());
			if (name.isEmpty() || name.endsWith("/")) continue;
			File f = new File(dir, name);
			f.getParentFile().mkdirs();
			try (InputStream in = u.openStream()) {
				Files.copy(in, f.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
		}
		Files.writeString(stamp.toPath(), version);
		return dir;
	}

	/** Where the wikilinks of a document resolve: the project it is in, or its own folder outside the workspace. */
	public static WikiLinks.Vault vault(File doc) {
		IFile[] in = ResourcesPlugin.getWorkspace().getRoot().findFilesForLocationURI(doc.toURI());
		return in.length > 0 && in[0].getProject().getLocation() != null ? vault(in[0].getProject()) : WikiLinks.vault(doc.getParentFile(), false);
	}

	/** The wikilink vault of a project with a location, its files listed from the workspace's resource tree. */
	static WikiLinks.Vault vault(IProject project) {
		return WikiLinks.vault(project.getLocation().toFile(), () -> {
			List<String> files = new ArrayList<>();
			try {
				project.accept(r -> {
					if (r.getType() == IResource.PROJECT) return true;
					if (files.size() >= WikiLinks.MAX_FILES || !listed(r)) return false;
					if (r.getType() == IResource.FILE) files.add(r.requestFullPath().removeFirstSegments(1).toString());
					return true;
				}, IResource.NONE);
			} catch (CoreException e) {
				// closed meanwhile: what was listed
			}
			return files;
		});
	}

	/** A-/A+ buttons: change body and code font size together. */
	static void zoom(int delta) {
		for (String key : new String[] { PrefInit.BODY_FONT, PrefInit.CODE_FONT }) {
			FontData f = PreferenceConverter.getFontData(prefs(), key);
			f.setHeight(Math.max(6, f.getHeight() + delta));
			PreferenceConverter.setValue(prefs(), key, f);
		}
	}
}
