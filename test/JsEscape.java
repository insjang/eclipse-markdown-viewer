package io.github.insjang.mdviewer;
public class JsEscape {
	public static void main(String[] a) throws Exception {
		String s = "a\"b\\c\nd\r\te f</script>한글|표";
		String lit = MarkdownEditor.js(s);
		Process p = new ProcessBuilder("node", "-e", "process.stdout.write(" + lit + ")").start();
		String back = new String(p.getInputStream().readAllBytes(), "UTF-8");
		if (!back.equals(s)) throw new AssertionError(lit + " -> " + back);
		System.out.println("js() ok");
		// Edge reports the page URL percent-encoded, File.toURI() keeps Korean as is
		if (!MarkdownEditor.decode("file:///C:/%ED%95%9C%EA%B8%80%20a/p.html#x").startsWith(MarkdownEditor.decode("file:///C:/한글%20a/p.html")))
			throw new AssertionError("decode");
		System.out.println("decode() ok");
	}
}
