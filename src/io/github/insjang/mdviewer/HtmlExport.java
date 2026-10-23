package io.github.insjang.mdviewer;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Export HTML: the page from preview.js htmlDoc() made into one file (CSS and KaTeX fonts inside). Local
 * images become links relative to the saved file; they are never read, so the file holds nothing but
 * what the document shows (a link could lead anywhere on the disk).
 */
final class HtmlExport {
	/** src="file:///..." of <img> and href="file:///..." of SVG <image>: quoted value without quotes, nothing that can backtrack */
	private static final Pattern LOCAL = Pattern.compile("(<(?:img|image)\\b[^>]*?\\s(?:src|href|xlink:href)=\")(file:///[^\"]*)\"");

	/** web: the extracted web/ folder, theme: dark or light, dir: the folder the page is saved in */
	static String page(String html, File web, String theme, File dir) throws Exception {
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
		return images(html.substring(0, at) + css + html.substring(at + 7), dir);
	}

	/** Local images as links relative to dir (the page moves with its images); other addresses stay as they are. */
	static String images(String html, File dir) {
		Matcher m = LOCAL.matcher(html);
		StringBuilder b = new StringBuilder();
		while (m.find()) {
			String rel = relative(m.group(2).replace("&amp;", "&"), dir);
			m.appendReplacement(b, Matcher.quoteReplacement(m.group(1) + (rel == null ? m.group(2) : rel) + "\""));
		}
		return m.appendTail(b).toString();
	}

	private static String relative(String url, File dir) {
		try {
			URI u = URI.create(url);
			if (u.getAuthority() != null || u.getPath() == null || u.getPath().matches("(?s)[/\\\\]{2}.*")) return null; // \\host: as it is
			Path image = Path.of(u).normalize(), base = dir.toPath().toAbsolutePath().normalize();
			if (image.getRoot() == null || !image.getRoot().equals(base.getRoot())) return null; // another drive: no relative path
			String rel = base.relativize(image).toString().replace(File.separatorChar, '/');
			return new URI(null, null, rel, null).getRawPath().replace("&", "&amp;");
		} catch (Exception e) {
			return null; // a bad URL: left as it was
		}
	}

	private HtmlExport() {
	}
}
