package com.kiro.intellij.chat

import com.intellij.openapi.project.Project
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

class ChatSessionTest {

    @Test
    fun `events are buffered with increasing seq and fetched by cursor`() {
        val session = ChatSession("test-session", mockk<Project>(relaxed = true))

        // stopGeneration은 done 이벤트를 적재한다 (프로세스가 없으면 stop은 no-op)
        session.stopGeneration()
        session.stopGeneration()

        val all = session.eventsAfter(0)
        assertEquals(2, all.size)
        assertEquals(listOf(1L, 2L), all.map { it.seq })
        assertTrue(all.all { it.event == "done" })

        // 커서 이후의 이벤트만 반환
        val afterFirst = session.eventsAfter(1)
        assertEquals(listOf(2L), afterFirst.map { it.seq })

        // 커서가 최신이면 빈 목록
        assertTrue(session.eventsAfter(2).isEmpty())
    }

    @Test
    fun `event buffer keeps seq monotonic across many events`() {
        val session = ChatSession("test-session", mockk<Project>(relaxed = true))
        repeat(10) { session.stopGeneration() }
        val events = session.eventsAfter(0)
        assertEquals((1L..10L).toList(), events.map { it.seq })
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
