# Markdown Viewer for Eclipse

A Markdown editor for Eclipse with a preview tab.
It renders GitHub Flavored Markdown with the same parser VS Code uses (markdown-it), draws Mermaid and PlantUML diagrams, keeps the source and the preview at the same scroll position, and exports a PDF in one click.

[한국어 README](README.ko.md)

## Features

| Feature | Details |
|---|---|
| Tabs | Source and Preview tabs in one editor area, opening on Preview by default |
| Scroll sync | Switching tabs keeps the scrollbar position: at the bottom of one means at the bottom of the other |
| Rendering | GFM tables, task lists, syntax highlighting, heading anchors (table of contents links) |
| Mermaid | ```` ```mermaid ```` blocks, with tighter default spacing and the preview's font |
| PlantUML | ```` ```plantuml ```` / ```` ```puml ```` blocks, drawn locally by the bundled PlantUML, no server, no Graphviz |
| Images | PNG, GIF and SVG relative to the document, inline `<svg>` |
| Links | `.md` links open in Eclipse, web and mail links in the system browser |
| PDF export | A4, light colors, diagrams included |
| Font size | `A-` / `A+` on the preview toolbar |
| Preferences | Fonts, line height, line breaks, dark and light colors (GitHub colors by default), diagram spacing |
| Offline | All JavaScript and PlantUML are inside the plugin JAR |
| Languages | English, Korean |

The Source tab is Eclipse's Generic Editor, so TM4E syntax highlighting and the Wild Web Developer Markdown language server keep working.

## Requirements

- Windows 10 or 11 with Microsoft Edge (WebView2): the preview and the PDF export use it
- Eclipse 2026-09 (4.41) or later with `org.eclipse.ui.genericeditor` (included in the Eclipse IDE packages)

## Install

1. Download `io.github.insjang.mdviewer_<version>.jar` from [Releases](https://github.com/insjang/eclipse-markdown-viewer/releases).
2. Close Eclipse and copy the JAR into the `dropins` folder of your Eclipse installation.
3. Start Eclipse. `.md` files now open in the Markdown Viewer.
   If they still open in another editor: right-click the file > Open With > Markdown Viewer, or set it as the default under Preferences > General > Editors > File Associations > `*.md`.

To uninstall, delete the JAR from `dropins` and restart Eclipse.

## Build

JDK 21 or later and bash (WSL works).

```bash
ECLIPSE=/path/to/eclipse ./build.sh           # build/io.github.insjang.mdviewer_<version>.jar
ECLIPSE=/path/to/eclipse ./build.sh install   # also copy it into $ECLIPSE/dropins (close Eclipse first)
```

The first build downloads PlantUML (MIT build, 17 MB) from Maven Central and checks its SHA-1; it is not kept in git.
Every build gets a new version qualifier, so a plain restart picks up a replaced JAR.

## Preferences

Preferences > General > Editors > Markdown Viewer

| Setting | Default |
|---|---|
| Body font | Malgun Gothic 11 pt |
| Code font | D2Coding ligature 10 pt (ligatures are turned off in the preview) |
| Line height | 1.6 |
| Theme | Dark |
| Keep line breaks as written | On (off: standard Markdown, lines join into one paragraph) |
| Open files on the Preview tab | On |
| Dark and light colors (13 each) | GitHub colors, `Restore Defaults` brings them back |
| Mermaid spacing: nodes, ranks, box padding | 25, 30, 6 px (Mermaid's own defaults are 50, 50, 15) |

- Indentation of lists, quotes and tabs in code is four characters of the code font.
- Diagrams use the body font and size; the PDF uses the light colors.
- A `%%{init: {...}}%%` line in a Mermaid block overrides the spacing for that diagram.

## Security

Markdown from elsewhere can be opened safely.

- HTML in a document is sanitized with DOMPurify: scripts, event handlers and `javascript:` links are removed.
- Only `http`, `https`, `mailto`, document-relative paths and `#anchors` survive as URLs, and only `http`, `https` and `mailto` are handed to the operating system.
- Mermaid runs with `securityLevel: 'strict'`.
- PlantUML runs with its `SANDBOX` security profile: `!include` cannot read local files or URLs. Its SVG output is sanitized too.

## Limitations

- Windows only: the Edge path is fixed and the PDF is printed by headless Edge. Linux and macOS support would need another browser backend and PDF path.
- No outline view.
- PlantUML's built-in layout engine (smetana) ignores spacing settings; its default layout is already compact.

## Source layout

```
src/io/github/insjang/mdviewer/
  MarkdownEditor.java          Source/Preview tabs, scroll sync, links, PDF export
  PlantUml.java                PlantUML blocks to SVG (font and colors, cache, sandbox)
  Activator.java               web assets, A-/A+
  PrefInit.java                preference defaults
  Colors.java                  colors per theme (GitHub defaults)
  MarkdownPreferencePage.java  preference page
  Contributor.java             undo, find and other editing actions
  Messages.java, messages*.properties   UI text (English, Korean)
web/
  preview.html / .js / .css    rendering, sanitizing, scrolling, styles
  *.min.js, hl-*.css           bundled libraries
test/
  check.js                     rendering (node)
  sanitize.js, plantuml.js     sanitizing and PlantUML wiring (node + jsdom)
  JsEscape.java, PlantUmlCheck.java
```

## License

[Eclipse Public License 2.0](LICENSE).
Bundled third-party libraries (markdown-it, markdown-it-task-lists, Mermaid, highlight.js, DOMPurify, PlantUML) keep their own licenses, listed in [NOTICE](NOTICE).
