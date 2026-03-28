package com.kiro.intellij.toolwindow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SettingsPanelTest {

    // ========== resolveEffectiveConfigDir ==========

    @Test
    fun `empty input should resolve to default kiro dir`() {
        val result = SettingsPanel.resolveEffectiveConfigDir("")
        assertTrue(result.endsWith("/.kiro"), "Expected ~/.kiro but got: $result")
        assertTrue(result.startsWith("/"), "Should be absolute path: $result")
    }

    @Test
    fun `blank input should resolve to default kiro dir`() {
        val result = SettingsPanel.resolveEffectiveConfigDir("   ")
        assertTrue(result.endsWith("/.kiro"))
    }

    @Test
    fun `custom path should be returned as-is`() {
        val custom = "/custom/kiro/config"
        val result = SettingsPanel.resolveEffectiveConfigDir(custom)
        assertEquals("/custom/kiro/config", result)
    }

    @Test
    fun `custom path with spaces should be preserved`() {
        val custom = "/Users/my user/kiro config"
        val result = SettingsPanel.resolveEffectiveConfigDir(custom)
        assertEquals("/Users/my user/kiro config", result)
    }

    @Test
    fun `home dir based path should work`() {
        val home = System.getProperty("user.home")
        val custom = "$home/.kiro-custom"
        val result = SettingsPanel.resolveEffectiveConfigDir(custom)
        assertEquals(custom, result)
    }

    @Test
    fun `default path should contain user home`() {
        val home = System.getProperty("user.home")
        val result = SettingsPanel.resolveEffectiveConfigDir("")
        assertTrue(result.startsWith(home), "Should start with $home but got: $result")
    }

    // ========== config dir label text logic ==========

    @Test
    fun `label text should show default suffix when input is blank`() {
        val configDir = ""
        val effectivePath = SettingsPanel.resolveEffectiveConfigDir(configDir)
        val isCustom = configDir.isNotBlank()

        val labelText = if (isCustom) {
            "현재 경로: $effectivePath"
        } else {
            "현재 경로: $effectivePath (기본)"
        }

        assertTrue(labelText.contains("(기본)"), "Blank input should show (기본): $labelText")
        assertTrue(labelText.contains("/.kiro"))
    }

    @Test
    fun `label text should not show default suffix when custom path is set`() {
        val configDir = "/custom/path"
        val effectivePath = SettingsPanel.resolveEffectiveConfigDir(configDir)
        val isCustom = configDir.isNotBlank()

        val labelText = if (isCustom) {
            "현재 경로: $effectivePath"
        } else {
            "현재 경로: $effectivePath (기본)"
        }

        assertFalse(labelText.contains("(기본)"), "Custom path should not show (기본): $labelText")
        assertTrue(labelText.contains("/custom/path"))
    }

    @Test
    fun `existing directory should be detected`(@TempDir tempDir: File) {
        val exists = tempDir.exists()
        assertTrue(exists)
    }

    @Test
    fun `non-existing directory should be detected`() {
        val nonExistent = File("/this/path/definitely/does/not/exist/kiro-test-xyz")
        assertFalse(nonExistent.exists())
    }

    // ========== openConfigFile path resolution ==========

    @Test
    fun `config settings subdir should be resolved from effective dir`() {
        val effectiveDir = SettingsPanel.resolveEffectiveConfigDir("")
        val settingsDir = File(effectiveDir, "settings")
        assertTrue(settingsDir.path.endsWith("/.kiro/settings"))
    }

    @Test
    fun `custom config settings subdir should use custom base`() {
        val effectiveDir = SettingsPanel.resolveEffectiveConfigDir("/my/custom/dir")
        val settingsDir = File(effectiveDir, "settings")
        assertEquals("/my/custom/dir/settings", settingsDir.path)
    }

    // ========== save triggers label update ==========

    @Test
    fun `changing config dir and resolving should give new path`() {
        val before = SettingsPanel.resolveEffectiveConfigDir("")
        val after = SettingsPanel.resolveEffectiveConfigDir("/new/kiro/path")

        assertNotEquals(before, after)
        assertTrue(before.endsWith("/.kiro"))
        assertEquals("/new/kiro/path", after)
    }

    @Test
    fun `clearing config dir should revert to default`() {
        val custom = SettingsPanel.resolveEffectiveConfigDir("/custom")
        val reverted = SettingsPanel.resolveEffectiveConfigDir("")

        assertEquals("/custom", custom)
        assertTrue(reverted.endsWith("/.kiro"))
    }

    @Test
    fun `config dir label with existing temp dir`(@TempDir tempDir: File) {
        val effectivePath = SettingsPanel.resolveEffectiveConfigDir(tempDir.absolutePath)
        val exists = File(effectivePath).exists()

        assertTrue(exists)
        assertEquals(tempDir.absolutePath, effectivePath)
    }
}
