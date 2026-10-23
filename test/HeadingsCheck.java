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
		System.out.println("headings ok");
	}
}
