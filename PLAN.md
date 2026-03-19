# Kiro with GUI — IntelliJ Plugin 구현 계획

## 1. 개요

"Claude Code with GUI" 플러그인과 동일한 아키텍처로, `kiro-cli`를 IntelliJ IDE 내에 GUI로 통합하는 플러그인.
IDE의 Tool Window에 터미널을 임베딩하여 kiro-cli를 실행하고, IDE의 diff viewer, diagnostic, selection context 등과 연동한다.

### 참고 아키텍처 (Claude Code JetBrains Plugin)

```
┌─────────────────────────────────────────┐
│   JetBrains IDE (IntelliJ, WebStorm)    │
│                                         │
│  ┌────────────────────────────────┐     │
│  │  Plugin (WebSocket MCP Server) │     │
│  │  Port: random 10000-65535      │     │
│  └──────────┬─────────────────────┘     │
│             │                            │
│             │ Lock file: ~/.kiro/ide/[port].lock
│             │                            │
│  ┌──────────▼─────────────────────┐     │
│  │  kiro-cli (WebSocket Client)   │     │
│  │  - Lock file로 IDE 발견        │     │
│  │  - WebSocket 연결              │     │
│  └────────────────────────────────┘     │
└─────────────────────────────────────────┘
```

**핵심 원리**: 플러그인이 WebSocket MCP 서버 역할을 하고, kiro-cli가 클라이언트로 연결한다.

### ⚠️ kiro-cli IDE 연동 프로토콜 현황

`~/.kiro/ide/` 디렉토리가 존재하지 않음 → kiro-cli가 아직 Claude Code 방식의 lock file 기반 IDE 연동을 지원하지 않을 가능성이 높다.

**대응 전략 (2단계)**:
1. **Phase 1~3**: 터미널 직접 임베딩 방식으로 구현 (lock file 불필요, 터미널 입출력으로 통신)
2. **Phase 2+**: 플러그인을 kiro-cli의 MCP 서버로 등록 (`kiro-cli mcp add`)하여 IDE 도구를 kiro-cli에 노출. WebSocket이 아닌 stdio 기반 MCP 서버로 구현하면 lock file 없이도 연동 가능.

```
[대안 아키텍처: stdio MCP 서버 방식]

┌─────────────────────────────────────────┐
│   JetBrains IDE                         │
│                                         │
│  ┌────────────────────────────────┐     │
│  │  Kiro Tool Window              │     │
│  │  (임베딩된 터미널에서           │     │
│  │   kiro-cli chat 실행)          │     │
│  └────────────────────────────────┘     │
│                                         │
│  ┌────────────────────────────────┐     │
│  │  IDE MCP Server (stdio)        │     │
│  │  - kiro-cli mcp add로 등록     │     │
│  │  - getDiagnostics, openDiff 등 │     │
│  │    IDE 도구를 MCP tool로 노출  │     │
│  └────────────────────────────────┘     │
└─────────────────────────────────────────┘
```

---

## 2. 기술 스택

| 항목 | 선택 |
|------|------|
| 언어 | Kotlin |
| 빌드 | Gradle + IntelliJ Platform Plugin (2.x) |
| 최소 IDE 버전 | 2025.1 (build 251.*) |
| 터미널 | Reworked Terminal API (`org.jetbrains.plugins.terminal`) |
| WebSocket | Ktor Server (Netty) 또는 Java-WebSocket |
| 프로토콜 | JSON-RPC 2.0 over WebSocket (MCP 호환) |
| 직렬화 | kotlinx.serialization |

---

## 3. 프로젝트 구조

```
kiro-with-gui/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── src/main/
│   ├── kotlin/com/kiro/intellij/
│   │   ├── KiroPlugin.kt                    # Plugin 진입점
│   │   ├── toolwindow/
│   │   │   ├── KiroToolWindowFactory.kt      # Tool Window 생성
│   │   │   └── KiroTerminalRunner.kt         # kiro-cli 프로세스 관리
│   │   ├── mcp/
│   │   │   ├── McpWebSocketServer.kt         # WebSocket MCP 서버
│   │   │   ├── McpProtocol.kt                # JSON-RPC 2.0 메시지 정의
│   │   │   ├── McpToolHandler.kt             # MCP 도구 핸들러 (IDE → kiro)
│   │   │   └── McpLockFile.kt                # Lock file 관리
│   │   ├── actions/
│   │   │   ├── OpenKiroAction.kt             # 단축키로 Kiro 열기
│   │   │   ├── SendSelectionAction.kt        # 선택 텍스트 전송
│   │   │   └── InsertFileRefAction.kt        # @File#L1-99 참조 삽입
│   │   ├── diff/
│   │   │   └── KiroDiffHandler.kt            # IDE diff viewer 연동
│   │   ├── diagnostics/
│   │   │   └── DiagnosticProvider.kt         # IDE diagnostic 공유
│   │   ├── context/
│   │   │   ├── SelectionTracker.kt           # 에디터 선택 추적
│   │   │   └── OpenEditorsTracker.kt         # 열린 탭 추적
│   │   ├── skills/
│   │   │   └── SkillManagerPanel.kt          # Skill 관리 UI 패널
│   │   └── settings/
│   │       ├── KiroSettingsConfigurable.kt   # 설정 화면
│   │       └── KiroSettings.kt               # 설정 상태 저장
│   └── resources/
│       ├── META-INF/
│       │   └── plugin.xml                    # 플러그인 설정
│       └── icons/
│           └── kiro.svg                      # 플러그인 아이콘
└── src/test/
    └── kotlin/com/kiro/intellij/
        └── ...
```

---

## 4. 핵심 컴포넌트 상세

### 4.1 Tool Window + 터미널 임베딩

플러그인의 메인 UI. IDE 하단 또는 우측에 "Kiro" Tool Window를 생성하고, 그 안에 kiro-cli를 실행하는 터미널을 임베딩한다.

**구현 방식**: IntelliJ의 Reworked Terminal API 사용

```kotlin
// KiroToolWindowFactory.kt 핵심 로직
class KiroToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val tabsManager = TerminalToolWindowTabsManager.getInstance(project)
        tabsManager.createTabBuilder()
            .withShellCommand(getKiroCommand())  // "kiro-cli chat"
            .withTabName("Kiro")
            .build()
    }
}
```

**kiro-cli 실행 옵션**:
- 기본 명령: `kiro-cli chat`
- 프로젝트 루트를 working directory로 설정
- 환경변수로 IDE 연동 정보 전달

### 4.2 WebSocket MCP 서버

kiro-cli가 IDE와 통신하기 위한 MCP 서버. Claude Code 플러그인과 동일한 프로토콜을 사용한다.

**Lock File** (`~/.kiro/ide/[port].lock`):
```json
{
  "pid": 12345,
  "workspaceFolders": ["/Users/hiseo/project"],
  "ideName": "IntelliJ IDEA",
  "transport": "ws",
  "authToken": "uuid-v4-token"
}
```

**서버 시작 흐름**:
1. 플러그인 로드 시 랜덤 포트(10000-65535)에서 WebSocket 서버 시작
2. Lock file 생성 (authToken = UUID v4)
3. kiro-cli가 `~/.kiro/ide/` 스캔하여 lock file 발견
4. `x-kiro-ide-authorization: [authToken]` 헤더로 연결

### 4.3 MCP 도구 (IDE → kiro-cli 제공)

kiro-cli가 호출할 수 있는 IDE 도구들:

| 도구 | 설명 |
|------|------|
| `getCurrentSelection` | 현재 에디터 선택 텍스트 반환 |
| `getLatestSelection` | 최근 선택 (50ms debounce) |
| `getDiagnostics` | lint 에러, 경고, 타입 에러 반환 |
| `getOpenEditors` | 열린 탭 목록 + 메타데이터 |
| `getWorkspaceFolders` | 프로젝트 루트 경로 |
| `openFile` | 파일 열기 (라인 선택 옵션) |
| `openDiff` | IDE diff viewer에 변경사항 표시 (blocking, 사용자 승인 대기) |
| `saveDocument` | 파일 저장 |
| `checkDocumentDirty` | 미저장 변경 확인 |
| `closeTab` | 에디터 탭 닫기 |
| `closeAllDiffTabs` | diff 뷰 모두 닫기 |
| `executeCode` | IDE에서 코드 실행 |

**IDE → kiro-cli 이벤트**:

| 이벤트 | 트리거 | 설명 |
|--------|--------|------|
| `selection_changed` | 자동 (50ms debounce) | 텍스트 선택 변경 시 |
| `at_mentioned` | `Cmd+Option+K` | 파일 참조 삽입 |

### 4.4 Diff Viewer 연동

kiro-cli가 파일 수정을 제안할 때, IDE의 내장 diff viewer로 표시한다.

**흐름**:
1. kiro-cli가 `openDiff` MCP 도구 호출 (원본 + 수정본 전달)
2. 플러그인이 `DiffManager`로 diff 에디터 열기
3. 사용자가 Accept/Reject 선택
4. 결과를 kiro-cli에 JSON-RPC response로 반환

```kotlin
// KiroDiffHandler.kt 핵심 로직
fun openDiff(filePath: String, originalContent: String, modifiedContent: String): CompletableFuture<Boolean> {
    val request = SimpleDiffRequest(
        "Kiro: $filePath",
        DiffContentFactory.getInstance().create(originalContent),
        DiffContentFactory.getInstance().create(modifiedContent),
        "Original", "Kiro Suggestion"
    )
    // diff 에디터 열고 사용자 응답 대기
    DiffManager.getInstance().showDiff(project, request)
    return userResponseFuture
}
```

### 4.5 Diagnostic 공유

IDE의 코드 분석 결과(lint, 타입 에러 등)를 kiro-cli에 자동 공유.

```kotlin
// DiagnosticProvider.kt
fun getDiagnostics(filePath: String?): List<DiagnosticInfo> {
    val analysisScope = if (filePath != null) {
        AnalysisScope(psiFile)
    } else {
        AnalysisScope(project)
    }
    // InspectionManager를 통해 현재 문제점 수집
    return DaemonCodeAnalyzer.getInstance(project)
        .getHighlights(document, HighlightSeverity.WARNING)
        .map { DiagnosticInfo(it.severity, it.description, it.startOffset, it.endOffset) }
}
```

### 4.6 Selection Context 추적

에디터에서 선택한 텍스트와 현재 열린 파일 정보를 자동으로 kiro-cli에 전달.

```kotlin
// SelectionTracker.kt
class SelectionTracker(private val project: Project) {
    init {
        EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
            override fun editorCreated(event: EditorFactoryEvent) {
                event.editor.selectionModel.addSelectionListener(object : SelectionListener {
                    override fun selectionChanged(e: SelectionEvent) {
                        // 50ms debounce 후 WebSocket으로 selection_changed 이벤트 전송
                        debounce(50) { sendSelectionChanged(e) }
                    }
                })
            }
        }, project)
    }
}
```

---

## 5. MCP / Skill / Powers 관리 기능

### 5.1 MCP 서버 관리 패널

Tool Window 내에 MCP 서버 상태를 보여주는 패널 추가.

**기능**:
- 현재 로드된 MCP 서버 목록 표시 (kiro-cli의 `/mcp` 결과 파싱)
- MCP 서버 추가/제거 UI (내부적으로 `kiro-cli mcp add/remove` 실행)
- 서버 상태 모니터링 (running/stopped/error)
- `~/.kiro/settings/mcp.json` 또는 `.kiro/settings/mcp.json` 직접 편집 버튼

**구현**: 터미널에 `/mcp` 명령을 보내고 출력을 파싱하거나, mcp.json 파일을 직접 읽어서 UI에 표시.

### 5.2 Skill 관리

kiro-cli의 Agent Skill을 IDE에서 관리.

**기능**:
- 사용 가능한 skill 목록 표시
- skill 활성화/비활성화
- 커스텀 skill 생성 (`/prompts create` 연동)
- skill 설명 및 트리거 조건 표시

**구현**: `/tools` 명령 결과 파싱 + `.kiro/agents/` 디렉토리의 agent config 파일 직접 편집.

### 5.3 Powers 관리

kiro-cli의 Powers 기능 연동.

**기능**:
- 설치된 Powers 목록 표시
- Power 활성화/비활성화 토글
- Power가 제공하는 도구/워크플로우 확인

---

## 6. 설정 (Settings)

`Settings → Tools → Kiro` 에서 설정 가능:

| 설정 | 기본값 | 설명 |
|------|--------|------|
| Kiro command | `kiro-cli` | kiro-cli 실행 경로 |
| Default model | `Auto` | 기본 모델 (Auto, Claude Opus 4.6, Opus 4.5, Sonnet 4.5, Sonnet 4.0, Haiku 4.5) |
| Auto-start | `true` | IDE 시작 시 자동으로 Kiro Tool Window 열기 |
| Diff tool | `auto` | diff 표시 방식 (auto/ide/terminal) |
| Selection sharing | `true` | 선택 텍스트 자동 공유 |
| Diagnostic sharing | `true` | IDE diagnostic 자동 공유 |
| MCP config scope | `workspace` | MCP 설정 범위 (workspace/global) |

---

## 7. 단축키

| 단축키 (Mac) | 단축키 (Win/Linux) | 동작 |
|-------------|-------------------|------|
| `Cmd+Esc` | `Ctrl+Esc` | Kiro Tool Window 열기/포커스 |
| `Cmd+Option+K` | `Ctrl+Alt+K` | 파일 참조 삽입 (@File#L1-99) |
| `Cmd+Shift+K` | `Ctrl+Shift+K` | 선택 텍스트를 Kiro에 전송 |

---

## 8. plugin.xml 구조

```xml
<idea-plugin>
    <id>com.kiro.intellij</id>
    <name>Kiro with GUI</name>
    <vendor>Kiro Community</vendor>
    <description>Kiro CLI integration with GUI for JetBrains IDEs</description>

    <depends>com.intellij.modules.platform</depends>
    <depends>org.jetbrains.plugins.terminal</depends>

    <extensions defaultExtensionNs="com.intellij">
        <toolWindow id="Kiro"
                    anchor="bottom"
                    factoryClass="com.kiro.intellij.toolwindow.KiroToolWindowFactory"
                    icon="/icons/kiro.svg"/>
        <applicationConfigurable
                    instance="com.kiro.intellij.settings.KiroSettingsConfigurable"
                    displayName="Kiro"
                    parentId="tools"/>
        <postStartupActivity
                    implementation="com.kiro.intellij.KiroStartupActivity"/>
    </extensions>

    <extensions defaultExtensionNs="org.jetbrains.plugins.terminal">
        <allowedActionsProvider
                    implementation="com.kiro.intellij.actions.KiroTerminalAllowedActionsProvider"/>
    </extensions>

    <actions>
        <action id="Kiro.Open"
                class="com.kiro.intellij.actions.OpenKiroAction"
                text="Open Kiro"
                description="Open Kiro chat window">
            <keyboard-shortcut first-keystroke="meta ESCAPE" keymap="$default"/>
            <keyboard-shortcut first-keystroke="ctrl ESCAPE" keymap="$default"/>
        </action>
        <action id="Kiro.InsertFileRef"
                class="com.kiro.intellij.actions.InsertFileRefAction"
                text="Insert File Reference to Kiro"
                description="Insert @File#Line reference">
            <keyboard-shortcut first-keystroke="meta alt K" keymap="$default"/>
            <keyboard-shortcut first-keystroke="ctrl alt K" keymap="$default"/>
        </action>
        <action id="Kiro.SendSelection"
                class="com.kiro.intellij.actions.SendSelectionAction"
                text="Send Selection to Kiro"
                description="Send selected text to Kiro">
            <keyboard-shortcut first-keystroke="meta shift K" keymap="$default"/>
            <keyboard-shortcut first-keystroke="ctrl shift K" keymap="$default"/>
        </action>
    </actions>
</idea-plugin>
```

---

## 9. 구현 순서 (Phase)

### Phase 1: 기본 동작 (MVP)
1. Gradle 프로젝트 셋업 (IntelliJ Platform Plugin 2.x)
2. Tool Window + 터미널 임베딩 (`kiro-cli chat` 실행)
3. 모델 선택 드롭다운 (Tool Window 상단 툴바, `/model` 명령 연동)
4. 설정 화면 (kiro-cli 경로, 기본 모델 설정)
5. `Cmd+Esc` 단축키로 Tool Window 열기

### Phase 2: IDE 연동
5. WebSocket MCP 서버 구현
6. Lock file 생성/관리
7. Selection context 추적 + `selection_changed` 이벤트
8. `Cmd+Option+K` 파일 참조 삽입 (`at_mentioned` 이벤트)
9. Diagnostic 공유 (`getDiagnostics` 도구)

### Phase 3: Diff + 고급 기능
10. `openDiff` 도구 → IDE diff viewer 연동
11. `openFile`, `saveDocument`, `closeTab` 등 나머지 MCP 도구
12. 열린 에디터 추적 (`getOpenEditors`)

### Phase 4: MCP / Skill / Powers 관리
13. MCP 서버 관리 패널 (목록, 추가/제거, 상태)
14. Skill 관리 UI (목록, 활성화/비활성화)
15. Powers 관리 UI
16. mcp.json / agent config 직접 편집 연동

### Phase 5: 마무리
17. 아이콘, UI 폴리싱
18. 에러 핸들링 (kiro-cli 미설치, 인증 실패 등)
19. JetBrains Marketplace 배포 준비

---

## 10. 테스트 전략

### 10.1 테스트 종류

| 종류 | 대상 | 도구 |
|------|------|------|
| Unit Test | MCP 프로토콜 파싱, Lock file 생성, 설정 로직 | JUnit 5 + kotlin.test |
| Integration Test | IDE API 연동 (Tool Window, Diff, Diagnostic) | IntelliJ Test Framework (`HeavyPlatformTestCase`) |
| UI Test | Tool Window 렌더링, 설정 화면, 단축키 | IntelliJ Remote Robot |
| E2E Test | kiro-cli 실제 실행 + IDE 연동 전체 흐름 | 수동 테스트 (자동화 어려움) |

### 10.2 Unit Test

IDE 의존성 없는 순수 로직 테스트. 빠르게 실행 가능.

```kotlin
// McpProtocolTest.kt — JSON-RPC 메시지 파싱 테스트
class McpProtocolTest {
    @Test
    fun `parse tool call request`() {
        val json = """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"getDiagnostics"}}"""
        val request = McpProtocol.parse(json)
        assertEquals("tools/call", request.method)
    }
}

// McpLockFileTest.kt — Lock file 생성/삭제 테스트
class McpLockFileTest {
    @Test
    fun `create and cleanup lock file`() {
        val lockFile = McpLockFile.create(port = 12345, workspaceFolders = listOf("/tmp/test"))
        assertTrue(lockFile.path.exists())
        lockFile.cleanup()
        assertFalse(lockFile.path.exists())
    }
}
```

**실행**: `./gradlew test`

### 10.3 Integration Test (IDE 환경)

IntelliJ Platform Test Framework를 사용하여 실제 IDE 환경에서 테스트.

```kotlin
// KiroToolWindowTest.kt
class KiroToolWindowTest : HeavyPlatformTestCase() {
    fun `test tool window is registered`() {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Kiro")
        assertNotNull(toolWindow)
    }

    fun `test diagnostic provider returns results`() {
        // 테스트 파일에 의도적 에러 삽입
        val file = myFixture.configureByText("Test.java", "public class Test { int x = \"wrong\"; }")
        val diagnostics = DiagnosticProvider(project).getDiagnostics(file.virtualFile.path)
        assertTrue(diagnostics.isNotEmpty())
    }
}
```

**실행**: `./gradlew integrationTest` (별도 task 구성 필요)

이 테스트는 실제 IntelliJ 인스턴스를 headless로 띄워서 실행하므로 느림 (1회 30초~2분).

### 10.4 UI Test (Remote Robot)

IntelliJ Remote Robot으로 실제 UI 인터랙션 테스트.

```kotlin
// KiroUiTest.kt
class KiroUiTest {
    @Test
    fun `open kiro tool window via shortcut`() {
        with(remoteRobot) {
            // Cmd+Esc 단축키 시뮬레이션
            keyboard { hotKey(KeyEvent.VK_META, KeyEvent.VK_ESCAPE) }
            // Kiro Tool Window가 열렸는지 확인
            find<JLabelFixture>(byText("Kiro")).isShowing
        }
    }
}
```

**실행**: IDE를 실제로 띄운 상태에서 `./gradlew uiTest`

### 10.5 수동 E2E 테스트 체크리스트

Phase별로 수동 확인할 항목:

**Phase 1 체크리스트**:
- [ ] IDE 시작 시 Kiro Tool Window가 하단에 표시되는가
- [ ] Tool Window 클릭 시 kiro-cli chat이 실행되는가
- [ ] kiro-cli에 메시지를 입력하고 응답을 받을 수 있는가
- [ ] `Cmd+Esc`로 Tool Window를 열고 닫을 수 있는가
- [ ] Settings → Tools → Kiro에서 kiro-cli 경로를 변경할 수 있는가
- [ ] kiro-cli가 설치되지 않은 경우 에러 메시지가 표시되는가

**Phase 2 체크리스트**:
- [ ] 에디터에서 텍스트 선택 시 kiro-cli에 컨텍스트가 전달되는가
- [ ] `Cmd+Option+K`로 @File#L1-99 참조가 삽입되는가
- [ ] IDE의 lint 에러가 kiro-cli에 공유되는가

**Phase 3 체크리스트**:
- [ ] kiro-cli의 파일 수정 제안이 IDE diff viewer에 표시되는가
- [ ] diff에서 Accept/Reject가 정상 동작하는가

**Phase 4 체크리스트**:
- [ ] MCP 서버 목록이 패널에 표시되는가
- [ ] MCP 서버 추가/제거가 동작하는가
- [ ] Skill 목록이 표시되고 활성화/비활성화가 되는가

---

## 11. 플러그인 적용 방법 (개발 & 배포)

### 11.1 개발 중 테스트 실행 (가장 자주 사용)

```bash
# 프로젝트 루트에서 실행
./gradlew runIde
```

이 명령은:
1. 플러그인을 빌드
2. 별도의 IntelliJ IDEA 인스턴스(sandbox)를 실행
3. 빌드된 플러그인이 자동으로 설치된 상태로 IDE가 열림
4. 코드 수정 후 다시 `./gradlew runIde`하면 변경사항 반영

**핫 리로드**: `./gradlew runIde`는 매번 IDE를 재시작해야 함. 빠른 반복을 위해:
```bash
# 빌드만 하고 이미 실행 중인 sandbox IDE에서 수동 리로드
./gradlew buildPlugin
# sandbox IDE에서: File → Invalidate Caches → Restart
```

### 11.2 로컬 설치 (내 IntelliJ에 직접 설치)

```bash
# 1. 플러그인 zip 빌드
./gradlew buildPlugin

# 2. 빌드 결과물 위치
ls build/distributions/
# → kiro-with-gui-0.1.0.zip

# 3. IntelliJ에서 설치
# Settings → Plugins → ⚙️ → Install Plugin from Disk...
# → kiro-with-gui-0.1.0.zip 선택
# → IDE 재시작
```

### 11.3 디버깅

```bash
# 디버그 모드로 sandbox IDE 실행
./gradlew runIde --debug-jvm

# IntelliJ에서 Remote JVM Debug 설정:
# Run → Edit Configurations → + → Remote JVM Debug
# Host: localhost, Port: 5005
# → Debug 버튼 클릭하여 연결
```

브레이크포인트를 걸고 플러그인 코드를 디버깅할 수 있음.

### 11.4 다른 IDE에서 테스트

```bash
# PyCharm에서 테스트
./gradlew runIde -PalternativeIdePath=/Applications/PyCharm.app/Contents

# WebStorm에서 테스트
./gradlew runIde -PalternativeIdePath=/Applications/WebStorm.app/Contents
```

또는 `build.gradle.kts`에서 타겟 IDE 변경:
```kotlin
intellijPlatform {
    // IntelliJ 대신 PyCharm으로 변경
    pycharmCommunity("2025.1")
}
```

### 11.5 JetBrains Marketplace 배포

```bash
# 1. 배포용 빌드
./gradlew buildPlugin

# 2. 플러그인 검증
./gradlew verifyPlugin

# 3. Marketplace 업로드
./gradlew publishPlugin
# → gradle.properties에 PUBLISH_TOKEN 설정 필요
```

**Marketplace 배포 전 준비**:
1. [JetBrains Marketplace](https://plugins.jetbrains.com/) 계정 생성
2. Plugin Upload Token 발급 (Marketplace → Profile → API Token)
3. `gradle.properties`에 추가:
   ```properties
   pluginSigningEnabled=true
   publishToken=your-marketplace-token
   ```
4. plugin.xml에 `<description>`, `<change-notes>`, `<vendor>` 정보 완성
5. `./gradlew verifyPlugin`으로 호환성 검증 통과 확인

### 11.6 개발 워크플로우 요약

```
코드 수정 → ./gradlew runIde → sandbox IDE에서 테스트
    ↓ (만족)
./gradlew test → Unit Test 통과 확인
    ↓
./gradlew buildPlugin → zip 생성
    ↓
내 IDE에 Install from Disk → 실제 환경 테스트
    ↓ (릴리스 준비 완료)
./gradlew verifyPlugin → 호환성 검증
    ↓
./gradlew publishPlugin → Marketplace 배포
```

---

## 12. build.gradle.kts 기본 구조 (테스트 포함)

```kotlin
plugins {
    id("org.jetbrains.intellij.platform") version "2.5.0"
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
}

group = "com.kiro.intellij"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.1")
        bundledPlugin("org.jetbrains.plugins.terminal")
        testFramework(TestFrameworkType.Platform)
    }
    implementation("io.ktor:ktor-server-netty:3.1.1")
    implementation("io.ktor:ktor-server-websockets:3.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))
}

intellijPlatform {
    pluginConfiguration {
        id = "com.kiro.intellij"
        name = "Kiro with GUI"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "251"
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
}
```

---

## 13. 주의사항 / 리스크

| 리스크 | 대응 |
|--------|------|
| kiro-cli가 `~/.kiro/ide/` lock file을 인식하지 않음 (확인됨) | stdio 기반 MCP 서버로 구현. `kiro-cli mcp add`로 플러그인의 MCP 서버를 등록하여 IDE 도구 노출 |
| Reworked Terminal API가 2025.3부터 제공 | 2025.1~2025.2는 Classic Terminal API 사용, 분기 처리 |
| kiro-cli의 MCP 프로토콜이 Claude Code와 다를 수 있음 | kiro-cli 소스 또는 문서에서 정확한 프로토콜 확인 필요 |
| WebSocket 보안 (CVE-2025-52882 참고) | 반드시 UUID v4 authToken 인증 적용 |
| sandbox IDE에서는 kiro-cli 인증이 안 될 수 있음 | sandbox IDE 실행 시 `~/.kiro` 설정 디렉토리를 공유하도록 JVM args 설정 |

---

## 14. 검증이 필요한 사항

플러그인 개발 전에 확인해야 할 것들:

1. **kiro-cli IDE 연동 프로토콜**: `kiro-cli`가 `~/.kiro/ide/` lock file을 스캔하는지, 아니면 다른 경로를 사용하는지 확인
   ```bash
   # lock file 경로 확인
   ls -la ~/.kiro/ide/
   # kiro-cli에 /ide 명령이 있는지 확인
   kiro-cli chat
   > /help --legacy /ide
   ```

2. **MCP 프로토콜 호환성**: kiro-cli가 사용하는 WebSocket MCP 프로토콜이 Claude Code와 동일한 JSON-RPC 2.0인지 확인

3. **Terminal API 가용성**: 사용 중인 IntelliJ 버전에서 어떤 Terminal API를 사용할 수 있는지 확인
   ```bash
   # IntelliJ 버전 확인
   # Help → About
   ```
