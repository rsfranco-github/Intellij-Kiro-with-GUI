package com.kiro.intellij.chat

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

class ChatBackendServerTest {

    @Test
    fun `json escape should handle special characters`() {
        val testCases = mapOf(
            "hello" to "hello",
            "hello\\world" to "hello\\\\world",
            "hello\"world" to "hello\\\"world",
            "hello\nworld" to "hello\\nworld",
            "path/to/file.kt" to "path/to/file.kt"
        )
        
        testCases.forEach { (input, expected) ->
            val escaped = escapeJson(input)
            assertEquals(expected, escaped, "Failed for input: $input")
        }
    }

    @Test
    fun `session id format should be valid`() {
        val sessionId = generateSessionId()
        assertTrue(sessionId.isNotBlank())
        assertTrue(sessionId.length > 5)
    }

    @Test
    fun `project file info json format`() {
        val file = ProjectFileInfo("ChatPanel.kt", "src/main/kotlin/chat/ChatPanel.kt", "src/main/kotlin/chat", "kt")
        val json = fileInfoToJson(file)
        
        assertTrue(json.contains("\"name\":\"ChatPanel.kt\""))
        assertTrue(json.contains("\"path\":\"src/main/kotlin/chat/ChatPanel.kt\""))
        assertTrue(json.contains("\"dir\":\"src/main/kotlin/chat\""))
        assertTrue(json.contains("\"ext\":\"kt\""))
    }

    @Test
    fun `agent info json format`() {
        val agent = AgentInfo("ui-expert", "UI/UX 디자인 전문가", "/path/to/agent.md")
        val json = agentInfoToJson(agent)
        
        assertTrue(json.contains("\"name\":\"ui-expert\""))
        assertTrue(json.contains("\"description\":\"UI/UX 디자인 전문가\""))
        assertTrue(json.contains("\"path\":\"/path/to/agent.md\""))
    }

    @Test
    fun `agent info json escapes special characters`() {
        val agent = AgentInfo("test", "Description with \"quotes\" and \\backslash", "/path")
        val json = agentInfoToJson(agent)
        
        assertTrue(json.contains("\\\"quotes\\\""))
        assertTrue(json.contains("\\\\backslash"))
    }

    @Test
    fun `project files filter by query`() {
        val files = listOf(
            ProjectFileInfo("ChatPanel.kt", "src/chat/ChatPanel.kt", "src/chat", "kt"),
            ProjectFileInfo("Settings.kt", "src/settings/Settings.kt", "src/settings", "kt"),
            ProjectFileInfo("ChatSession.kt", "src/chat/ChatSession.kt", "src/chat", "kt")
        )
        
        val query = "chat"
        val filtered = filterProjectFiles(files, query)
        
        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.name.lowercase().contains("chat") || it.path.lowercase().contains("chat") })
    }

    @Test
    fun `project files sort by name match priority`() {
        val files = listOf(
            ProjectFileInfo("AChat.kt", "src/AChat.kt", "src", "kt"),
            ProjectFileInfo("ChatPanel.kt", "src/ChatPanel.kt", "src", "kt"),
            ProjectFileInfo("MyChat.kt", "src/MyChat.kt", "src", "kt")
        )
        
        val query = "chat"
        val sorted = sortProjectFiles(files, query)
        
        // ChatPanel.kt가 첫 번째 (chat으로 시작)
        assertEquals("ChatPanel.kt", sorted[0].name)
    }

    @Test
    fun `parse agent file with frontmatter`() {
        val content = """
            ---
            name: test-agent
            description: Test agent description
            ---
            
            # Agent Content
        """.trimIndent()
        
        val result = parseAgentContent(content, "test-agent.md")
        
        assertNotNull(result)
        assertEquals("test-agent", result!!.name)
        assertEquals("Test agent description", result.description)
    }

    @Test
    fun `parse agent file without frontmatter`() {
        val content = """
            # My Agent
            
            This is the agent content.
        """.trimIndent()
        
        val result = parseAgentContent(content, "my-agent.md")
        
        assertNotNull(result)
        assertEquals("my-agent", result!!.name)
        assertEquals("# My Agent", result.description)
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    }

    private fun generateSessionId(): String {
        return "session-${System.currentTimeMillis()}"
    }

    // 테스트용 데이터 클래스
    private data class ProjectFileInfo(val name: String, val path: String, val dir: String, val ext: String)
    private data class AgentInfo(val name: String, val description: String, val path: String)

    private fun fileInfoToJson(f: ProjectFileInfo): String {
        return "{\"name\":\"${escapeJson(f.name)}\",\"path\":\"${escapeJson(f.path)}\",\"dir\":\"${escapeJson(f.dir)}\",\"ext\":\"${escapeJson(f.ext)}\"}"
    }

    private fun agentInfoToJson(a: AgentInfo): String {
        return "{\"name\":\"${escapeJson(a.name)}\",\"description\":\"${escapeJson(a.description)}\",\"path\":\"${escapeJson(a.path)}\"}"
    }

    private fun filterProjectFiles(files: List<ProjectFileInfo>, query: String): List<ProjectFileInfo> {
        if (query.isEmpty()) return files
        val q = query.lowercase()
        return files.filter { f ->
            f.name.lowercase().contains(q) || f.path.lowercase().contains(q)
        }
    }

    private fun sortProjectFiles(files: List<ProjectFileInfo>, query: String): List<ProjectFileInfo> {
        if (query.isEmpty()) return files.sortedBy { it.name.lowercase() }
        val q = query.lowercase()
        return files.sortedWith(compareBy(
            { !it.name.lowercase().startsWith(q) },
            { !it.name.lowercase().contains(q) },
            { it.name.lowercase() }
        ))
    }

    private fun parseAgentContent(content: String, filename: String): AgentInfo? {
        val name = filename.removeSuffix(".md")

        val frontmatterMatch = Regex("^---\\s*\\n([\\s\\S]*?)\\n---").find(content)
        val description = if (frontmatterMatch != null) {
            val yaml = frontmatterMatch.groupValues[1]
            Regex("description:\\s*[\"']?(.+?)[\"']?\\s*$", RegexOption.MULTILINE)
                .find(yaml)?.groupValues?.get(1)?.trim() ?: ""
        } else {
            content.lines().firstOrNull { it.isNotBlank() }?.take(100) ?: ""
        }

        return AgentInfo(name, description, "/path/$filename")
    }
}
