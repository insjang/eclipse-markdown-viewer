package io.github.insjang.mdviewer.editing;
// javac -encoding UTF-8 -d build/test src/io/github/insjang/mdviewer/editing/Fences.java test/FencesCheck.java && java -cp build/test io.github.insjang.mdviewer.editing.FencesCheck : where a fenced code block is open, for lists, tables, pairs and links alike
public class FencesCheck {
	static boolean inside(String text, int to) {
		String[] lines = text.split("\n", -1);
		return Fences.inside(l -> lines[l], 0, to, false);
	}

	static void eq(boolean want, String text, int to) {
		if (inside(text, to) != want) throw new AssertionError("line " + to + " of: " + text.replace("\n", "\\n") + ", want " + want);
	}

	public static void main(String[] a) {
		eq(false, "text\n- item\n", 2); // no fence
		eq(true, "```\n- item\n", 1); // opened
		eq(false, "```\ncode\n```\n- item\n", 3); // closed
		eq(true, "~~~\ncode\n```\n- item\n", 3); // another mark does not close
		eq(true, "````\ncode\n```\n- item\n", 3); // fewer marks do not close
		eq(false, "```\ncode\n````\n- item\n", 3); // more marks do
		eq(true, "```\ncode\n```js\n- item\n", 3); // text after the closing marks: not a closing line
		eq(true, "```js\ncode\n", 2); // info text on the opening line
		eq(false, "``` a`b\n- item\n", 1); // a backtick fence takes no backtick in its info text: not a fence
		eq(true, "~~~ a`b\n- item\n", 1); // a tilde fence may
		eq(true, "  ```\n- item\n", 1); // blanks in front
		eq(true, "\t```\n- item\n", 1);
		eq(false, "``\n- item\n", 1); // two are not a fence
		eq(true, "```\n```\n```\nx\n", 3); // open, close, open again
		Fences.Fence f = Fences.parse("  ~~~~md");
		if (f == null || !f.indent().equals("  ") || f.mark() != '~' || f.length() != 4 || !f.info().equals("md")) throw new AssertionError(String.valueOf(f));
		if (Fences.parse("text") != null || Fences.parse("") != null || Fences.parse("   ") != null) throw new AssertionError("not a fence");
		// crafted lines: nothing but a loop over the characters
		String[] bad = { " ".repeat(100000), " ".repeat(100000) + "`", "`".repeat(100000), "`".repeat(100000) + " ", "~".repeat(100000) + " `" };
		for (String b : bad) {
			long t = System.nanoTime();
			for (int i = 0; i < 50; i++) Fences.parse(b);
			if (System.nanoTime() - t > 1_000_000_000L) throw new AssertionError("slow: " + (System.nanoTime() - t) / 1_000_000 + " ms");
		}
		System.out.println("fences ok");
	}
}
