package io.github.insjang.mdviewer;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
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
	/**
	 * a file or folder added to or removed from a project: its wikilink file list is read again. Build output (derived, such as bin/)
	 * comes and goes with every build and is left out, so a build does not make the next render walk the project again.
	 */
	private static final IResourceChangeListener WIKI_FILES = e -> {
		try {
			if (e.getDelta() != null) e.getDelta().accept(d -> {
				if (d.getKind() == IResourceDelta.CHANGED) return true;
				if (d.getResource().isDerived() || d.getResource().getProject() == null) return d.getResource().getProject() == null;
				IPath project = d.getResource().getProject().getLocation();
				if (project != null) WikiLinks.forget(project.toFile());
				return false;
			});
		} catch (CoreException x) {
			WikiLinks.forget(null);
		}
	};

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
		IPath project = in.length > 0 ? in[0].getProject().getLocation() : null;
		return project == null ? WikiLinks.vault(doc.getParentFile(), false) : WikiLinks.vault(project.toFile(), true);
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
