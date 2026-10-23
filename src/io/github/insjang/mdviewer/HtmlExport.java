package io.github.insjang.mdviewer;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Export HTML: the page from preview.js htmlDoc() made into one file (CSS, KaTeX fonts and local images inside). */
final class HtmlExport {
	/** images larger than this keep their file:/// address (they then show only on this computer) */
	static final long MAX_IMAGE = 10 << 20;
	private static final Map<String, String> TYPES = Map.of("png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg", "gif", "image/gif",
			"svg", "image/svg+xml", "webp", "image/webp", "bmp", "image/bmp", "ico", "image/x-icon");
	/** src="file:///..." of <img> and href="file:///..." of SVG <image>: quoted value without quotes, nothing that can backtrack */
	private static final Pattern LOCAL = Pattern.compile("(<(?:img|image)\\b[^>]*?\\s(?:src|href|xlink:href)=\")(file:///[^\"]*)\"");

	/** web: the extracted web/ folder, theme: dark or light */
	static String page(String html, File web, String theme) throws Exception {
		StringBuilder css = new StringBuilder(Files.readString(new File(web, "preview.css").toPath()))
				.append(Files.readString(new File(web, "light".equals(theme) ? "hl-light.css" : "hl-dark.css").toPath()));
		if (html.contains("class=\"katex")) {
			String k = Files.readString(new File(web, "katex.min.css").toPath());
			Matcher m = Pattern.compile("url\\(fonts/([\\w-]+\\.woff2)\\)").matcher(k);
			StringBuilder b = new StringBuilder();
			while (m.find()) m.appendReplacement(b, "url(data:font/woff2;base64,"
					+ Base64.getEncoder().encodeToString(Files.readAllBytes(new File(web, "fonts/" + m.group(1)).toPath())) + ")");
			css.append(m.appendTail(b));
		}
		int at = html.indexOf("/*CSS*/"); // the one in <head>, before any document text
		return images(html.substring(0, at) + css + html.substring(at + 7));
	}

	/** Local images as data: URIs, so the file can be mailed or moved; network paths are never read. */
	static String images(String html) {
		Matcher m = LOCAL.matcher(html);
		StringBuilder b = new StringBuilder();
		while (m.find()) {
			String data = data(m.group(2).replace("&amp;", "&"));
			m.appendReplacement(b, Matcher.quoteReplacement(m.group(1) + (data == null ? m.group(2) : data) + "\""));
		}
		return m.appendTail(b).toString();
	}

	private static String data(String url) {
		try {
			URI u = URI.create(url);
			String path = u.getPath();
			if (u.getAuthority() != null || path == null || path.matches("(?s)[/\\\\]{2}.*")) return null; // \\host: never touched
			File f = new File(u);
			if (f.getPath().startsWith("\\\\") || f.getPath().startsWith("//")) return null;
			String ext = f.getName().replaceFirst(".*\\.", "").toLowerCase(Locale.ROOT);
			if (!TYPES.containsKey(ext) || !f.isFile() || f.length() > MAX_IMAGE) return null;
			return "data:" + TYPES.get(ext) + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(f.toPath()));
		} catch (Exception e) {
			return null; // unreadable or a bad URL: left as it was
		}
	}

	private HtmlExport() {
	}
}
