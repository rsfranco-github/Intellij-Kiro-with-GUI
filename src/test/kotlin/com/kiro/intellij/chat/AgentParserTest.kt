package com.kiro.intellij.chat

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * 에이전트 파일 파싱 테스트
 */
class AgentParserTest {

    @Test
    fun `parse frontmatter with description`() {
        val content = """
            ---
            name: test-agent
            description: This is a test agent for code review
            ---
            
            # Test Agent
            
            This agent helps with code review.
        """.trimIndent()
        
        val result = parseAgentContent(content, "test-agent.md")
        
        assertNotNull(result)
        assertEquals("test-agent", result!!.name)
        assertEquals("This is a test agent for code review", result.description)
    }

    @Test
    fun `parse frontmatter with quoted description`() {
        val content = """
            ---
            name: ui-expert
            description: "LLM GUI 중심의 IntelliJ 플러그인 UI/UX 디자인 전문가"
            ---
            
            Content here
        """.trimIndent()
        
        val result = parseAgentContent(content, "ui-expert.md")
        
        assertNotNull(result)
        assertEquals("ui-expert", result!!.name)
        assertEquals("LLM GUI 중심의 IntelliJ 플러그인 UI/UX 디자인 전문가", result.description)
    }

    @Test
    fun `parse without frontmatter uses first line`() {
        val content = """
            # Code Reviewer Agent
            
            This agent reviews code for best practices.
        """.trimIndent()
        
        val result = parseAgentContent(content, "code-reviewer.md")
        
        assertNotNull(result)
        assertEquals("code-reviewer", result!!.name)
        assertEquals("# Code Reviewer Agent", result.description)
    }

    @Test
    fun `parse empty content`() {
        val content = ""
        
        val result = parseAgentContent(content, "empty.md")
        
        assertNotNull(result)
        assertEquals("empty", result!!.name)
        assertEquals("", result.description)
    }

    @Test
    fun `parse frontmatter without description`() {
        val content = """
            ---
            name: simple-agent
            version: 1.0
            ---
            
            Agent content
        """.trimIndent()
        
        val result = parseAgentContent(content, "simple-agent.md")
        
        assertNotNull(result)
        assertEquals("simple-agent", result!!.name)
        assertEquals("", result.description)
    }

    @Test
    fun `filename without extension becomes name`() {
        val content = "Some content"
        
        val result = parseAgentContent(content, "my-custom-agent.md")
        
        assertNotNull(result)
        assertEquals("my-custom-agent", result!!.name)
    }

    @Test
    fun `description truncated at 100 chars when no frontmatter`() {
        val longLine = "A".repeat(150)
        val content = longLine
        
        val result = parseAgentContent(content, "long.md")
        
        assertNotNull(result)
        assertEquals(100, result!!.description.length)
    }

    // 테스트용 파싱 함수 (ChatBackendServer의 로직과 동일)
    private data class AgentInfo(val name: String, val description: String)

    private fun parseAgentContent(content: String, filename: String): AgentInfo? {
        val name = filename.removeSuffix(".md")

        // frontmatter 파싱 (---로 감싸진 YAML)
        val frontmatterMatch = Regex("^---\\s*\\n([\\s\\S]*?)\\n---").find(content)
        val description = if (frontmatterMatch != null) {
            val yaml = frontmatterMatch.groupValues[1]
            Regex("description:\\s*[\"']?(.+?)[\"']?\\s*$", RegexOption.MULTILINE)
                .find(yaml)?.groupValues?.get(1)?.trim() ?: ""
        } else {
            // frontmatter가 없으면 첫 번째 줄을 설명으로 사용
            content.lines().firstOrNull { it.isNotBlank() }?.take(100) ?: ""
        }

        return AgentInfo(name, description)
    }
}
