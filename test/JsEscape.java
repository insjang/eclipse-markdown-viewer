package io.github.insjang.mdviewer;
public class JsEscape {
	public static void main(String[] a) throws Exception {
		String s = "a\"b\\c\nd\r\te f</script>한글|표";
		String lit = MarkdownEditor.js(s);
		Process p = new ProcessBuilder("node", "-e", "process.stdout.write(" + lit + ")").start();
		String back = new String(p.getInputStream().readAllBytes(), "UTF-8");
		if (!back.equals(s)) throw new AssertionError(lit + " -> " + back);
		System.out.println("js() ok");
	}
}
