package io.github.insjang.mdviewer;

import java.io.File;
import java.nio.file.Files;
// javac -cp build/classes -d build/test test/HtmlExportCheck.java && java -cp build/classes:build/test io.github.insjang.mdviewer.HtmlExportCheck
public class HtmlExportCheck {
	public static void main(String[] a) throws Exception {
		File dir = Files.createTempDirectory("mdexport").toFile();
		try {
			new File(dir, "img").mkdir();
			new File(dir, "out").mkdir();
			String base = dir.toURI().toString().replaceFirst("^file:/+", "file:///");
			String html = "<p><img src=\"" + base + "img/a%20b.png\" alt=\"x\"> <img src=\"" + base + "c&amp;d.png\">"
					+ " <img src=\"file:////host/s/x.png\"> <img src=\"file:///%5C%5Chost/s/y.png\"> <img src=\"https://e.com/z.png\">"
					+ " <svg><image href=\"" + base + "img/a%20b.png\"/></svg> text src=\"" + base + "img/a%20b.png\"</p>";
			// saved next to the document: links from there; saved in a sub-folder: ../
			String out = HtmlExport.images(html, dir);
			if (!out.startsWith("<p><img src=\"img/a%20b.png\" alt=\"x\">")) throw new AssertionError("relative: " + out);
			if (!out.contains("<img src=\"c&amp;d.png\">")) throw new AssertionError("&: " + out);
			if (!out.contains("<image href=\"img/a%20b.png\"/>")) throw new AssertionError("svg image: " + out);
			if (!HtmlExport.images(html, new File(dir, "out")).startsWith("<p><img src=\"../img/a%20b.png\"")) throw new AssertionError("up: " + out);
			for (String kept : new String[] { "file:////host/s/x.png", "file:///%5C%5Chost/s/y.png", "https://e.com/z.png",
					"text src=\"" + base + "img/a%20b.png\"" })
				if (!out.contains(kept)) throw new AssertionError("kept as is: " + kept + " in " + out);
			if (out.contains("data:image")) throw new AssertionError("nothing read: " + out);
			System.out.println("html export ok");
		} finally { // no temporary folder left behind
			try (var w = Files.walk(dir.toPath())) {
				w.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
			}
		}
	}
}
