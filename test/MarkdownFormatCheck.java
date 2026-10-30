package io.github.insjang.mdviewer.editing;
// javac -encoding UTF-8 -d build/test src/io/github/insjang/mdviewer/Headings.java src/io/github/insjang/mdviewer/editing/Fences.java src/io/github/insjang/mdviewer/editing/MarkdownLinks.java src/io/github/insjang/mdviewer/editing/MarkdownFormat.java test/MarkdownFormatCheck.java && java -cp build/test io.github.insjang.mdviewer.editing.MarkdownFormatCheck : the text changes of Ctrl+B, Ctrl+I and Ctrl+K, and crafted lines that must not take long
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
		// marks that pair up in the middle
		eq("a b c", apply("**a** b **c**", MarkdownFormat.toggle("**a** b **c**", 0, 13, '*', 2))); // all of them go, not only the two at the ends
		eq("a b c", apply("*a* b *c*", MarkdownFormat.toggle("*a* b *c*", 0, 9, '*', 1)));
		eq("- **x and y**", apply("- **x** and y", MarkdownFormat.toggle("- **x** and y", 0, 13, '*', 2))); // one pair around the whole, not ****x** and y**
		eq("**a b c**", apply("**a** b **c", MarkdownFormat.toggle("**a** b **c", 0, 11, '*', 2)));
		eq("**a b**", apply("a **b**", MarkdownFormat.toggle("a **b**", 0, 7, '*', 2)));
		eq("*a **b** c*", apply("a **b** c", MarkdownFormat.toggle("a **b** c", 0, 9, '*', 1))); // bold inside is not an italic mark
		eq("a\n- b", apply("**a**\n- **b**", MarkdownFormat.toggle("**a**\n- **b**", 0, 13, '*', 2))); // line by line, each taken away
		MarkdownFormat.Result mixed = MarkdownFormat.toggle("- **x** and y", 0, 13, '*', 2);
		eq("x and y", "- **x and y**".substring(mixed.selectionStart(), mixed.selectionEnd())); // the selection is the content
		// code is left alone
		eq("null", String.valueOf(MarkdownFormat.toggle("```\ncode\n```", 4, 8, '*', 2))); // in a fence
		eq("null", String.valueOf(MarkdownFormat.toggle("```\ncode\n```", 4, 4, '*', 2)));
		eq("null", String.valueOf(MarkdownFormat.toggle("text `some code` here", 7, 11, '*', 2))); // inside an inline span
		eq("null", String.valueOf(MarkdownFormat.toggle("text `some code` here", 9, 9, '*', 1)));
		eq("null", String.valueOf(MarkdownFormat.link("text `some code` here", 9, 9, null)));
		eq("**`some code`**", apply("`some code`", MarkdownFormat.toggle("`some code`", 0, 11, '*', 2))); // the whole span, with its backticks, can be marked
		eq("**text** `x`", apply("text `x`", MarkdownFormat.toggle("text `x`", 0, 4, '*', 2))); // text beside code
		eq("```\nx\n```\n**after**", apply("```\nx\n```\nafter", MarkdownFormat.toggle("```\nx\n```\nafter", 10, 15, '*', 2))); // after the fence closed
		// a link's destination
		eq("true", String.valueOf(MarkdownFormat.inDestination("[t]("))); // the selection starts right after the parenthesis
		eq("true", String.valueOf(MarkdownFormat.inDestination("see [t](docs/a")));
		eq("true", String.valueOf(MarkdownFormat.inDestination("[t](<my file")));
		eq("true", String.valueOf(MarkdownFormat.inDestination("[ref]: ")));
		eq("true", String.valueOf(MarkdownFormat.inDestination("  [ref]: <docs/")));
		eq("false", String.valueOf(MarkdownFormat.inDestination("[t](a.md) and ")));
		eq("false", String.valueOf(MarkdownFormat.inDestination("[t](a.md \"title")));
		eq("false", String.valueOf(MarkdownFormat.inDestination("plain text")));
		eq("false", String.valueOf(MarkdownFormat.inDestination("[a]: b and ")));
		eq("null", String.valueOf(MarkdownFormat.link("[t](docs/a.md)", 4, 13, "https://example.com/c"))); // Ctrl+K over a destination
		// crafted lines: the caret in the middle of 100000 brackets, or of a link-like run
		String[] bad = { "[", "[a](", "[a]", "(", "- ", "> ", "#", "* ", "`", "`` `", "](", "**", "*a* " };
		for (String piece : bad) {
			String b = piece.repeat(100000 / piece.length());
			int mid = b.length() / 2;
			long t = System.nanoTime();
			MarkdownFormat.link(b, mid, mid, null);
			MarkdownFormat.link(b, 0, b.length(), null);
			MarkdownFormat.toggle(b, 0, b.length(), '*', 2);
			MarkdownFormat.toggle(b, mid, mid, '*', 1);
			MarkdownFormat.inDestination(b);
			if (System.nanoTime() - t > 1_000_000_000L) throw new AssertionError("slow: " + (System.nanoTime() - t) / 1_000_000 + " ms for " + piece);
		}
		System.out.println("format ok");
	}
}
