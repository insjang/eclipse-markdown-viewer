package io.github.insjang.mdviewer;

import java.io.File;
import java.nio.file.Files;
// same classpath as JsEscape (MarkdownEditor needs the Eclipse UI jars), run from any folder:
// a google-chrome in the current folder is not taken through a relative or empty PATH entry
public class PrinterCheck {
	public static void main(String[] a) throws Exception {
		File fake = new File("google-chrome");
		boolean made = !fake.exists() && fake.createNewFile() && fake.setExecutable(true);
		File dir = Files.createTempDirectory("mdprinter").toFile(), chrome = new File(dir, "chromium");
		chrome.createNewFile();
		chrome.setExecutable(true);
		try {
			String got = MarkdownEditor.printer(".:" + File.pathSeparator + "relative/bin" + File.pathSeparator + dir);
			if (!chrome.getPath().equals(got)) throw new AssertionError("only absolute PATH entries: " + got);
			if (MarkdownEditor.printer(null) != null && !new File(MarkdownEditor.printer(null)).isAbsolute()) throw new AssertionError("no PATH");
			if (MarkdownEditor.printer("." + File.pathSeparator) != null) throw new AssertionError("relative only: " + MarkdownEditor.printer("."));
		} finally {
			if (made) fake.delete();
		}
		System.out.println("printer ok");
	}
}
