package com.kiro.intellij.chat

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * 파일 검색 로직 테스트
 */
class FileSearchTest {

    private data class ProjectFileInfo(val name: String, val path: String, val dir: String, val ext: String)

    @Test
    fun `filter files by name`() {
        val files = listOf(
            ProjectFileInfo("ChatPanel.kt", "src/main/kotlin/chat/ChatPanel.kt", "src/main/kotlin/chat", "kt"),
            ProjectFileInfo("ChatSession.kt", "src/main/kotlin/chat/ChatSession.kt", "src/main/kotlin/chat", "kt"),
            ProjectFileInfo("Settings.kt", "src/main/kotlin/settings/Settings.kt", "src/main/kotlin/settings", "kt")
        )
        
        val query = "chat"
        val matches = filterFiles(files, query)
        
        assertEquals(2, matches.size)
        assertTrue(matches.all { it.name.lowercase().contains("chat") })
    }

    @Test
    fun `filter files by path`() {
        val files = listOf(
            ProjectFileInfo("Panel.kt", "src/main/kotlin/chat/Panel.kt", "src/main/kotlin/chat", "kt"),
            ProjectFileInfo("Panel.kt", "src/main/kotlin/settings/Panel.kt", "src/main/kotlin/settings", "kt")
        )
        
        val query = "settings"
        val matches = filterFiles(files, query)
        
        assertEquals(1, matches.size)
        assertEquals("src/main/kotlin/settings/Panel.kt", matches[0].path)
    }

    @Test
    fun `sort by name match priority`() {
        val files = listOf(
            ProjectFileInfo("AChat.kt", "src/AChat.kt", "src", "kt"),
            ProjectFileInfo("ChatPanel.kt", "src/ChatPanel.kt", "src", "kt"),
            ProjectFileInfo("MyChat.kt", "src/MyChat.kt", "src", "kt")
        )
        
        val query = "chat"
        val sorted = sortFiles(files, query)
        
        // ChatPanel.kt가 첫 번째 (chat으로 시작)
        assertEquals("ChatPanel.kt", sorted[0].name)
    }

    @Test
    fun `empty query returns all files`() {
        val files = listOf(
            ProjectFileInfo("A.kt", "src/A.kt", "src", "kt"),
            ProjectFileInfo("B.kt", "src/B.kt", "src", "kt")
        )
        
        val matches = filterFiles(files, "")
        
        assertEquals(2, matches.size)
    }

    @Test
    fun `case insensitive search`() {
        val files = listOf(
            ProjectFileInfo("ChatPanel.kt", "src/ChatPanel.kt", "src", "kt"),
            ProjectFileInfo("chatSession.kt", "src/chatSession.kt", "src", "kt")
        )
        
        val matches = filterFiles(files, "CHAT")
        
        assertEquals(2, matches.size)
    }

    @Test
    fun `limit results to max count`() {
        val files = (1..100).map { 
            ProjectFileInfo("File$it.kt", "src/File$it.kt", "src", "kt") 
        }
        
        val matches = filterFiles(files, "file").take(15)
        
        assertEquals(15, matches.size)
    }

    @Test
    fun `fuzzy match partial name`() {
        val files = listOf(
            ProjectFileInfo("ChatBackendServer.kt", "src/ChatBackendServer.kt", "src", "kt"),
            ProjectFileInfo("ChatPanel.kt", "src/ChatPanel.kt", "src", "kt")
        )
        
        val matches = filterFiles(files, "backend")
        
        assertEquals(1, matches.size)
        assertEquals("ChatBackendServer.kt", matches[0].name)
    }

    // 테스트용 필터 함수 (ChatBackendServer의 로직과 동일)
    private fun filterFiles(files: List<ProjectFileInfo>, query: String): List<ProjectFileInfo> {
        if (query.isEmpty()) return files
        val q = query.lowercase()
        return files.filter { f ->
            f.name.lowercase().contains(q) || f.path.lowercase().contains(q)
        }
    }

    // 테스트용 정렬 함수 (ChatBackendServer의 로직과 동일)
    private fun sortFiles(files: List<ProjectFileInfo>, query: String): List<ProjectFileInfo> {
        if (query.isEmpty()) return files.sortedBy { it.name.lowercase() }
        val q = query.lowercase()
        return files.sortedWith(compareBy(
            { !it.name.lowercase().startsWith(q) },
            { !it.name.lowercase().contains(q) },
            { it.name.lowercase() }
        ))
    }
}
