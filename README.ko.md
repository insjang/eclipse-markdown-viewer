# Markdown Viewer for Eclipse

[English README](README.md)

| Source 탭 | Preview 탭, 다크 테마 | Preview 탭, 라이트 테마 |
|---|---|---|
| ![Eclipse 안의 Source 탭](docs/images/eclipse-source.png) | ![Eclipse 안의 Preview 탭, 다크 테마](docs/images/eclipse-preview.png) | ![Eclipse 안의 Preview 탭, 라이트 테마](docs/images/eclipse-light.png) |

| 다크 테마 | 라이트 테마 (PDF에도 사용) |
|---|---|
| ![다크 테마 다이어그램](docs/images/diagrams-dark.png) | ![라이트 테마 다이어그램](docs/images/diagrams-light.png) |

![KaTeX 수식, 다크 테마: 인라인·블록 수식, TeX 오류는 빨간색](docs/images/math-dark.png)

스크린샷은 기본 설정으로 [docs/sample.md](docs/sample.md)를 연 화면, 수식은 [test/math.md](test/math.md).

Eclipse용 Markdown 편집기 겸 미리보기 플러그인.
Source와 Preview가 한 편집기의 탭이라 화면을 나누거나 별도 뷰를 띄우지 않음: 문서가 편집 영역 전체를 쓰고, 옆에 터미널 등 다른 뷰를 둘 공간이 남음.
기본 Mylyn WikiText 미리보기가 지원하지 않는 GFM 표·체크박스·Mermaid를 VS Code와 같은 파서(markdown-it)로 표시.

## 주요 기능

| 기능 | 내용 |
|---|---|
| 하단 탭 | Source / Preview 탭을 같은 편집 영역에서 전환, 화면 분할 없이 각각 전체 폭 사용 (기본: Preview로 열림) |
| 아웃라인 | Outline 뷰(Window → Show View → Outline)에 제목 트리 표시, 클릭하면 Source 커서와 Preview가 그 제목으로 이동. 코드 블록·머리말 안의 `#` 줄은 제외 |
| 위치 동기화 | 탭을 바꿔도 스크롤바 위치 유지: 한쪽 끝이면 다른 쪽도 끝 (스크롤 비율 기준), 양방향. Preview에서 더블클릭하면 그 문단·표 행·목록 항목·다이어그램의 줄로 Source 탭 이동 |
| 렌더링 | GFM 표, 체크박스, 코드 하이라이트, Mermaid, PlantUML, 제목 앵커(목차 링크) |
| 머리말 (front matter) | 파일 맨 앞 YAML 블록(`---` ~ `---`, SKILL.md·정적 사이트 원고 등)을 가로줄·굵은 글씨 대신 접힌 키·값 표로 표시 |
| GitHub 문법 | 알림 상자(`> [!NOTE]`, `[!TIP]`, `[!IMPORTANT]`, `[!WARNING]`, `[!CAUTION]`, GitHub 색), 각주(`[^1]`), 이모지 코드(`:smile:`) |
| 수식 | GitHub와 같은 `$인라인$`, `$$블록$$`, ```` ```math ```` 블록, 내장 KaTeX로 오프라인 표시. `\$`와 $5 같은 금액은 글자 그대로, TeX 오류는 빨간색 |
| 다이어그램 확대 | Mermaid 다이어그램 위에서 Ctrl+휠로 포인터 기준 확대·축소, 끌어서 이동, `원래 크기` 버튼으로 복귀 |
| 코드 블록 | 마우스를 올리면 나오는 `복사` 버튼으로 코드 원문 복사 |
| 이미지 | 문서 기준 상대 경로의 PNG·GIF·SVG, 문서 안 `<svg>`. Source 탭에서 스크린샷(클립보드에 이미지만 있을 때)을 붙여넣으면 문서 옆 `images/image-<날짜>-<시각>.png`로 저장하고 링크 삽입 |
| 링크 | `.md`·`.markdown`·`.mdown`·`.mkd` 링크: Eclipse에서 열기 / 웹·메일 링크: 기본 브라우저 |
| PDF 내보내기 | A4, 라이트 색, Mermaid·수식 포함 (Windows: Edge, Linux: Chrome·Chromium 헤드리스 인쇄) |
| HTML | `HTML 내보내기`: 그려진 그대로(다이어그램은 SVG) 스타일을 넣은 파일 하나, 수식이 있으면 KaTeX 글꼴, 10 MB 이하 로컬 이미지 포함 (웹 이미지와 더 큰 파일은 원래 주소 참조). `HTML 복사`: 요소마다 `style=""`을 넣어 클립보드로 (메일, 위키, 블로그 편집기에 붙여넣기) |
| 실시간 반영 | Claude Code, Codex CLI 등 AI 에이전트가 터미널에서 파일을 쓰거나 고치면 Preview가 0.3초 안에 다시 그림 (표, Mermaid, PlantUML 포함). 바로 감지하려면 Preferences → General → Workspace → Refresh using native hooks or polling 켜기. 에디터에 저장 안 한 수정이 있으면 Eclipse가 다시 읽을지 물음 |
| 찾기 | Preview 탭에서 Ctrl+F: 찾기 막대, 일치 항목 모두 강조(다이어그램 글자 포함), Enter·Shift+Enter 또는 F3·Shift+F3로 다음·이전, 개수 표시, Esc로 닫기. Source 탭의 Ctrl+F는 기존 Eclipse 찾기/바꾸기 |
| 링크 검사 | 열린 워크스페이스 파일의 깨진 상대 경로 링크·이미지·`#앵커`(제목, HTML `id`/`name`)를 Problems 뷰에 경고로 표시. 웹 링크, 절대·네트워크 경로는 검사 안 함 |
| 글자 크기 | Preview 상단 `A-` / `A+` |
| 설정 | 글꼴, 줄 간격, 줄바꿈 유지, 다크·라이트 색 (기본: GitHub 색), 탭 더블클릭으로 에디터 닫기 (선택) |
| 오프라인 | JS 라이브러리와 PlantUML을 JAR에 포함, 인터넷 불필요 |
| 언어 | 영어, 한국어 (Eclipse 언어 설정을 따름) |

Source 탭은 Eclipse Generic Editor 사용.
TM4E 구문 강조와 Wild Web Developer의 Markdown 자동완성이 그대로 동작.

## 요구 사항

- Windows 10/11 + Microsoft Edge (WebView2): 모든 기능, PDF 내보내기 포함
- Linux: WebKitGTK(`libwebkit2gtk-4.1`)가 있으면 미리보기 동작 (WSL의 Ubuntu 24.04에서 확인), PDF 내보내기는 `PATH`의 Google Chrome 또는 Chromium 사용 (`google-chrome`, `google-chrome-stable`, `chromium-browser`, `chromium`, `microsoft-edge`), WSL의 Google Chrome으로 확인
- Eclipse 2026-09 (4.41) 이상에서 확인
  - 필요 번들: `org.eclipse.ui.genericeditor`, `org.eclipse.ui.editors`, `org.eclipse.jface.text` (Eclipse IDE 패키지에 기본 포함)
- 빌드: JDK 21 이상, bash (WSL 가능)

## 빌드와 설치

Eclipse 설치 경로를 `ECLIPSE` 환경 변수로 지정.

```bash
ECLIPSE=/path/to/eclipse ./build.sh            # build/io.github.insjang.mdviewer_<버전>.jar 생성 (처음 한 번 PlantUML JAR을 Maven Central에서 받아 체크섬 확인)
ECLIPSE=/path/to/eclipse ./build.sh install    # Eclipse 종료 상태에서 dropins/ 에 복사 (이전 버전 JAR은 삭제)
ECLIPSE=/path/to/eclipse ./build.sh site       # build/site 에 p2 업데이트 사이트 생성 (feature/ 에 feature.xml)
```

- 설치 후 Eclipse를 평소처럼 실행 (`-clean` 불필요)
  - 빌드마다 버전 끝자리에 빌드 시각이 붙어 캐시된 옛 번들과 겹치지 않음
- 배포판 설치 (셋 중 하나)
  - Eclipse Marketplace: 아래 버튼을 실행 중인 Eclipse로 끌어다 놓기, 또는 Help → Eclipse Marketplace에서 "Markdown Viewer" 검색 ([등록 페이지](https://marketplace.eclipse.org/content/markdown-viewer))

    [![Drag to your running Eclipse workspace to install Markdown Viewer](https://marketplace.eclipse.org/modules/custom/eclipsefdn/eclipsefdn_marketplace/images/btn-install.svg)](https://marketplace.eclipse.org/marketplace-client-intro?mpc_install=7567639 "Drag to your running Eclipse workspace to install Markdown Viewer")

  - 업데이트 사이트: Help → Install New Software → Work with에 `https://insjang.github.io/eclipse-markdown-viewer/updates/` 입력 후 Enter → Markdown Viewer 체크 → 설치 → 재시작
    - 코드 서명이 없어 서명 안 된 콘텐츠 신뢰 여부를 물음: Trust Selected
    - 이후 Help → Check for Updates로 업데이트
  - JAR 하나: [Releases](https://github.com/insjang/eclipse-markdown-viewer/releases)에서 받아 Eclipse 종료 상태에서 `dropins/`에 복사
- 제거: Help → About Eclipse IDE → Installation Details → Installed Software → Markdown Viewer → Uninstall, 또는 `dropins/io.github.insjang.mdviewer_*.jar` 삭제 후 재시작

## 사용법

- `.md` 파일 더블클릭: Markdown Viewer로 열림
  - Mylyn 편집기로 열리면: 우클릭 → Open With → Markdown Viewer
  - 기본 편집기 지정: Preferences → General → Editors → File Associations → `*.md` → Markdown Viewer → Default
- Preview 상단 버튼: `PDF 내보내기`, `HTML 내보내기`, `HTML 복사`, `A-`, `A+`, `글꼴 설정...`
- 저장(`Ctrl+S`)과 실행 취소는 Source 탭 편집기 기준으로 동작

## 설정

Preferences → General → Editors → Markdown Viewer

![Markdown Viewer 설정 화면: 기본값에서 라이트 테마만 선택한 상태](docs/images/preferences.png)

| 항목 | 기본값 |
|---|---|
| 본문 글꼴 | Eclipse 대화상자 글꼴 11pt (한국어 Windows: 맑은 고딕, 영문 Windows: Segoe UI) |
| 코드 글꼴 | Eclipse 텍스트 글꼴 10pt (Windows: Consolas, Preview에서는 합자 끔) |
| 줄 간격 | 1.6 |
| 테마 | 어둡게 |
| 원문 줄바꿈 그대로 표시 | 켜짐 (끄면 표준 Markdown처럼 한 문단으로 합침) |
| Preview 탭으로 시작 | 켜짐 |
| 깨진 상대 링크·#앵커를 Problems 뷰에 표시 | 켜짐 |
| 탭 더블클릭으로 에디터 닫기 | 꺼짐 (켜면 최대화 대신 닫기, 모든 에디터 탭에 적용, Ctrl+M은 계속 최대화) |
| 다크·라이트 색 13종 | GitHub 색 (`Restore Defaults`로 복원) |
| Mermaid 간격 (노드·단계·상자 안 여백) | 25 · 30 · 6 px |

- 들여쓰기: 코드 글꼴 영문 4자 폭 (목록, 인용문, 코드 탭)
- Mermaid: 본문 글꼴·크기 사용
- Mermaid 간격: 노드 사이 25, 단계 사이 30, 상자 안 여백 6 (Mermaid 기본 50·50·15보다 촘촘), 설정에서 조정. 블록 첫 줄의 `%%{init: {...}}%%`가 우선
  - 흐름도·시퀀스·ER에 적용 (시퀀스·ER은 각자 기본값에서 같은 비율로 축소)
  - PlantUML 내장 배치 엔진(smetana)은 간격 설정을 무시하므로 적용 안 함 (기본 배치가 이미 촘촘)
- 수식 예 (GitHub와 같은 문법):

  ````markdown
  $ax^2 + bx + c = 0$의 근은

  $$
  x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}
  $$
  ````

- PlantUML: ` ```plantuml ` 또는 ` ```puml ` 블록, 본문 글꼴·크기와 테마 색 적용
  - 플러그인 안의 PlantUML(MIT판)이 로컬에서 그림: 서버 전송 없음, Graphviz 불필요 (내장 smetana 배치)
  - `!include`로 로컬 파일·URL 읽기 차단 (PlantUML SANDBOX 보안 설정)
  - 처음 한 번은 엔진 로딩으로 1~2초 걸릴 수 있음
- PDF: 같은 글꼴 + 라이트 색

## 보안

외부에서 받은 `.md` 파일도 안전하게 열리도록 처리.

- 문서 안 HTML: DOMPurify로 정화 후 표시 (스크립트·이벤트 속성·`javascript:` 링크 제거)
- 허용 URL: `http`, `https`, `mailto`, 문서 기준 상대 경로, `#앵커`
- OS로 넘기는 링크: `http`, `https`, `mailto`만 (로컬 실행 파일 실행 차단)
- Mermaid: `strict` 모드
- KaTeX: `trust: false` (`\href`, `\url`, `\includegraphics`는 글자로만 표시)
- 네트워크 경로(`\\호스트\공유`, `//호스트/...`, `file://호스트/...`): 이미지·링크·스타일에서 제거, 이런 경로가 든 Mermaid 그림은 그리지 않음 (Windows는 읽기만 해도 그 호스트에 접속해 사용자 NTLM 해시를 넘김)
- 링크 검사: 문서 기준 상대 경로만 확인, 웹·절대·네트워크 경로는 파일 시스템에 묻지 않음
- 머리말·제목·링크 분석: 역추적이 없는 정규식과 직접 파싱 (악의적으로 긴 줄도 바로 처리, `HeadingsCheck`·`LinkCheckCheck`·`check.js`로 확인)
- HTML 내보내기·복사: 정화된 미리보기 내용만 사용, 스크립트 없음

## 구조

```
src/io/github/insjang/mdviewer/
  MarkdownEditor.java          Source/Preview 탭, 위치 동기화, 링크 처리, PDF 내보내기
  Headings.java, Outline.java  소스의 제목 추출, Outline 뷰 페이지
  LinkCheck.java               깨진 상대 링크를 Problems 뷰 경고로
  HtmlExport.java              HTML 내보내기: CSS·글꼴·로컬 이미지를 파일 하나에
  Activator.java               웹 자원 추출, A-/A+
  PrefInit.java                설정 기본값
  Colors.java                  테마별 색 (GitHub 기본값)
  MarkdownPreferencePage.java  설정 화면
  Contributor.java             실행 취소·찾기 등 편집 동작 연결
  TabClose.java                탭 더블클릭으로 에디터 닫기 (선택)
  PlantUml.java                PlantUML 블록을 SVG로 (글꼴·색 주입, 캐시, 샌드박스)
web/
  preview.html / .js / .css    렌더링, 줄 번호 앵커, 스타일
  *.min.js, hl-*.css           외부 라이브러리
feature/                       업데이트 사이트용 feature.xml, category.xml
icons/                         편집기 아이콘
test/
  check.js                     렌더링 검사 (node)
  sanitize.js                  HTML 정화 검사 (node + jsdom)
  plantuml.js                  PlantUML 블록 연결 검사 (node + jsdom)
  math.js, math.md             수식 렌더링·정화 검사 (node + jsdom)
  PlantUmlCheck.java           PlantUML 렌더링 검사 (한글, 글꼴, include 차단)
  JsEscape.java                Java → JS 문자열 변환 검사
  HeadingsCheck.java           아웃라인 제목 추출 검사 (줄 번호·id가 preview.js와 같은지)
  LinkCheckCheck.java          링크 검사 (깨진 경로·앵커, 코드·네트워크 경로 제외, 악의적 긴 줄)
  HtmlExportCheck.java         HTML 내보내기 이미지 포함 (크기 상한, 네트워크 경로 제외)
  chrome.sh, find-many.js      실제 미리보기 페이지를 헤드리스 Chrome으로 (일치 13만 건 찾기)
```

## 테스트

```bash
node test/check.js [검사할.md]
NODE_PATH=<jsdom 설치 경로>/node_modules node test/sanitize.js
NODE_PATH=<jsdom 설치 경로>/node_modules node test/math.js
javac -cp build/classes -d build/test test/HeadingsCheck.java && java -cp build/classes:build/test io.github.insjang.mdviewer.HeadingsCheck
# LinkCheckCheck: classpath에 $ECLIPSE/plugins 의 org.eclipse.osgi, org.eclipse.core.*, org.eclipse.equinox.* 추가
```

## 함께 쓰면 좋은 플러그인

[Modern Terminal](https://github.com/insjang/eclipse-modern-terminal) (같은 작성자): Windows Terminal 같은 색과 한글 폭 처리, TUI 앱 마우스 지원, 세션 저장 SSH와 SFTP Files 뷰를 갖춘 Eclipse 터미널. 터미널의 `.md` 경로를 Ctrl+클릭하면 이 뷰어로 열림. [Eclipse Marketplace](https://marketplace.eclipse.org/content/modern-terminal)

## 라이선스

- AI 코딩 도구(Anthropic의 Claude 등)의 도움을 받아 개발.

- 이 플러그인: [EPL-2.0](LICENSE)
- 포함 라이브러리는 각자의 라이선스를 따름: [NOTICE](NOTICE)

## 포함 라이브러리

| 라이브러리 | 버전 | 라이선스 |
|---|---|---|
| markdown-it | 14.3.2 | MIT |
| markdown-it-task-lists | 2.1.1 | ISC |
| markdown-it-footnote | 4.0.0 | MIT |
| markdown-it-emoji | 3.1.0 | MIT |
| GitHub Octicons (알림 아이콘 5개) | - | MIT |
| KaTeX | 0.19.0 | MIT |
| @vscode/markdown-it-katex | 1.1.2 | MIT |
| mermaid | 11.17.2 | MIT |
| highlight.js | 11.12.0 | BSD-3-Clause |
| DOMPurify | 3.4.16 | Apache-2.0 / MPL-2.0 |
| PlantUML (plantuml-mit) | 1.2026.8 | MIT |

## 제약

- PDF 내보내기는 Chromium 계열 브라우저 필요: Windows는 `C:\Program Files (x86)` 또는 `C:\Program Files`의 Edge, 그 밖은 `PATH`의 Chrome·Chromium, 없으면 버튼 숨김
  - snap 패키지 Chromium은 홈 폴더 밖, 홈 바로 아래 숨김 폴더의 파일을 못 읽음: 워크스페이스(`.metadata`에 인쇄용 페이지를 둠)가 그런 위치면 Google Chrome 패키지 사용
  - macOS(`/Applications/Google Chrome.app`)는 미확인
- 워크스페이스 밖 파일(`File → Open File`)도 열리지만 `.md` 링크 이동은 워크스페이스 파일 우선
