package io.github.insjang.mdviewer;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class Activator extends AbstractUIPlugin {
	static final String ID = "io.github.insjang.mdviewer";
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

	/** A-/A+ buttons: change body and code font size together. */
	static void zoom(int delta) {
		for (String key : new String[] { PrefInit.BODY_FONT, PrefInit.CODE_FONT }) {
			FontData f = PreferenceConverter.getFontData(prefs(), key);
			f.setHeight(Math.max(6, f.getHeight() + delta));
			PreferenceConverter.setValue(prefs(), key, f);
		}
	}
}
