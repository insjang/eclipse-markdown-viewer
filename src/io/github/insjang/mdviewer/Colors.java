package io.github.insjang.mdviewer;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.swt.graphics.RGB;

/** Preview colors per theme; defaults are GitHub's markdown colors (github-markdown-css). */
final class Colors {
	/** CSS variable, label, dark default, light default */
	static final String[][] ITEMS = {
			{ "bg", Messages.color_bg, "#0d1117", "#ffffff" },
			{ "fg", Messages.color_fg, "#f0f6fc", "#1f2328" },
			{ "heading", Messages.color_heading, "#f0f6fc", "#1f2328" },
			{ "strong", Messages.color_strong, "#f0f6fc", "#1f2328" },
			{ "em", Messages.color_em, "#f0f6fc", "#1f2328" },
			{ "link", Messages.color_link, "#4493f8", "#0969da" },
			{ "code", Messages.color_code, "#f0f6fc", "#1f2328" },
			{ "code-bg", Messages.color_code_bg, "#1f232a", "#eff1f3" },
			{ "pre-bg", Messages.color_pre_bg, "#151b23", "#f6f8fa" },
			{ "quote", Messages.color_quote, "#9198a1", "#59636e" },
			{ "border", Messages.color_border, "#3d444d", "#d1d9e0" },
			{ "th-bg", Messages.color_th_bg, "#0d1117", "#ffffff" },
			{ "row-alt", Messages.color_row_alt, "#151b23", "#f6f8fa" } };
	static final String[] THEMES = { "dark", "light" };

	static String key(String theme, String var) {
		return "color." + theme + "." + var;
	}

	static void setDefaults(IPreferenceStore s) {
		for (String[] i : ITEMS)
			for (int t = 0; t < 2; t++) PreferenceConverter.setDefault(s, key(THEMES[t], i[0]), rgb(i[2 + t]));
	}

	/** {"dark":{"--bg":"#0d1117",...},"light":{...}} */
	static String json(IPreferenceStore s) {
		StringBuilder b = new StringBuilder("{");
		for (String theme : THEMES) {
			b.append(b.length() > 1 ? "," : "").append('"').append(theme).append("\":{");
			for (int n = 0; n < ITEMS.length; n++) {
				RGB c = PreferenceConverter.getColor(s, key(theme, ITEMS[n][0]));
				b.append(n > 0 ? "," : "").append("\"--").append(ITEMS[n][0]).append("\":\"")
						.append(String.format("#%02x%02x%02x", c.red, c.green, c.blue)).append('"');
			}
			b.append('}');
		}
		return b.append('}').toString();
	}

	private static RGB rgb(String hex) {
		int v = Integer.parseInt(hex.substring(1), 16);
		return new RGB(v >> 16, (v >> 8) & 0xff, v & 0xff);
	}
}
