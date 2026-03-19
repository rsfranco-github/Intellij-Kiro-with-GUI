# Kiro with GUI — 프로젝트 상태 (2026-03-19)

## 현재 상태: Phase 4 완료 + 채팅 UI 전환 완료

### 완료된 작업

**Phase 0**: 프로젝트 셋업
- Gradle + IntelliJ Platform Plugin 2.x, Kotlin 2.1, kotlinx.serialization
- 최소 IDE 버전: 2025.1 (build 251)

**Phase 1**: Tool Window + 기본 기능
- 우측 Tool Window ("Kiro")
- Settings → Tools → Kiro (kiro-cli 경로, 기본 모델, auto-start)
- `Cmd+Esc` 단축키로 Tool Window 열기

**Phase 2A**: IDE 컨텍스트 연동 (터미널 직접 입력)
- `Cmd+Shift+K`: 선택 코드를 채팅에 전송
- `Cmd+Option+K`: @파일경로#L1-99 참조 삽입
- 에디터 우클릭 메뉴에도 추가

**Phase 2B**: stdio MCP 서버
- TCP 서버 + Node.js bridge 스크립트 (src/main/resources/mcp/bridge.js)
- MCP 도구: getCurrentSelection, getOpenEditors, getWorkspaceFolders, getDiagnostics, openFile
- `kiro-cli mcp add`로 자동 등록, 종료 시 자동 해제

**Phase 3**: Diff viewer 연동
- KiroDiffHandler: IDE diff viewer로 변경사항 표시
- openDiff MCP 도구: 원본/수정본 비교 또는 git HEAD 비교
- openFile MCP 도구: 파일 열기 + 라인 이동

**Phase 4**: MCP/Skills/Agent 관리 UI
- Manage 탭 내 MCP/Skills/Agent 서브탭
- MCP: mcp.json 읽기, 서버 추가/제거, 직접 편집
- Skills: trust/untrust 토글
- Agent: 목록 표시, swap

**채팅 UI 전환** (터미널 → 커스텀 UI):
- JCEF 기반 말풍선 채팅 UI (내 메시지: 오른쪽 파란색, LLM: 왼쪽 회색)
- Kotlin 내장 HTTP 서버 + SSE 스트리밍
- 매 메시지마다 `kiro-cli chat --no-interactive --wrap never` 실행
- `--resume`으로 멀티턴 대화 유지
- marked.js로 마크다운 렌더링 (코드 블록, 리스트 등)
- 멀티 탭 채팅 (톱니바퀴 → New Chat, X로 닫기)
- 입력 중 비활성화 → 응답 완료 후 활성화

### 남은 작업

**Phase 5: 폴리싱 & 배포**
- [ ] 아이콘 제작 (현재 임시 SVG)
- [ ] 에러 핸들링 강화 (kiro-cli 미설치, 인증 만료 등)
- [ ] 다중 프로젝트 지원
- [ ] 성능 최적화
- [ ] README.md 작성
- [ ] JetBrains Marketplace 배포

**알려진 이슈**
- `~/.kiro/ide/` 디렉토리 미존재 → kiro-cli가 lock file 기반 IDE 연동 미지원
  → 대안: stdio MCP 서버 방식으로 구현함
- kiro-cli `--no-interactive`는 JSON 출력 미지원 → ANSI strip으로 처리
- Terminal API 버전 분기: 2025.3+는 Reworked Terminal API, 2025.1~2는 Classic

### 아키텍처

```
사용자 입력 → JCEF webview (HTML/JS)
    → fetch POST /api/send → Kotlin HTTP 서버 (ChatBackendServer, 127.0.0.1:랜덤포트)
    → ChatSession → KiroCliProcess
    → kiro-cli chat --no-interactive --resume "메시지"
    → 실시간 출력 → SSE event: chunk → webview 말풍선 업데이트
    → 프로세스 종료 → SSE event: done → 입력 활성화
```

### 주요 파일

```
src/main/kotlin/com/kiro/intellij/
├── chat/
│   ├── ChatBackendServer.kt    # 내장 HTTP 서버 + SSE
│   ├── ChatMessage.kt          # 메시지 데이터 클래스
│   ├── ChatPanel.kt            # JCEF 채팅 UI + HTML
│   ├── ChatSession.kt          # CLI 프로세스 + SSE 연결
│   └── KiroCliProcess.kt       # kiro-cli 실행, ANSI strip
├── mcp/
│   ├── McpProtocol.kt          # JSON-RPC 데이터 클래스
│   ├── McpServer.kt            # TCP MCP 서버 + kiro-cli 등록
│   └── McpToolHandler.kt       # IDE 도구 구현
├── diff/
│   └── KiroDiffHandler.kt      # IDE diff viewer 연동
├── actions/
│   ├── OpenKiroAction.kt       # Cmd+Esc
│   ├── SendSelectionAction.kt  # Cmd+Shift+K
│   ├── InsertFileRefAction.kt  # Cmd+Option+K
│   └── KiroToolWindowUtil.kt   # 유틸
├── toolwindow/
│   ├── KiroToolWindowFactory.kt # Tool Window + 멀티 탭
│   └── KiroManagePanel.kt      # MCP/Skills/Agent 관리
└── settings/
    ├── KiroSettings.kt          # 설정 상태
    └── KiroSettingsConfigurable.kt # Settings UI
```

### Git 커밋 히스토리

```
049b5e9 Phase 0: project scaffolding
1a95f1e Phase 1: tool window with terminal embedding, model dropdown, settings, shortcut
05d14ea Phase 1: fix memory leak, use ShellTerminalWidget API for 2025.1
a18fe6a Phase 2A: send selection, insert file ref, right-click menu
f773764 move tool window to right side
234ed51 Phase 2B: stdio MCP server with TCP bridge, tool handler, auto-register
b65feaa Phase 3: diff viewer handler, openDiff MCP tool
7fcc9b0 Phase 4: MCP/Skills/Agent management UI tabs
afd0dc9 Chat UI: replace terminal with bubble chat UI (JCEF), multi-tab, markdown
5217383 fix: replace long-running process with per-message execution + --resume
5795c48 Kotlin backend: embedded HTTP server + SSE streaming, proper multi-turn chat
```

### 참고 문서
- PLAN.md: 전체 구현 계획
- TODO.md: 단계별 상세 TODO 리스트
