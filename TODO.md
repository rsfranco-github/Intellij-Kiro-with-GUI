# Kiro with GUI — 개발 단계 & TODO

## 개발 원칙

- 각 Phase 끝에 `./gradlew runIde`로 동작 확인 후 다음 Phase 진행
- Phase 1이 완전히 동작해야 Phase 2 시작
- 각 TODO 항목은 하나의 커밋 단위로 작업

---

## Phase 0: 프로젝트 초기 셋업 (예상: 1일)

프로젝트 스캐폴딩. 빈 플러그인이 IntelliJ에서 로드되는 것까지 확인.

- [ ] **0-1** Gradle 프로젝트 생성
  - `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` 작성
  - IntelliJ Platform Plugin 2.x 설정
  - Kotlin 2.1, kotlinx.serialization 의존성
  - `./gradlew build` 성공 확인

- [ ] **0-2** plugin.xml 기본 구조 작성
  - plugin id, name, vendor, description
  - `com.intellij.modules.platform` 의존성
  - `org.jetbrains.plugins.terminal` 의존성

- [ ] **0-3** 빈 플러그인 로드 확인
  - `./gradlew runIde` 실행
  - sandbox IDE에서 Settings → Plugins → "Kiro with GUI" 표시 확인
  - IDE 로그에 에러 없는지 확인

- [ ] **0-4** Git 초기화 + .gitignore
  - `.idea/`, `build/`, `.gradle/`, `*.iml` 제외
  - 첫 커밋

**✅ Phase 0 완료 기준**: `./gradlew runIde`로 sandbox IDE가 열리고, 플러그인이 설치된 상태로 표시됨

---

## Phase 1: Tool Window + 터미널 임베딩 (예상: 2~3일)

핵심 MVP. IDE 안에서 kiro-cli chat을 실행할 수 있는 상태.

- [ ] **1-1** KiroToolWindowFactory 구현
  - `ToolWindowFactory` 구현
  - plugin.xml에 toolWindow 등록 (anchor=bottom)
  - 빈 패널로 Tool Window 표시 확인

- [ ] **1-2** 터미널 임베딩 — kiro-cli chat 실행
  - Terminal API로 kiro-cli chat 프로세스 실행
  - working directory = 프로젝트 루트
  - Tool Window 안에 터미널 위젯 표시
  - ⚠️ Terminal API 버전 분기 확인:
    - 2025.3+: Reworked Terminal API (`TerminalToolWindowTabsManager`)
    - 2025.1~2025.2: Classic Terminal API (`TerminalView` + `LocalTerminalDirectRunner`)
  - **검증**: kiro-cli chat이 실행되고 프롬프트가 표시되는가

- [ ] **1-3** kiro-cli 경로 자동 감지
  - `which kiro-cli` 또는 `where kiro-cli`로 경로 탐색
  - 못 찾으면 일반적인 경로 확인: `/usr/local/bin/kiro-cli`, `~/.local/bin/kiro-cli`
  - 못 찾으면 Tool Window에 "kiro-cli를 찾을 수 없습니다" 안내 + 설정 링크

- [ ] **1-4** Settings 화면 구현
  - `Settings → Tools → Kiro` 메뉴
  - 설정 항목:
    - Kiro command 경로 (텍스트 필드 + 파일 선택 버튼)
    - Auto-start on IDE open (체크박스)
  - `KiroSettings` (PersistentStateComponent) 로 설정 저장
  - 설정 변경 후 터미널 재시작 로직

- [ ] **1-5** 모델 선택 드롭다운
  - Tool Window 상단 툴바에 모델 선택 ComboBox 배치
  - 모델 목록: `Auto`, `Claude Opus 4.6`, `Claude Opus 4.5`, `Claude Sonnet 4.5`, `Claude Sonnet 4.0`, `Claude Haiku 4.5`
  - 선택 시 kiro-cli 터미널에 `/model <model-name>` 전송
  - 현재 모델 표시 (kiro-cli 시작 시 기본값 = Auto)
  - 설정에서 기본 모델 저장 가능 (내부적으로 `/model set-current-as-default`)

- [ ] **1-6** 단축키: Cmd+Esc로 Tool Window 열기/포커스
  - `OpenKiroAction` 구현
  - plugin.xml에 keyboard-shortcut 등록
  - Tool Window가 닫혀있으면 열기, 열려있으면 포커스

- [ ] **1-7** kiro-cli 미설치 시 에러 처리
  - 프로세스 시작 실패 시 사용자 친화적 에러 메시지
  - "Install kiro-cli" 링크 (https://kiro.dev/docs/cli/)
  - Notification으로 알림

**✅ Phase 1 완료 기준**: `Cmd+Esc` → Kiro Tool Window 열림 → kiro-cli chat 실행 → 메시지 입력/응답 가능

---

## Phase 2: IDE 컨텍스트 연동 (예상: 3~4일)

에디터의 선택 텍스트, 파일 참조, diagnostic을 kiro-cli에 전달.

### 2A: 터미널 입력 방식 (MCP 없이)

kiro-cli 터미널에 직접 텍스트를 주입하는 방식. 가장 단순.

- [ ] **2A-1** SendSelectionAction 구현
  - `Cmd+Shift+K`: 현재 선택 텍스트를 kiro-cli 터미널에 전송
  - 선택 없으면 현재 파일 전체 경로를 전송
  - Terminal API의 `sendText()` 사용
  - 포맷: `다음 코드를 봐줘:\n\`\`\`\n{선택텍스트}\n\`\`\`\n`

- [ ] **2A-2** InsertFileRefAction 구현
  - `Cmd+Option+K`: 현재 파일 + 라인 범위를 @File#L1-99 형식으로 터미널에 삽입
  - 선택 영역이 있으면 해당 라인 범위, 없으면 커서 위치 라인
  - Terminal API의 `sendText()` 사용

- [ ] **2A-3** 현재 파일 컨텍스트 자동 전달
  - 에디터 탭 전환 시 kiro-cli에 현재 파일 경로 알림 (선택적)
  - 설정에서 on/off 가능

### 2B: stdio MCP 서버 방식 (고급 연동)

플러그인이 MCP 서버 프로세스를 실행하고, kiro-cli에 등록하여 IDE 도구를 노출.

- [ ] **2B-1** IDE MCP 서버 스크립트 작성
  - Node.js 또는 Python 스크립트로 stdio MCP 서버 구현
  - 또는 Kotlin으로 standalone JAR 빌드
  - 플러그인이 이 프로세스를 관리
  - MCP 도구: `getCurrentSelection`, `getOpenEditors`, `getWorkspaceFolders`

- [ ] **2B-2** MCP 서버 자동 등록
  - 플러그인 시작 시 `kiro-cli mcp add --name kiro-ide --command "..." --scope workspace` 실행
  - 플러그인 종료 시 `kiro-cli mcp remove --name kiro-ide` 실행

- [ ] **2B-3** IDE ↔ MCP 서버 통신
  - 플러그인이 MCP 서버 프로세스와 내부 통신 (localhost socket 또는 파일)
  - MCP 서버가 kiro-cli로부터 tool call 받으면 → 플러그인에 요청 → IDE API 호출 → 결과 반환

- [ ] **2B-4** getDiagnostics 도구 구현
  - IDE의 `DaemonCodeAnalyzer`에서 현재 파일의 에러/경고 수집
  - MCP tool response로 반환
  - 포맷: `[{severity, message, file, line, column}]`

**✅ Phase 2 완료 기준**: 에디터에서 코드 선택 → `Cmd+Shift+K` → kiro-cli에 전달됨. `Cmd+Option+K`로 파일 참조 삽입 동작.

---

## Phase 3: Diff Viewer 연동 (예상: 3~4일)

kiro-cli가 파일 수정을 제안할 때 IDE diff viewer로 표시.

- [ ] **3-1** kiro-cli 출력 파싱 — 파일 수정 감지
  - Terminal output 모니터링
  - kiro-cli가 파일을 수정했을 때의 출력 패턴 파악
  - 파일 변경 이벤트 감지 (VirtualFileListener)

- [ ] **3-2** Diff viewer 표시
  - 파일 변경 감지 시 원본(git HEAD 또는 수정 전) vs 현재 내용 diff 표시
  - `DiffManager.getInstance().showDiff()` 사용
  - Accept: 변경 유지, Reject: git checkout으로 원복

- [ ] **3-3** openDiff MCP 도구 (2B 방식 사용 시)
  - kiro-cli가 직접 diff 요청을 보내는 방식
  - 원본 + 수정본을 받아서 IDE diff viewer에 표시
  - 사용자 Accept/Reject 결과를 kiro-cli에 반환

- [ ] **3-4** openFile MCP 도구
  - kiro-cli가 특정 파일을 IDE에서 열도록 요청
  - 라인 번호 지정 시 해당 라인으로 스크롤 + 하이라이트

- [ ] **3-5** saveDocument / checkDocumentDirty 도구
  - 파일 저장 요청 처리
  - 미저장 변경 확인 응답

**✅ Phase 3 완료 기준**: kiro-cli가 파일을 수정하면 IDE diff viewer에 변경사항이 표시되고, Accept/Reject 가능

---

## Phase 4: MCP / Skill / Powers 관리 UI (예상: 3~4일)

Tool Window에 관리 패널 추가.

- [ ] **4-1** Tool Window 탭 구조 변경
  - 기존 터미널 탭 + 새로운 "관리" 탭 추가
  - 탭: `Chat` | `MCP` | `Skills` | `Settings`

- [ ] **4-2** MCP 서버 관리 패널
  - `~/.kiro/settings/mcp.json` 및 `.kiro/settings/mcp.json` 읽기
  - 서버 목록 테이블 (이름, 명령어, 상태)
  - 추가 버튼 → 다이얼로그 (이름, 명령어, scope 입력)
  - 내부적으로 `kiro-cli mcp add/remove` 실행
  - 제거 버튼
  - mcp.json 직접 편집 버튼 (IDE 에디터로 열기)

- [ ] **4-3** MCP 서버 상태 모니터링
  - kiro-cli chat 세션에서 `/mcp` 명령 결과 파싱
  - 또는 `kiro-cli mcp status --name <name>` 실행
  - 상태 표시: 🟢 running / 🔴 stopped / 🟡 error

- [ ] **4-4** Skill 관리 패널
  - `/tools` 명령 결과 파싱하여 도구 목록 표시
  - `.kiro/agents/` 디렉토리의 agent config 읽기
  - skill 설명, 토큰 수, 권한 상태 표시
  - trust/untrust 토글 (내부적으로 `/tools trust <name>` 실행)

- [ ] **4-5** Powers 관리 패널
  - 설치된 Powers 목록 표시
  - 활성화/비활성화 토글
  - Power 상세 정보 (제공 도구, 설명)

- [ ] **4-6** Agent 전환 UI
  - 현재 활성 agent 표시
  - agent 목록 드롭다운 (내부적으로 `/agent swap` 실행)
  - agent 생성/편집 버튼

**✅ Phase 4 완료 기준**: MCP 탭에서 서버 목록 확인/추가/제거 가능. Skills 탭에서 도구 목록 확인 및 trust 토글 가능.

---

## Phase 5: 폴리싱 & 배포 (예상: 2~3일)

- [ ] **5-1** 아이콘 제작
  - Kiro 로고 기반 16x16, 32x32 SVG 아이콘
  - Tool Window 아이콘, Action 아이콘

- [ ] **5-2** 에러 핸들링 강화
  - kiro-cli 프로세스 비정상 종료 시 자동 재시작 옵션
  - 네트워크 에러, 인증 만료 시 안내 메시지
  - kiro-cli 버전 호환성 체크

- [ ] **5-3** 다중 프로젝트 지원
  - 여러 프로젝트 창이 열려있을 때 각각 독립적인 kiro-cli 세션
  - 프로젝트별 MCP 설정 분리

- [ ] **5-4** 성능 최적화
  - Selection tracker debounce 조정
  - 불필요한 diagnostic 수집 최소화
  - MCP 서버 프로세스 메모리 관리

- [ ] **5-5** README.md 작성
  - 설치 방법, 사용법, 스크린샷
  - 기여 가이드

- [ ] **5-6** Marketplace 배포
  - `./gradlew verifyPlugin` 통과
  - plugin.xml description, change-notes 완성
  - 스크린샷 3~5장 준비
  - `./gradlew publishPlugin` 실행

**✅ Phase 5 완료 기준**: JetBrains Marketplace에 플러그인 게시 완료

---

## 전체 타임라인

```
Phase 0 (1일)     ████
Phase 1 (2~3일)   ████████████
Phase 2 (3~4일)   ████████████████
Phase 3 (3~4일)   ████████████████
Phase 4 (3~4일)   ████████████████
Phase 5 (2~3일)   ████████████
                  ─────────────────────
                  총 예상: 14~19일 (약 3~4주)
```

---

## 의사결정 로그

개발 중 내린 결정을 기록하는 곳.

| 날짜 | 결정 | 이유 |
|------|------|------|
| 2026-03-18 | `~/.kiro/ide/` lock file 방식 대신 stdio MCP 서버 방식 채택 | kiro-cli가 lock file 기반 IDE 연동을 지원하지 않음 확인 |
| 2026-03-18 | Phase 2를 2A(터미널 직접 입력) + 2B(MCP 서버) 2단계로 분리 | 2A만으로도 기본 동작 가능, 2B는 점진적 개선 |
| | | |

---

## 참고 링크

- [IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/welcome.html)
- [Embedded Terminal API](https://plugins.jetbrains.com/docs/intellij/embedded-terminal.html)
- [Kiro CLI 문서](https://kiro.dev/docs/cli/)
- [Kiro CLI 슬래시 명령어](https://kiro.dev/docs/cli/reference/slash-commands/)
- [Kiro CLI MCP](https://kiro.dev/docs/cli/mcp)
- [Claude Code JetBrains 연동 분석](https://lattice.uptownhr.com/claude-code/ide-integration-programmatic-messaging)
