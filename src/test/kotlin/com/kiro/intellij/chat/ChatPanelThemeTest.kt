package com.kiro.intellij.chat

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * ChatPanel theme support tests.
 * Tests that the HTML builder correctly includes CSS variable blocks for both themes,
 * that initialBodyClass is injected into the body tag, and that hardcoded dark color
 * values are not present outside of theme variable blocks.
 *
 * Uses buildChatHtml (top-level internal function) directly — no IntelliJ services required.
 */
class ChatPanelThemeTest {

    @Test
    fun `html should contain theme-dark CSS variable block`() {
        val html = buildChatHtml(12345, "test-session", "theme-dark")
        assertTrue(html.contains("body.theme-dark"), "HTML should contain body.theme-dark block")
        assertTrue(html.contains("--bg:"), "HTML should contain CSS variable --bg")
        assertTrue(html.contains("--fg:"), "HTML should contain CSS variable --fg")
    }

    @Test
    fun `html should contain theme-light CSS variable block`() {
        val html = buildChatHtml(12345, "test-session", "theme-light")
        assertTrue(html.contains("body.theme-light"), "HTML should contain body.theme-light block")
        assertTrue(html.contains("--bg: #ffffff"), "Light theme should have white background")
    }

    @Test
    fun `html body class should match initialBodyClass theme-dark`() {
        val html = buildChatHtml(12345, "test-session", "theme-dark")
        assertTrue(html.contains("""<body class="theme-dark">"""), "body class should be theme-dark")
        assertFalse(html.contains("""<body class="theme-light">"""), "body class should not be theme-light")
    }

    @Test
    fun `html body class should match initialBodyClass theme-light`() {
        val html = buildChatHtml(12345, "test-session", "theme-light")
        assertTrue(html.contains("""<body class="theme-light">"""), "body class should be theme-light")
        assertFalse(html.contains("""<body class="theme-dark">"""), "body class should not be theme-dark")
    }

    @Test
    fun `html should not have hardcoded dark background outside theme variable blocks`() {
        val html = buildChatHtml(12345, "test-session", "theme-dark")
        val darkBlockStart = html.indexOf("body.theme-dark {")
        val lightBlockStart = html.indexOf("body.theme-light {")

        fun findBlockEnd(start: Int): Int {
            var depth = 0
            var i = start
            while (i < html.length) {
                when (html[i]) {
                    '{' -> depth++
                    '}' -> { depth--; if (depth == 0) return i + 1 }
                }
                i++
            }
            return html.length
        }

        val darkBlockEnd = findBlockEnd(darkBlockStart)
        val lightBlockEnd = findBlockEnd(lightBlockStart)

        val htmlWithoutBlocks = html.substring(0, minOf(darkBlockStart, lightBlockStart)) +
            html.substring(maxOf(darkBlockEnd, lightBlockEnd))

        assertFalse(
            htmlWithoutBlocks.contains("background: #1e1e1e") ||
            htmlWithoutBlocks.contains("background:#1e1e1e"),
            "Hardcoded #1e1e1e should not appear outside theme blocks"
        )
    }

    @Test
    fun `html should contain setTheme JS function`() {
        val html = buildChatHtml(12345, "test-session", "theme-dark")
        assertTrue(html.contains("function setTheme(name)"), "HTML should contain setTheme function")
        assertTrue(html.contains("theme-dark"), "setTheme should reference theme-dark")
        assertTrue(html.contains("theme-light"), "setTheme should reference theme-light")
    }

    @Test
    fun `html should use CSS variables for key colors`() {
        val html = buildChatHtml(12345, "test-session", "theme-dark")
        assertTrue(html.contains("background: var(--bg)"), "body background should use CSS var")
        assertTrue(html.contains("color: var(--fg)"), "body color should use CSS var")
        assertTrue(html.contains("var(--accent)"), "send button should use --accent")
        assertTrue(html.contains("var(--user-bg)"), "user message should use --user-bg")
        assertTrue(html.contains("var(--assistant-bg)"), "assistant message should use --assistant-bg")
    }

    @Test
    fun `html focus-within box-shadow should use CSS variable not hardcoded color`() {
        val html = buildChatHtml(12345, "test-session", "theme-dark")
        assertTrue(
            html.contains("box-shadow: 0 0 0 1px var(--accent-shadow)"),
            "focus-within box-shadow should use var(--accent-shadow)"
        )
        assertFalse(
            html.contains("box-shadow: 0 0 0 1px rgba(0,122,204"),
            "focus-within box-shadow must not use hardcoded dark color"
        )
    }

    @Test
    fun `dark theme should have accent-shadow CSS variable`() {
        val html = buildChatHtml(12345, "test-session", "theme-dark")
        assertTrue(html.contains("--accent-shadow:"), "dark theme must define --accent-shadow")
    }

    @Test
    fun `light theme should have accent-shadow CSS variable`() {
        val html = buildChatHtml(12345, "test-session", "theme-light")
        assertTrue(html.contains("--accent-shadow:"), "light theme must define --accent-shadow")
    }
}
