# Eclipse Markdown Viewer

Eclipse용 Markdown 편집기 겸 미리보기 플러그인.
기본 Mylyn WikiText 미리보기가 지원하지 않는 GFM 표·체크박스·Mermaid를 VS Code와 같은 파서(markdown-it)로 표시.

## 주요 기능

| 기능 | 내용 |
|---|---|
| 하단 탭 | Source / Preview 탭을 같은 편집 영역에서 전환 (기본: Preview로 열림) |
| 위치 동기화 | Preview → Source 전환 시 보던 위치의 원본 줄로 이동 (표는 행 단위), 반대 방향도 동일 |
| 렌더링 | GFM 표, 체크박스, 코드 하이라이트, Mermaid, PlantUML, 제목 앵커(목차 링크) |
| 이미지 | 문서 기준 상대 경로의 PNG·GIF·SVG, 문서 안 `<svg>` |
| 링크 | `.md` 링크: Eclipse에서 열기 / 웹·메일 링크: 기본 브라우저 |
| PDF 내보내기 | A4, 라이트 색, Mermaid 포함 (Edge 헤드리스 인쇄) |
| 글자 크기 | Preview 상단 `A-` / `A+` |
| 설정 | 글꼴, 줄 간격, 줄바꿈 유지, 다크·라이트 색 (기본: GitHub 색) |
| 오프라인 | JS 라이브러리를 JAR에 포함, 인터넷 불필요 |

Source 탭은 Eclipse Generic Editor 사용.
TM4E 구문 강조와 Wild Web Developer의 Markdown 자동완성이 그대로 동작.

## 요구 사항

- Windows 10/11 + Microsoft Edge (Preview 표시와 PDF 생성에 사용)
- Eclipse 2026-09 (4.41) 이상에서 확인
  - 필요 번들: `org.eclipse.ui.genericeditor`, `org.eclipse.ui.editors`, `org.eclipse.jface.text` (Eclipse IDE 패키지에 기본 포함)
- 빌드: JDK 21 이상, bash (WSL 가능)

## 빌드와 설치

`build.sh` 상단의 `ECLIPSE` 경로를 설치 위치에 맞게 수정.

```bash
./build.sh            # build/local.mdpreview_<버전>.jar 생성 (처음 한 번 PlantUML JAR을 Maven Central에서 받아 체크섬 확인)
./build.sh install    # Eclipse 종료 상태에서 dropins/ 에 복사 (이전 버전 JAR은 삭제)
```

- 설치 후 Eclipse를 평소처럼 실행 (`-clean` 불필요)
  - 빌드마다 버전 끝자리에 빌드 시각이 붙어 캐시된 옛 번들과 겹치지 않음
- 제거: `dropins/local.mdpreview_*.jar` 삭제 후 재시작
- p2(Install New Software, Marketplace) 사용 안 함

## 사용법

- `.md` 파일 더블클릭: Markdown Viewer로 열림
  - Mylyn 편집기로 열리면: 우클릭 → Open With → Markdown Viewer
  - 기본 편집기 지정: Preferences → General → Editors → File Associations → `*.md` → Markdown Viewer → Default
- Preview 상단 버튼: `PDF 내보내기`, `A-`, `A+`, `글꼴 설정...`
- 저장(`Ctrl+S`)과 실행 취소는 Source 탭 편집기 기준으로 동작

## 설정

Preferences → General → Editors → Markdown Viewer

| 항목 | 기본값 |
|---|---|
| 본문 글꼴 | 맑은 고딕 11pt |
| 코드 글꼴 | D2Coding ligature 10pt (Preview에서는 합자 끔) |
| 줄 간격 | 1.6 |
| 테마 | 어둡게 |
| 원문 줄바꿈 그대로 표시 | 켜짐 (끄면 표준 Markdown처럼 한 문단으로 합침) |
| Preview 탭으로 시작 | 켜짐 |
| 다크·라이트 색 13종 | GitHub 색 (`Restore Defaults`로 복원) |
| Mermaid 간격 (노드·단계·상자 안 여백) | 25 · 30 · 6 px |

- 들여쓰기: 코드 글꼴 영문 4자 폭 (목록, 인용문, 코드 탭)
- Mermaid: 본문 글꼴·크기 사용
- Mermaid 간격: 노드 사이 25, 단계 사이 30, 상자 안 여백 6 (Mermaid 기본 50·50·15보다 촘촘), 설정에서 조정. 블록 첫 줄의 `%%{init: {...}}%%`가 우선
  - 흐름도·시퀀스·ER에 적용 (시퀀스·ER은 각자 기본값에서 같은 비율로 축소)
  - PlantUML 내장 배치 엔진(smetana)은 간격 설정을 무시하므로 적용 안 함 (기본 배치가 이미 촘촘)
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

## 구조

```
src/local/mdpreview/
  MarkdownEditor.java          Source/Preview 탭, 위치 동기화, 링크 처리, PDF 내보내기
  Activator.java               웹 자원 추출, A-/A+
  PrefInit.java                설정 기본값
  Colors.java                  테마별 색 (GitHub 기본값)
  MarkdownPreferencePage.java  설정 화면
  Contributor.java             실행 취소·찾기 등 편집 동작 연결
  PlantUml.java                PlantUML 블록을 SVG로 (글꼴·색 주입, 캐시, 샌드박스)
web/
  preview.html / .js / .css    렌더링, 줄 번호 앵커, 스타일
  *.min.js, hl-*.css           외부 라이브러리
test/
  check.js                     렌더링 검사 (node)
  sanitize.js                  HTML 정화 검사 (node + jsdom)
  plantuml.js                  PlantUML 블록 연결 검사 (node + jsdom)
  PlantUmlCheck.java           PlantUML 렌더링 검사 (한글, 글꼴, include 차단)
  JsEscape.java                Java → JS 문자열 변환 검사
```

## 테스트

```bash
node test/check.js [검사할.md]
NODE_PATH=<jsdom 설치 경로>/node_modules node test/sanitize.js
```

## 포함 라이브러리

| 라이브러리 | 버전 | 라이선스 |
|---|---|---|
| markdown-it | 14.3.2 | MIT |
| markdown-it-task-lists | 2.1.1 | ISC |
| mermaid | 11.17.2 | MIT |
| highlight.js | 11.12.0 | BSD-3-Clause |
| DOMPurify | 3.4.16 | Apache-2.0 / MPL-2.0 |
| PlantUML (plantuml-mit) | 1.2026.8 | MIT |

## 제약

- Windows 전용: Edge 경로 고정 (`C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe`)
- 워크스페이스 밖 파일(`File → Open File`)도 열리지만 `.md` 링크 이동은 워크스페이스 파일 우선
- 아웃라인 뷰 미지원
