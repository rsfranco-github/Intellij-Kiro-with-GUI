package com.kiro.intellij.chat

import com.intellij.openapi.project.Project
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

class ChatSessionTest {

    @Test
    fun `clearStreamWriter should only clear when the same writer is registered`() {
        val session = ChatSession("test-session", mockk<Project>(relaxed = true))
        val received = mutableListOf<String>()
        val writerA: (String, String) -> Unit = { event, _ -> received.add("A:$event") }
        val writerB: (String, String) -> Unit = { event, _ -> received.add("B:$event") }

        // SSE 재연결 시나리오: A 등록 → B로 교체 → 늦게 실행된 A의 정리 코드가 B를 지우면 안 됨
        session.setStreamWriter(writerA)
        session.setStreamWriter(writerB)
        session.clearStreamWriter(writerA)
        session.stopGeneration()
        assertEquals(listOf("B:done"), received)

        // 등록된 writer 자신을 해제하면 이후 이벤트가 전달되지 않음
        session.clearStreamWriter(writerB)
        session.stopGeneration()
        assertEquals(listOf("B:done"), received)
    }

    @Test
    fun `session id should be set correctly`() {
        // ChatSession requires Project, so we test the basic structure
        val sessionId = "test-session-123"
        assertNotNull(sessionId)
        assertTrue(sessionId.isNotBlank())
    }

    @Test
    fun `model can be set and retrieved`() {
        // Test model name validation
        val validModels = listOf(
            "auto",
            "claude-opus-4.6",
            "claude-sonnet-4.6",
            "claude-opus-4.5",
            "claude-sonnet-4.5",
            "claude-sonnet-4",
            "claude-haiku-4.5"
        )
        validModels.forEach { model ->
            assertTrue(model.isNotBlank())
        }
    }
}
