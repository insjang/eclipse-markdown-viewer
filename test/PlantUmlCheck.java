package local.mdpreview;

/** java PlantUmlCheck: renders a sequence and a class diagram (Korean text) with the preview style */
public class PlantUmlCheck {
	public static void main(String[] a) throws Exception {
		String style = "skinparam defaultFontName \"Malgun Gothic\"\nskinparam defaultFontSize 15\nskinparam backgroundColor transparent\n"
				+ "<style>\nroot { FontName \"Malgun Gothic\"; FontSize 15; FontColor #f0f6fc; LineColor #9198a1; BackGroundColor #151b23 }\ndocument { BackGroundColor transparent }\n</style>";
		String seq = PlantUml.svg("Alice -> 상담원 : 연결 요청\n상담원 --> Alice : 응답", style);
		String cls = PlantUml.svg("class 주문 {\n +번호 : int\n}\nclass 고객\n고객 \"1\" -- \"*\" 주문", style);
		String inc = PlantUml.svg("!include /etc/passwd\nA -> B", style);
		for (String[] t : new String[][] { { "sequence", seq }, { "class", cls }, { "include", inc } }) {
			String s = t[1];
			System.out.println(t[0] + ": svg=" + s.startsWith("<svg") + " len=" + s.length() + " font=" + s.contains("Malgun Gothic")
					+ " hangul=" + (s.contains("상담원") || s.contains("주문")) + " color=" + s.toLowerCase().contains("#f0f6fc")
					+ " passwd=" + s.contains("root:x") + " error=" + (s.contains("plantuml-error") || s.contains("rror")));
		}
		java.nio.file.Files.writeString(java.nio.file.Path.of(a[0], "seq.svg"), seq);
		java.nio.file.Files.writeString(java.nio.file.Path.of(a[0], "cls.svg"), cls);
	}
}
