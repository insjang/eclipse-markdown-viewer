package io.github.insjang.mdviewer;
// javac -encoding UTF-8 -d build/test src/io/github/insjang/mdviewer/Headings.java src/io/github/insjang/mdviewer/FoldBlocks.java test/FoldBlocksCheck.java && java -cp build/test io.github.insjang.mdviewer.FoldBlocksCheck : the foldable blocks of a Markdown text (first-last line, from 0), and crafted lines that must not hang
public class FoldBlocksCheck {
	static void eq(String want, String src) {
		String got = FoldBlocks.parse(src).folds.toString().replaceAll("Fold\\[startLine=(\\d+), endLine=(\\d+)\\]", "$1-$2");
		if (!got.equals(want)) throw new AssertionError(got + ", want " + want + " for: " + src.replace("\n", "\\n"));
	}

	public static void main(String[] a) {
		eq("[0-3]", "---\ntitle: x\n# not a heading\n---\n"); // front matter, no heading inside
		eq("[1-3, 0-3]", "# One\n```\ncode\n```\n"); // the fence, then the section it is in
		eq("[0-2]", "~~~md\n# in code\n~~~\n"); // a heading in a fence is not structure
		eq("[0-2, 3-4]", "<!-- c\nx\n-->\n# h\ny\n"); // multi-line HTML comment, a heading with text under it
		eq("[0-3, 2-3]", "# A\n\n## B\ntext\n"); // B holds one text line, A holds B
		eq("[0-2]", "Title\n=====\ntext\n"); // setext heading: its section starts at the text line, the underline is part of the heading
		eq("[0-2]", "| a | b |\n| --- | :-: |\n| 1 | 2 |\n"); // table
		eq("[]", "| a | b |\n| nope |\n| 1 | 2 |\n"); // no delimiter row
		eq("[0-2]", "> a\n> b\n> c\n"); // quote of three lines
		eq("[]", "> a\n> b\n"); // two lines are not worth a fold
		eq("[0-2]", "- a\n  - b\n  - c\n"); // list item with something nested
		eq("[]", "- a\n- b\n"); // items alone
		eq("[]", "* * *\n  x\n  y\n"); // a rule is not a list item
		eq("[]", "    > a\n    > b\n    > c\n"); // four blanks outside a list: indented code, not a quote
		eq("[1-3, 0-3]", "- a\n    > b\n    > c\n    > d\n"); // a quote inside a list item is one
		eq("[1-3, 0-4]", "- a\n  ```\n  code\n  ```\n  tail\n"); // a fenced block does not end the item
		// crafted lines: no backtracking, no hang
		String[] bad = { " ".repeat(100000) + " x", "|".repeat(100000), "|" + " ".repeat(100000) + "- ", "`".repeat(100000) + " ", "- ".repeat(50000), "#" + " ".repeat(100000) + "\u0085" };
		for (String b : bad) {
			long t = System.nanoTime();
			FoldBlocks.parse("x\n" + b + "\n" + b + "\ny\n");
			if (System.nanoTime() - t > 500_000_000L) throw new AssertionError("slow: " + (System.nanoTime() - t) / 1_000_000 + " ms for " + b.substring(0, 12));
		}
		System.out.println("fold blocks ok");
	}
}
