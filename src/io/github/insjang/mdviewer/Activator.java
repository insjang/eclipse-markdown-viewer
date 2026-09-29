package io.github.insjang.mdviewer;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class Activator extends AbstractUIPlugin {
	static final String ID = "io.github.insjang.mdviewer";
	static final String[] WEB = { "preview.html", "preview.js", "preview.css", "hl-dark.css", "hl-light.css",
			"markdown-it.min.js", "markdown-it-task-lists.min.js", "highlight.min.js", "mermaid.min.js", "purify.min.js" };
	private static Activator plugin;

	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
	}

	@Override
	public void stop(BundleContext context) throws Exception {
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
		for (String name : WEB) {
			try (InputStream in = plugin.getBundle().getEntry("web/" + name).openStream()) {
				Files.copy(in, new File(dir, name).toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
		}
		Files.writeString(stamp.toPath(), version);
		return dir;
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
