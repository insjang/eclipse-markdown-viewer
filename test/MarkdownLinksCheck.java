package io.github.insjang.mdviewer.editing;
// javac -encoding UTF-8 -d build/test src/io/github/insjang/mdviewer/Headings.java src/io/github/insjang/mdviewer/editing/MarkdownLinks.java test/MarkdownLinksCheck.java && java -cp build/test io.github.insjang.mdviewer.editing.MarkdownLinksCheck : links, anchors and fences of a Markdown text, and crafted lines that must not hang or overflow the stack
public class MarkdownLinksCheck {
	static final MarkdownLinks.Outline NONE = MarkdownLinks.outline("");

	static void at(String want, String line, int col) {
		MarkdownLinks.Link l = MarkdownLinks.at(line, col, MarkdownLinks.outline("[ref]: https://a.b\n"));
		String got = l == null ? null : l.target();
		if (want == null ? got != null : !want.equals(got)) throw new AssertionError(got + ", want " + want + " for " + line + " at " + col);
	}

	static void eq(Object want, Object got) {
		if (!want.equals(got)) throw new AssertionError(got + ", want " + want);
	}

	public static void main(String[] a) {
		at("dir/a.md", "see [a](dir/a.md) here", 6);
		at(null, "see [a](dir/a.md) here", 1); // not on the link
		at("dir/i.png", "![i](dir/i.png \"title\")", 3);
		at("#one", "[x](#one)", 1);
		at("my file.md", "[a](<my file.md>)", 4);
		at("dir/f(1).md", "[a](dir/f(1).md)", 4); // balanced parentheses in the destination
		at("https://e.org/x", "<https://e.org/x>", 5);
		at("https://e.org/x", "see https://e.org/x. Next", 8); // the dot is not part of the address
		at(null, "`[a](dir/a.md)`", 3); // inside a code span
		at("ref", "[text][ref]", 2); // the label of a reference link, defined above
		at("ref", "[ref]", 1);
		at(null, "[nope]", 1); // no definition
		at("https://a.b", "[ref]: https://a.b", 8);
		MarkdownLinks.Outline o = MarkdownLinks.outline("---\n# not a heading\n---\n# Intro\n\n## Part two\n\n## Part two\n```java\n# in code\n```\n");
		eq("[Heading[line=3, text=Intro, anchor=intro], Heading[line=5, text=Part two, anchor=part-two], Heading[line=7, text=Part two, anchor=part-two-1]]", o.headings.toString());
		eq(7, o.heading("part-two-1").line());
		eq(true, o.isOpeningFence(8));
		eq(true, o.isCode(9) && o.isCode(10) && !o.isCode(7));
		// crafted lines: no stack overflow, no minutes of backtracking
		String[] bad = { "[", "[a](", "[a]", "`", "<", "![", "[x\\", "a[b]", "(", "[a](" + "(", "<https:a", "](" };
		for (String piece : bad) {
			for (int len : new int[] { 3000, MarkdownLinks.MAX_LINE, 200000 }) {
				String b = piece.repeat(len / piece.length());
				long t = System.nanoTime();
				MarkdownLinks.Outline x = MarkdownLinks.outline("# h\n" + b + "\n" + b + "\n");
				for (int col = 0; col < b.length(); col += Math.max(1, b.length() / 4)) MarkdownLinks.at(b, col, x);
				if (System.nanoTime() - t > 1_000_000_000L) throw new AssertionError("slow: " + (System.nanoTime() - t) / 1_000_000 + " ms for " + piece + " x " + len);
			}
		}
		System.out.println("links ok");
	}
}
