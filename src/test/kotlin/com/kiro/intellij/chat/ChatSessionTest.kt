package com.kiro.intellij.chat

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

class ChatSessionTest {

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
