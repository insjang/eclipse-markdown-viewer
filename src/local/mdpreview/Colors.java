package local.mdpreview;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.swt.graphics.RGB;

/** Preview colors per theme; defaults are GitHub's markdown colors (github-markdown-css). */
final class Colors {
	/** CSS variable, label, dark default, light default */
	static final String[][] ITEMS = {
			{ "bg", "배경", "#0d1117", "#ffffff" },
			{ "fg", "본문", "#f0f6fc", "#1f2328" },
			{ "heading", "제목", "#f0f6fc", "#1f2328" },
			{ "strong", "굵게", "#f0f6fc", "#1f2328" },
			{ "em", "기울임", "#f0f6fc", "#1f2328" },
			{ "link", "링크", "#4493f8", "#0969da" },
			{ "code", "인라인 코드 글자", "#f0f6fc", "#1f2328" },
			{ "code-bg", "인라인 코드 배경", "#1f232a", "#eff1f3" },
			{ "pre-bg", "코드 블록 배경", "#151b23", "#f6f8fa" },
			{ "quote", "인용문 글자", "#9198a1", "#59636e" },
			{ "border", "테두리·구분선", "#3d444d", "#d1d9e0" },
			{ "th-bg", "표 머리글 배경", "#0d1117", "#ffffff" },
			{ "row-alt", "표 짝수 행 배경", "#151b23", "#f6f8fa" } };
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
