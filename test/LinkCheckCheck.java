package io.github.insjang.mdviewer;

import java.io.File;
import java.nio.file.Files;
// javac -cp build/classes:<eclipse plugins> -d build/test test/LinkCheckCheck.java && java -cp build/classes:build/test:<same> io.github.insjang.mdviewer.LinkCheckCheck
public class LinkCheckCheck {
	public static void main(String[] a) throws Exception {
		File dir = Files.createTempDirectory("mdlinks").toFile();
		try {
			Files.writeString(new File(dir, "other.md").toPath(), "# Other Part\n<a name=\"raw\"></a>\n");
			new File(dir, "img").mkdir();
			Files.writeString(new File(dir, "img/a b.png").toPath(), "");
			String src = "---\nlink: [x](front.md)\n---\n# Intro\r\n[ok](other.md#other-part) [ok](other.md#raw) [ok](#intro) [ok](#user-content-intro) ![i](img/a%20b.png)\n"
					+ "[bad](missing.md) [bad](other.md#nope) [bad](#nope) `[code](gone.md)` [w](https://e.com/x) [abs](/etc/x) [unc](//host/s/x.png) [unc](\\\\host\\s)\n"
					+ "```\n[fenced](gone.md)\n```\n[ref]: missing2.png \"t\"\n[^1]: Footnote, not a link.\nText[^1] [up](../) [q](other.md?x=1) [fn](#fn1)\n";
			var got = LinkCheck.check(src, dir, null);
			var msgs = got.stream().map(p -> (p.line() + 1) + ":" + src.substring(p.start(), p.end())).toList();
			if (!msgs.equals(java.util.List.of("6:missing.md", "6:other.md#nope", "6:#nope", "10:missing2.png"))) throw new AssertionError(msgs + " " + got);
			if (!got.get(0).message().contains("missing.md")) throw new AssertionError(got.get(0).message());
			// QA D8: network paths written percent-encoded are skipped too (never handed to File); D10: prefixed footnote anchors
			String more = "[n](%5C%5Chost%5Cs%5Cx.md) [n](%2F%2Fhost/s/x.md) [n](file%3A//host/x.md) [f](#user-content-fn1) [f](#user-content-fnref1:1) [bad](%2E/gone.md)\n";
			var got2 = LinkCheck.check(more, dir, null).stream().map(p -> more.substring(p.start(), p.end())).toList();
			if (!got2.equals(java.util.List.of("%2E/gone.md"))) throw new AssertionError("encoded: " + got2);
			// wikilinks: target in the vault (the folder here), #heading in it or in this document; not in code
			String wiki = "# Intro\n[[other]] [[Other#Other Part|x]] [[#Intro]] ![[a b.png]] [[gone]] [[other#Nope]] [[#nope]] [[other#^block]] `[[code]]`\n```\n[[fenced]]\n```\n";
			var got3 = LinkCheck.check(wiki, dir, io.github.insjang.mdviewer.editing.WikiLinks.vault(dir, false)).stream().map(p -> wiki.substring(p.start(), p.end()) + "=" + p.message()).toList();
			if (!got3.equals(java.util.List.of("[[gone]]=" + org.eclipse.osgi.util.NLS.bind(Messages.link_noWiki, "gone"), "[[other#Nope]]=" + org.eclipse.osgi.util.NLS.bind(Messages.link_noAnchor, "#Nope", "other"),
					"[[#nope]]=" + org.eclipse.osgi.util.NLS.bind(Messages.link_noAnchorHere, "#nope")))) throw new AssertionError("wiki: " + got3);
			// a crafted line must not take long
			String hostile = "[x](<".repeat(30_000) + "](" .repeat(30_000) + "[x]:" + " ".repeat(50_000) + "`".repeat(30_001);
			long t = System.nanoTime();
			LinkCheck.check(hostile + "\n" + "[" + "a".repeat(100_000) + "\n" + "[[a]]".repeat(800) + "\n", dir, io.github.insjang.mdviewer.editing.WikiLinks.vault(dir, false));
			long ms = (System.nanoTime() - t) / 1_000_000;
			if (ms > 500) throw new AssertionError("slow: " + ms + " ms");
			System.out.println("link check ok");
		} finally { // no temporary folder left behind
			try (var w = Files.walk(dir.toPath())) {
				w.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
			}
		}
	}
}
