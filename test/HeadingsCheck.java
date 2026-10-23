package io.github.insjang.mdviewer;
// javac -cp build/classes -d build/test test/HeadingsCheck.java && java -cp build/classes:build/test io.github.insjang.mdviewer.HeadingsCheck : outline headings and ids, the same lines and ids preview.js gives
public class HeadingsCheck {
	public static void main(String[] a) {
		String src = "---\ntitle: x\n# not a heading\n---\n# Title #\n\nText\n===\n\n```md\n# in code\n```\n~~~\n## also code\n~~~\n## 정책 결정 (안건)\n- item\n---\n  ### C#\nPara\none\n---\n## Title\n#5 not\n    # indented code\n";
		String got = Headings.of(src).toString();
		String want = "[Heading[level=1, text=Title, line=4, id=title], Heading[level=1, text=Text, line=6, id=text], "
				+ "Heading[level=2, text=정책 결정 (안건), line=15, id=정책-결정-안건], Heading[level=3, text=C#, line=18, id=c], "
				+ "Heading[level=2, text=Para one, line=19, id=para-one], Heading[level=2, text=Title, line=22, id=title-1]]";
		if (!got.equals(want)) throw new AssertionError(got);
		if (Headings.tree(Headings.of("## a\n# b\n### c\n## d\n")).toString().replaceAll("Heading\\[level=(\\d), text=(\\w).*?\\]", "$2")
				.equals("[Node[heading=a, children=[]], Node[heading=b, children=[Node[heading=c, children=[]], Node[heading=d, children=[]]]]]") == false)
			throw new AssertionError("tree");
		for (String[] c : new String[][] { { "# foo ##", "foo" }, { "# foo#", "foo#" }, { "# ###", "" }, { "### b \\##", "b \\##" }, { "# a #  ", "a" } })
			if (!Headings.of(c[0]).get(0).text().equals(c[1])) throw new AssertionError(c[0] + " -> " + Headings.of(c[0]));
		// crafted lines must not backtrack: a downloaded .md would hang the UI thread
		String sp = " ".repeat(100_000);
		for (String bad : new String[] { "# a" + sp + "x", "#" + sp + "#".repeat(50_000) + "x", "# " + "[".repeat(100_000), "# " + "[a](".repeat(30_000),
				"x\n" + "=".repeat(100_000) + " x", "---\n" + "a:" + sp, "``" + "`".repeat(100_000) + "x\n# h" }) {
			long t = System.nanoTime();
			for (Headings.Heading h : Headings.of(bad)) Headings.label(h.text());
			long ms = (System.nanoTime() - t) / 1_000_000;
			if (ms > 100) throw new AssertionError("slow: " + ms + " ms on " + bad.substring(0, 12));
		}
		System.out.println("headings ok");
	}
}
