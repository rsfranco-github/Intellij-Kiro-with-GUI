package com.kiro.intellij.chat

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class KiroCliProcessTest {

    @Test
    fun `spinner frames separated by carriage return are emitted once`() {
        val chunks = mutableListOf<String>()
        val emitter = OutputEmitter { chunks.add(it) }

        // 로그아웃 상태에서 kiro-cli가 뿜는 로그인 스피너: \r로 같은 줄을 덮어쓰는 프레임들
        emitter.feed("⡆ Opening browser... | Press (^) + C to cancel\r")
        emitter.feed("⡇ Opening browser... | Press (^) + C to cancel\r")
        emitter.feed("⡏ Opening browser... | Press (^) + C to cancel\r")
        emitter.feed("▰▰▱▱▱▱▱ Opening browser... | Press (^) + C to cancel\r")
        emitter.feed("▰▰▰▱▱▱▱ Opening browser... | Press (^) + C to cancel\r")
        emitter.finish()

        assertEquals(1, chunks.size, "same spinner message should be emitted only once: $chunks")
        assertTrue(chunks[0].startsWith("[SYS]"))
        assertTrue(chunks[0].contains("Opening browser"))
    }

    @Test
    fun `different system messages are not deduplicated`() {
        val chunks = mutableListOf<String>()
        val emitter = OutputEmitter { chunks.add(it) }

        emitter.feed("⡆ Opening browser... | Press (^) + C to cancel\r")
        emitter.feed("⡆ Loading something else entirely\r")
        emitter.finish()

        assertEquals(2, chunks.size)
    }

    @Test
    fun `normal newline lines pass through unchanged`() {
        val chunks = mutableListOf<String>()
        val emitter = OutputEmitter { chunks.add(it) }

        emitter.feed("hello world\n")
        emitter.feed("second line\n")
        emitter.finish()

        assertEquals(listOf("hello world\n", "second line\n"), chunks)
        assertTrue(emitter.hasOutput)
    }

    @Test
    fun `crlf is treated as a normal newline not a spinner frame`() {
        val chunks = mutableListOf<String>()
        val emitter = OutputEmitter { chunks.add(it) }

        emitter.feed("windows line\r\n")
        emitter.finish()

        assertEquals(1, chunks.size)
        assertFalse(chunks[0].startsWith("[SYS]"), "CRLF line must not be classified as spinner output: ${chunks[0]}")
    }

    @Test
    fun `content after final spinner frame is emitted as normal chunk`() {
        val chunks = mutableListOf<String>()
        val emitter = OutputEmitter { chunks.add(it) }

        emitter.feed("⡆ Opening browser... | Press (^) + C to cancel\r")
        emitter.feed("⡇ Opening browser... | Press (^) + C to cancel\r")
        emitter.feed("✓ Signed in with Google\n")
        emitter.feed("actual answer\n")
        emitter.finish()

        assertTrue(chunks.any { it.contains("Signed in with Google") })
        assertTrue(chunks.any { it == "actual answer\n" })
    }

    @Test
    fun `normalizeSystemLine equates spinner frames`() {
        val a = KiroCliProcess.normalizeSystemLine("⡆ Opening browser... | Press (^) + C to cancel")
        val b = KiroCliProcess.normalizeSystemLine("⡏ Opening browser... | Press (^) + C to cancel")
        val c = KiroCliProcess.normalizeSystemLine("▰▰▰▱▱▱▱ Opening browser... | Press (^) + C to cancel")
        assertEquals(a, b)
        assertEquals(a, c)

        val other = KiroCliProcess.normalizeSystemLine("⡆ Loading tools...")
        assertTrue(a != other)
    }

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
