package io.github.insjang.mdviewer.editing;
// javac -encoding UTF-8 -d build/test src/io/github/insjang/mdviewer/editing/MarkdownFormat.java test/MarkdownFormatCheck.java && java -cp build/test io.github.insjang.mdviewer.editing.MarkdownFormatCheck : the text changes of Ctrl+B, Ctrl+I and Ctrl+K, and crafted lines that must not take long
public class MarkdownFormatCheck {
	static String apply(String text, MarkdownFormat.Result r) {
		StringBuilder sb = new StringBuilder(text);
		for (int i = r.edits().size() - 1; i >= 0; i--) {
			MarkdownFormat.Edit e = r.edits().get(i);
			sb.replace(e.offset(), e.offset() + e.length(), e.text());
		}
		return sb.toString();
	}

	static void eq(String want, String got) {
		if (!want.equals(got)) throw new AssertionError(got + ", want " + want);
	}

	public static void main(String[] a) {
		eq("**word**", apply("word", MarkdownFormat.toggle("word", 0, 4, '*', 2)));
		eq("word", apply("**word**", MarkdownFormat.toggle("**word**", 2, 6, '*', 2))); // again takes it away
		eq("*word*", apply("word", MarkdownFormat.toggle("word", 0, 4, '*', 1)));
		eq("***word***", apply("**word**", MarkdownFormat.toggle("**word**", 2, 6, '*', 1))); // italic on bold makes both
		eq("- **item**", apply("- item", MarkdownFormat.toggle("- item", 0, 6, '*', 2))); // the list marker stays outside
		eq("see [docs](https://example.com/c) here", apply("see docs here", MarkdownFormat.link("see docs here", 4, 8, "https://example.com/c")));
		eq("see []() here", apply("see  here", MarkdownFormat.link("see  here", 4, 4, null)));
		MarkdownFormat.Result r = MarkdownFormat.link("a [text](http://x.org/y) b", 5, 5, null); // in a link: selects the address
		eq("http://x.org/y", "a [text](http://x.org/y) b".substring(r.selectionStart(), r.selectionEnd()));
		// crafted lines: the caret in the middle of 100000 brackets, or of a link-like run
		String[] bad = { "[", "[a](", "[a]", "(", "- ", "> ", "#", "* " };
		for (String piece : bad) {
			String b = piece.repeat(100000 / piece.length());
			int mid = b.length() / 2;
			long t = System.nanoTime();
			MarkdownFormat.link(b, mid, mid, null);
			MarkdownFormat.link(b, 0, b.length(), null);
			MarkdownFormat.toggle(b, 0, b.length(), '*', 2);
			MarkdownFormat.toggle(b, mid, mid, '*', 1);
			if (System.nanoTime() - t > 1_000_000_000L) throw new AssertionError("slow: " + (System.nanoTime() - t) / 1_000_000 + " ms for " + piece);
		}
		System.out.println("format ok");
	}
}
