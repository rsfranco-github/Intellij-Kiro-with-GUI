package com.kiro.intellij.chat

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class KiroCliProcessTest {

    @Test
    fun `stripAnsi should remove ANSI escape codes`() {
        assertEquals("hello", KiroCliProcess.stripAnsi("\u001B[32mhello\u001B[0m"))
        assertEquals("hello world", KiroCliProcess.stripAnsi("\u001B[1;34mhello\u001B[0m \u001B[31mworld\u001B[0m"))
        assertEquals("plain text", KiroCliProcess.stripAnsi("plain text"))
    }

    @Test
    fun `stripAnsi should remove carriage returns`() {
        assertEquals("hello", KiroCliProcess.stripAnsi("hello\r"))
        assertEquals("line1\nline2", KiroCliProcess.stripAnsi("line1\r\nline2"))
    }

    @Test
    fun `stripAnsi should remove leading prompt marker`() {
        assertEquals("hello", KiroCliProcess.stripAnsi("> hello"))
        assertEquals("not a prompt > here", KiroCliProcess.stripAnsi("not a prompt > here"))
    }

    @Test
    fun `stripAnsi should handle cursor position codes`() {
        assertEquals("text", KiroCliProcess.stripAnsi("\u001B[?25htext"))
        assertEquals("text", KiroCliProcess.stripAnsi("\u001B[10Gtext"))
    }

    @Test
    fun `classifyExitCode 1 without output should suggest auth check`() {
        val msg = KiroCliProcess.classifyExitCode(1, hasOutput = false)
        assertTrue(msg.contains("authentication") || msg.contains("Check"), "Should mention authentication: $msg")
    }

    @Test
    fun `classifyExitCode 1 with output should mention error`() {
        val msg = KiroCliProcess.classifyExitCode(1, hasOutput = true)
        assertTrue(msg.contains("error"), "Should mention error: $msg")
    }

    @Test
    fun `classifyExitCode 126 should mention permission`() {
        val msg = KiroCliProcess.classifyExitCode(126, hasOutput = false)
        assertTrue(msg.contains("permission") || msg.contains("Permission"), "Should mention permission: $msg")
    }

    @Test
    fun `classifyExitCode 127 should mention not found`() {
        val msg = KiroCliProcess.classifyExitCode(127, hasOutput = false)
        assertTrue(msg.contains("not found"), "Should mention not found: $msg")
    }

    @Test
    fun `classifyExitCode 130 should mention user interruption`() {
        val msg = KiroCliProcess.classifyExitCode(130, hasOutput = false)
        assertTrue(msg.contains("Interrupted") || msg.contains("interrupted"), "Should mention interruption: $msg")
    }

    @Test
    fun `classifyExitCode 137 should mention OOM`() {
        val msg = KiroCliProcess.classifyExitCode(137, hasOutput = false)
        assertTrue(msg.contains("terminated") || msg.contains("memory"), "Should mention kill/OOM: $msg")
    }

    @Test
    fun `classifyExitCode unknown should show exit code`() {
        val msg = KiroCliProcess.classifyExitCode(42, hasOutput = false)
        assertTrue(msg.contains("42"), "Should contain exit code: $msg")
    }

    @Test
    fun `classifyException for IOException with No such file`() {
        val e = java.io.IOException("No such file or directory")
        val msg = KiroCliProcess.classifyException(e)
        assertTrue(msg.contains("not found"), "Should mention not found: $msg")
    }

    @Test
    fun `classifyException for IOException with Permission denied`() {
        val e = java.io.IOException("Permission denied")
        val msg = KiroCliProcess.classifyException(e)
        assertTrue(msg.contains("permission") || msg.contains("Permission"), "Should mention permission: $msg")
    }

    @Test
    fun `classifyException for InterruptedException`() {
        val e = InterruptedException("thread interrupted")
        val msg = KiroCliProcess.classifyException(e)
        assertTrue(msg.contains("interrupted") || msg.contains("Interrupted"), "Should mention interruption: $msg")
    }

    @Test
    fun `classifyException for generic IOException`() {
        val e = java.io.IOException("Connection reset")
        val msg = KiroCliProcess.classifyException(e)
        assertTrue(msg.contains("I/O"), "Should mention I/O: $msg")
    }

    @Test
    fun `classifyException for unknown exception`() {
        val e = RuntimeException("something went wrong")
        val msg = KiroCliProcess.classifyException(e)
        assertTrue(msg.contains("Unexpected") || msg.contains("unexpected"), "Should mention unexpected: $msg")
    }
}
