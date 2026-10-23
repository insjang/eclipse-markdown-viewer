package io.github.insjang.mdviewer;

import java.io.File;
import java.nio.file.Files;
// javac -cp build/classes -d build/test test/HtmlExportCheck.java && java -cp build/classes:build/test io.github.insjang.mdviewer.HtmlExportCheck
public class HtmlExportCheck {
	public static void main(String[] a) throws Exception {
		File dir = Files.createTempDirectory("mdexport").toFile();
		Files.write(new File(dir, "a b.png").toPath(), new byte[] { (byte) 0x89, 'P', 'N', 'G' });
		Files.write(new File(dir, "big.png").toPath(), new byte[(int) HtmlExport.MAX_IMAGE + 1]);
		String base = dir.toURI().toString().replaceFirst("^file:/+", "file:///");
		String html = "<p><img src=\"" + base + "a%20b.png\" alt=\"x\"> <img alt=\"y\" src=\"" + base + "big.png\"> <img src=\"" + base + "missing.png\">"
				+ " <img src=\"file:////host/s/x.png\"> <img src=\"file:///%5C%5Chost/s/y.png\"> <img src=\"https://e.com/z.png\">"
				+ " <svg><image href=\"" + base + "a%20b.png\"/></svg> text src=\"" + base + "a%20b.png\"</p>";
		String out = HtmlExport.images(html);
		if (!out.startsWith("<p><img src=\"data:image/png;base64,iVBORw==\" alt=\"x\">")) throw new AssertionError("small image embedded: " + out);
		if (!out.contains("<image href=\"data:image/png;base64,iVBORw==\"/>")) throw new AssertionError("svg image: " + out);
		for (String kept : new String[] { base + "big.png", base + "missing.png", "file:////host/s/x.png", "file:///%5C%5Chost/s/y.png", "https://e.com/z.png",
				"text src=\"" + base + "a%20b.png\"" })
			if (!out.contains(kept)) throw new AssertionError("kept as is: " + kept + " in " + out);
		System.out.println("html export ok");
	}
}
