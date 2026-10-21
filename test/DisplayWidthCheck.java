package io.github.insjang.mdviewer.editing;
// javac -encoding UTF-8 -d build/test src/io/github/insjang/mdviewer/editing/DisplayWidth.java test/DisplayWidthCheck.java && java -cp build/test io.github.insjang.mdviewer.editing.DisplayWidthCheck : cell width of table text (Hangul, Han, Kana, fullwidth forms 2, combining marks 0)
public class DisplayWidthCheck {
	static void eq(int want, String s) {
		if (DisplayWidth.of(s) != want) throw new AssertionError(s + ": " + DisplayWidth.of(s) + ", want " + want);
	}

	public static void main(String[] a) {
		eq(5, "Hello");
		eq(0, "");
		eq(5, "정책 ");
		eq(9, "정책 결정");
		eq(4, "漢字");
		eq(4, "かな");
		eq(4, "ＡＢ"); // fullwidth forms
		eq(1, "é"); // e + combining acute
		eq(2, "😀"); // emoji outside the BMP
		eq(1, "a‍"); // zero width joiner counts 0
		eq(1, "é");
		eq(3, "a漢"); // mixed
		System.out.println("display width ok");
	}
}
