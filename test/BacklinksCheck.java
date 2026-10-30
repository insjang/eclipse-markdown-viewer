package io.github.insjang.mdviewer;

import java.io.File;
import java.nio.file.Files;

import io.github.insjang.mdviewer.editing.WikiLinks;
// javac -encoding UTF-8 -d build/test src/io/github/insjang/mdviewer/Headings.java src/io/github/insjang/mdviewer/Backlinks.java src/io/github/insjang/mdviewer/editing/MarkdownLinks.java src/io/github/insjang/mdviewer/editing/WikiLinks.java test/BacklinksCheck.java && java -cp build/test io.github.insjang.mdviewer.BacklinksCheck : which files of a vault link to a document (wikilinks, relative links), never by network paths, re-read only when changed
public class BacklinksCheck {
	static void eq(Object want, Object got) {
		if (!String.valueOf(want).equals(String.valueOf(got))) throw new AssertionError(got + ", want " + want);
	}

	static String names(java.util.List<Backlinks.Entry> list) {
		return list.stream().map(e -> e.name() + "=" + e.lines()).toList().toString();
	}

	public static void main(String[] a) throws Exception {
		File root = Files.createTempDirectory("mdback").toFile();
		try {
			String[][] files = { { "Target.md", "# T\n[[Target]] links itself\n" },
					{ "a.md", "see [[target]] here\n`[[Target]]` in code\n```\n[[Target]]\n```\n[x](Target.md#t) and [[Target#T|t]]\nmore [[target]]\nfourth [[Target]]\n" },
					{ "notes/b.md", "  [up](../Target.md)  \n[n](%2E%2E/Target.md) [w](https://e.com/Target.md) [abs](/Target.md) [unc](//host/Target.md) [e](%5C%5Chost%5CTarget.md)\n" },
					{ "notes/a.md", "[ref]: ../Target.md\n" }, { "c.markdown", "---\nx: [[Target]]\n---\n%% nothing \n[[Other]]\n" }, { "Other.md", "x" },
					{ "big.md", "[[Target]]\n" + "x".repeat((int) Backlinks.MAX_SIZE) }, { "img.png", "[[Target]]" } };
			for (String[] f : files) {
				File file = new File(root, f[0]);
				file.getParentFile().mkdirs();
				Files.writeString(file.toPath(), f[1]);
			}
			File target = new File(root, "Target.md");
			WikiLinks.Vault v = WikiLinks.vault(root, false);
			// itself, code, front matter, web, absolute and network paths, other targets, big files and non-Markdown files are left out;
			// a name used twice shows its path; at most 3 lines a file, trimmed; sorted by name
			eq("[a=[see [[target]] here, [x](Target.md#t) and [[Target#T|t]], more [[target]]], b=[[up](../Target.md), [n](%2E%2E/Target.md) [w](https://e.com/Target.md) [abs](/Target.md) [unc](//host/Target.md) [e](%5C%5Chost%5CTarget.md)], notes/a=[[ref]: ../Target.md]]",
					names(Backlinks.of(target, v)));
			eq("[]", names(Backlinks.of(new File(root, "nothing.md"), v)));
			// a changed file is read again, an unchanged one not (its cached lines are kept even if the text changed behind its back)
			File b = new File(root, "notes/b.md");
			long t = b.lastModified();
			Files.writeString(b.toPath(), "[[Other]]" + " ".repeat((int) b.length() - 9)); // same size and time
			b.setLastModified(t);
			Files.writeString(new File(root, "Other.md").toPath(), "[[Target]] now\n");
			eq("[a, b, notes/a, Other]", Backlinks.of(target, v).stream().map(Backlinks.Entry::name).toList());
			b.setLastModified(t + 5000);
			eq("[a, notes/a, Other]", Backlinks.of(target, v).stream().map(Backlinks.Entry::name).toList());
			// relative paths: the link check's rules
			for (String bad : new String[] { "https://e.com/x", "C:/x.md", "/x.md", "\\\\h\\x.md", "%2F%2Fh/x.md", "file%3A//h/x.md", "#top", "" })
				eq(null, Backlinks.path(bad));
			eq("a b.md", Backlinks.path("a%20b.md?x=1#h"));
		} finally { // no temporary files left behind
			try (var w = Files.walk(root.toPath())) {
				w.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
			}
		}
		// crafted lines: bounded work
		File dir = new File(".");
		java.nio.file.Path none = new File("/none.md").toPath();
		WikiLinks.Vault empty = new WikiLinks.Vault(dir, java.util.List.of(), java.util.Map.of());
		for (String piece : new String[] { "](", "](<", "[[a]]", "`", "[x]: " }) {
			String l = piece.repeat(200_000 / piece.length());
			long s = System.nanoTime();
			Backlinks.links(l, dir, none, empty);
			Backlinks.candidates(l + "\n" + l);
			if (System.nanoTime() - s > 1_000_000_000L) throw new AssertionError("slow: " + piece);
		}
		System.out.println("backlinks ok");
	}
}
