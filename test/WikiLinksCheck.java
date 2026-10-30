package io.github.insjang.mdviewer.editing;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
// javac -encoding UTF-8 -d build/test src/io/github/insjang/mdviewer/Headings.java src/io/github/insjang/mdviewer/editing/MarkdownLinks.java src/io/github/insjang/mdviewer/editing/WikiLinks.java test/WikiLinksCheck.java && java -cp build/test io.github.insjang.mdviewer.editing.WikiLinksCheck : wikilinks of a line, targets resolved in a vault (shortest path, never out of it), crafted lines
public class WikiLinksCheck {
	static void eq(Object want, Object got) {
		if (!String.valueOf(want).equals(String.valueOf(got))) throw new AssertionError(got + ", want " + want);
	}

	public static void main(String[] a) throws Exception {
		eq("[Link[start=4, end=12, target=note, heading=null], Link[start=13, end=36, target=a b, heading=Some Head], Link[start=37, end=45, target=, heading=top], Link[start=46, end=60, target=i.png, heading=null]]",
				WikiLinks.in("see [[note]] [[a b#Some Head|label]] [[#top]] ![[i.png|300]] end"));
		eq("[Link[start=4, end=9, target=b, heading=null]]", WikiLinks.in("[[a [[b]] `[[c]]` \\[[d]] [[]] [[|x]] [[e]b]]")); // innermost, not in code, not escaped, not empty
		eq("[]", WikiLinks.in("[[a]" + " ".repeat(MarkdownLinks.MAX_LINE) + "[[b]]")); // too long a line
		eq("Link[start=0, end=6, target=x, heading=null]", WikiLinks.at("![[x]] y", 2));
		eq(null, WikiLinks.at("![[x]] y", 6));

		File root = Files.createTempDirectory("mdwiki").toFile(), outside = new File(root.getParentFile(), root.getName() + "-out.md");
		try {
			for (String f : new String[] { "Note.md", "notes/Note.md", "notes/deep/Note.md", "notes/Other.md", "img/pic.png", "v1.2 plan.md", ".obsidian/hidden.md", "notes/.x.md" }) {
				File file = new File(root, f);
				file.getParentFile().mkdirs();
				Files.writeString(file.toPath(), "# H\n");
			}
			Files.writeString(outside.toPath(), "");
			try {
				Files.createSymbolicLink(new File(root, "link.md").toPath(), outside.toPath()); // a link out of the vault
			} catch (IOException | UnsupportedOperationException e) {
				// Windows without the right to make links: nothing to check
			}
			WikiLinks.Vault v = WikiLinks.walk(root);
			eq(6, v.files().size()); // hidden folder and file, and the link, left out
			File notes = new File(root, "notes"), deep = new File(notes, "deep");
			eq(new File(notes, "Note.md"), v.resolve(notes, "note")); // the document's folder first, any case
			eq(new File(root, "Note.md"), v.resolve(deep.getParentFile().getParentFile(), "Note")); // at the root
			eq(new File(root, "Note.md"), v.resolve(new File(root, "img"), "NOTE")); // elsewhere: the shortest path
			eq(new File(deep, "Note.md"), v.resolve(deep, "Note.md"));
			eq(new File(notes, "Other.md"), v.resolve(root, "Other"));
			eq(new File(root, "img/pic.png"), v.resolve(deep, "pic.png"));
			eq(new File(root, "v1.2 plan.md"), v.resolve(root, "v1.2 plan")); // a dot in the name is not an extension
			eq(new File(deep, "Note.md"), v.resolve(root, "notes/deep/Note")); // a path from the root
			eq(new File(deep, "Note.md"), v.resolve(notes, "deep/Note")); // or from the document's folder
			eq(new File(notes, "Other.md"), v.resolve(deep, "../Other")); // .. inside the vault is fine
			// safety: nothing with a scheme, absolute or network paths, nothing outside the vault
			for (String bad : new String[] { "missing", "", "C:/Windows/win.ini", "file://host/x", "https://e.com/x", "/etc/passwd", "\\\\host\\s\\x",
					"//host/s/x", "../" + root.getName() + "-out", "notes/../../" + root.getName() + "-out.md", ".obsidian/hidden", "notes/.x", "img", "link" })
				eq(null, v.resolve(notes, bad));
			eq(null, WikiLinks.vault(root, false).resolve(root, "nope"));
			eq(v.files().size(), WikiLinks.vault(root, false).files().size()); // cached
		} finally { // no temporary files left behind
			outside.delete();
			try (var w = Files.walk(root.toPath())) {
				w.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
			}
		}
		// crafted lines: each character looked at a bounded number of times
		for (String piece : new String[] { "[[", "]]", "[[a", "![[", "[[a]]", "`[[a]]`", "[[a|", "[]", "`", "[[a#" }) {
			String b = piece.repeat(MarkdownLinks.MAX_LINE / piece.length());
			long t = System.nanoTime();
			for (int i = 0; i < 100; i++) WikiLinks.in(b);
			if (System.nanoTime() - t > 1_000_000_000L) throw new AssertionError("slow: " + (System.nanoTime() - t) / 1_000_000 + " ms for " + piece);
		}
		System.out.println("wikilinks ok");
	}
}
