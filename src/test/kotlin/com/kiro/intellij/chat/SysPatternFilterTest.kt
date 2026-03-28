package com.kiro.intellij.chat

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ChatPanel의 시스템 로그 패턴 필터링 테스트.
 * JavaScript의 SYS_PATTERNS를 Kotlin Regex로 미러링하여 검증.
 */
class SysPatternFilterTest {

    // ChatPanel.kt의 SYS_PATTERNS를 Kotlin Regex로 미러링
    private val sysPatterns = listOf(
        Regex("^Reading file:", RegexOption.IGNORE_CASE),
        Regex("^Writing file:", RegexOption.IGNORE_CASE),
        Regex("^Running:", RegexOption.IGNORE_CASE),
        Regex("^Searching", RegexOption.IGNORE_CASE),
        Regex("^✓"), Regex("^⚡"), Regex("^\\(using tool:", RegexOption.IGNORE_CASE),
        Regex("^▸"), Regex("^⏳"), Regex("^📎"), Regex("^🔍"), Regex("^📁"), Regex("^✅"), Regex("^↱"), Regex("^⋮"),
        Regex("^Batch\\s"), Regex("^All tools are now trusted", RegexOption.IGNORE_CASE),
        Regex("^Agents can sometimes", RegexOption.IGNORE_CASE),
        Regex("^Learn more at https://", RegexOption.IGNORE_CASE),
        Regex("using tool:", RegexOption.IGNORE_CASE),
        Regex("^Operation \\d+:", RegexOption.IGNORE_CASE),
        Regex("^Loading", RegexOption.IGNORE_CASE),
        Regex("^⠋"), Regex("^⠙"), Regex("^⠹"), Regex("^⠸"), Regex("^⠼"), Regex("^⠴"), Regex("^⠦"), Regex("^⠧"), Regex("^⠇"), Regex("^⠏"),
        Regex("^::"),
        Regex("^\\d+\\.\\s+Class\\s", RegexOption.IGNORE_CASE),
        Regex("^\\d+\\.\\s+Function\\s", RegexOption.IGNORE_CASE),
        Regex("^Analyzing", RegexOption.IGNORE_CASE),
        Regex("^Scanning", RegexOption.IGNORE_CASE),
        Regex("^Processing", RegexOption.IGNORE_CASE),
        Regex("^Indexing", RegexOption.IGNORE_CASE),
        Regex("[▰▱]"),
        Regex("Opening browser", RegexOption.IGNORE_CASE),
        Regex("Logging in", RegexOption.IGNORE_CASE),
        Regex("Fetching profiles", RegexOption.IGNORE_CASE),
        Regex("^Device authorized", RegexOption.IGNORE_CASE),
        Regex("^Logged in", RegexOption.IGNORE_CASE),
        Regex("Press.*C.*cancel", RegexOption.IGNORE_CASE),
        Regex("^Found \\d+ of \\d+ symbols", RegexOption.IGNORE_CASE),
        Regex("^No matches found", RegexOption.IGNORE_CASE),
        Regex("^No files found matching", RegexOption.IGNORE_CASE),
        Regex("under current directory", RegexOption.IGNORE_CASE),
        Regex("under /Users/", RegexOption.IGNORE_CASE),
        Regex("^\\d+\\.\\s+(Class|Function|Method|Interface|Object|Property)\\s", RegexOption.IGNORE_CASE),
        Regex("^matching pattern:", RegexOption.IGNORE_CASE),
        Regex("^Searching in", RegexOption.IGNORE_CASE),
        Regex("mcp server.*not load", RegexOption.IGNORE_CASE),
        Regex("TMPDIR", RegexOption.IGNORE_CASE),
        Regex("See.*log.*for more", RegexOption.IGNORE_CASE),
    )

    private fun isSystemLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        return sysPatterns.any { it.containsMatchIn(trimmed) }
    }

    // === 시스템 로그로 분류되어야 하는 것들 ===

    @Test
    fun `spinner lines are system logs`() {
        assertTrue(isSystemLine("▰▱▱▱▱▱▱ Opening browser... | Press (^) + C to cancel"))
        assertTrue(isSystemLine("▰▰▰▱▱▱▱ Logging in..."))
        assertTrue(isSystemLine("▰▰▰▰▰▰▰ Fetching profiles..."))
    }

    @Test
    fun `tool usage lines are system logs`() {
        assertTrue(isSystemLine("Reading file: src/main/kotlin/Test.kt"))
        assertTrue(isSystemLine("Writing file: output.txt"))
        assertTrue(isSystemLine("Running: npm install"))
        assertTrue(isSystemLine("Searching for pattern"))
        assertTrue(isSystemLine("(using tool: readFile)"))
    }

    @Test
    fun `status icons are system logs`() {
        assertTrue(isSystemLine("✓ File saved"))
        assertTrue(isSystemLine("⚡ Quick action"))
        assertTrue(isSystemLine("✅ Done"))
        assertTrue(isSystemLine("▸ Processing"))
    }

    @Test
    fun `loading and progress lines are system logs`() {
        assertTrue(isSystemLine("Loading..."))
        assertTrue(isSystemLine("⠋ Loading modules"))
        assertTrue(isSystemLine("⠙ Compiling"))
        assertTrue(isSystemLine(":: Building"))
        assertTrue(isSystemLine("Analyzing code"))
        assertTrue(isSystemLine("Scanning files"))
        assertTrue(isSystemLine("Processing results"))
        assertTrue(isSystemLine("Indexing project"))
    }

    @Test
    fun `auth spinner lines are system logs`() {
        assertTrue(isSystemLine("Device authorized"))
        assertTrue(isSystemLine("Logged in successfully"))
        assertTrue(isSystemLine("▰▱▱▱▱▱▱ Opening browser... | Press (^) + C to cancel"))
    }

    @Test
    fun `class and function index lines are system logs`() {
        assertTrue(isSystemLine("1. Class AgentPanel at src/main/kotlin/AgentPanel.kt:21:1"))
        assertTrue(isSystemLine("2. Function setupUI at src/main/kotlin/Test.kt:10:5"))
    }

    // === 시스템 로그가 아닌 것들 (본문으로 표시) ===

    @Test
    fun `normal text is not system log`() {
        assertFalse(isSystemLine("Hello, how can I help you?"))
        assertFalse(isSystemLine("Here is the code you requested:"))
        assertFalse(isSystemLine("The function takes two parameters."))
    }

    @Test
    fun `code content is not system log`() {
        assertFalse(isSystemLine("fun main() {"))
        assertFalse(isSystemLine("    println(\"Hello\")"))
        assertFalse(isSystemLine("val x = 42"))
    }

    @Test
    fun `markdown content is not system log`() {
        assertFalse(isSystemLine("- First item"))
        assertFalse(isSystemLine("1. First step"))
        assertFalse(isSystemLine("**bold text**"))
        assertFalse(isSystemLine("> quote"))
    }

    @Test
    fun `empty lines are not system logs`() {
        assertFalse(isSystemLine(""))
        assertFalse(isSystemLine("   "))
    }

    @Test
    fun `table content is not system log`() {
        assertFalse(isSystemLine("| Name | Value |"))
        assertFalse(isSystemLine("|------|-------|"))
        assertFalse(isSystemLine("| foo  | bar   |"))
    }

    // === 새로 추가된 패턴 테스트 ===

    @Test
    fun `tool output lines are system logs`() {
        assertTrue(isSystemLine("Found 1 of 1 symbols:"))
        assertTrue(isSystemLine("Found 3 of 10 symbols:"))
        assertTrue(isSystemLine("No matches found for pattern: <hr>"))
        assertTrue(isSystemLine("No files found matching pattern: **/*.html under current directory"))
        assertTrue(isSystemLine("1. Interface IData at src/main/kotlin/Test.kt:5:1"))
        assertTrue(isSystemLine("2. Property name at src/main/kotlin/Test.kt:10:5"))
    }

    @Test
    fun `mcp warning lines are system logs`() {
        assertTrue(isSystemLine("One or more mcp server did not load correctly."))
        assertTrue(isSystemLine("See \$TMPDIR/kiro-log/kiro-chat.log for more details."))
    }

    @Test
    fun `trust all tools warning lines are system logs`() {
        assertTrue(isSystemLine("All tools are now trusted (!). Kiro will execute tools without asking for confirmation."))
        assertTrue(isSystemLine("Agents can sometimes do unexpected things so understand the risks."))
        assertTrue(isSystemLine("Learn more at https://kiro.dev/docs/cli/chat/security/#using-tools-trust-all-safely"))
    }

    @Test
    fun `path references are system logs`() {
        assertTrue(isSystemLine("under /Users/someone/projects/my-plugin/src/main/kotlin"))
        assertTrue(isSystemLine("matching pattern: **/*.css under current directory"))
        assertTrue(isSystemLine("Searching in /Users/someone/project"))
    }
}
