package io.github.insjang.mdviewer;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

/** PlantUML fenced blocks to SVG, locally, with the preview's font and colors. */
final class PlantUml {
	static {
		// a document from elsewhere must not read local files or URLs through !include
		System.setProperty("PLANTUML_SECURITY_PROFILE", "SANDBOX"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static final Map<String, String> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
		private static final long serialVersionUID = 1L;

		@Override
		protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
			return size() > 64;
		}
	};

	/**
	 * @param source the block as written, with or without @startuml/@enduml
	 * @param style  skinparam and style lines for font and colors, put right after the start line
	 */
	static synchronized String svg(String source, String style) {
		String key = style + '\n' + source;
		String svg = CACHE.get(key);
		if (svg == null) {
			svg = render(withStyle(source, style));
			CACHE.put(key, svg);
		}
		return svg;
	}

	static String withStyle(String source, String style) {
		String src = source.strip();
		if (!src.startsWith("@start")) { //$NON-NLS-1$
			src = "@startuml\n" + src + "\n@enduml"; //$NON-NLS-1$ //$NON-NLS-2$
		}
		int eol = src.indexOf('\n');
		// the Java layout (smetana) needs no Graphviz installed
		return src.substring(0, eol + 1) + "!pragma layout smetana\n" + style + '\n' + src.substring(eol + 1); //$NON-NLS-1$
	}

	private static String render(String source) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			new SourceStringReader(source).outputImage(out, new FileFormatOption(FileFormat.SVG));
			return out.toString(StandardCharsets.UTF_8);
		} catch (Exception | LinkageError e) {
			return "<pre class=\"plantuml-error\">PlantUML: " + e.toString().replace("&", "&amp;").replace("<", "&lt;") + "</pre>"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
		}
	}
}
