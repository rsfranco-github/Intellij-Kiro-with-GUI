package com.kiro.intellij.settings

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class KiroProjectSettingsTest {

    @Test
    fun `default state should have empty overrides`() {
        val state = KiroProjectSettings.State()
        assertEquals("", state.modelOverride)
        assertEquals("", state.mcpConfigDir)
    }

    @Test
    fun `getEffectiveModel should return override when set`() {
        val projectState = KiroProjectSettings.State(modelOverride = "claude-opus-4.5")
        val globalState = KiroSettings.State(defaultModel = "Auto")

        assertEquals("claude-opus-4.5", projectState.getEffectiveModel(globalState))
    }

    @Test
    fun `getEffectiveModel should fallback to global when override is blank`() {
        val projectState = KiroProjectSettings.State(modelOverride = "")
        val globalState = KiroSettings.State(defaultModel = "claude-sonnet-4.5")

        assertEquals("claude-sonnet-4.5", projectState.getEffectiveModel(globalState))
    }

    @Test
    fun `getEffectiveMcpConfigDir should return override when set`() {
        val projectState = KiroProjectSettings.State(mcpConfigDir = "/custom/path")
        val globalState = KiroSettings.State(kiroConfigDir = "/global/path")

        assertEquals("/custom/path", projectState.getEffectiveMcpConfigDir(globalState))
    }

    @Test
    fun `getEffectiveMcpConfigDir should fallback to global when override is blank`() {
        val projectState = KiroProjectSettings.State(mcpConfigDir = "")
        val globalState = KiroSettings.State(kiroConfigDir = "/global/path")

        assertEquals("/global/path", projectState.getEffectiveMcpConfigDir(globalState))
    }

    @Test
    fun `multiple project states should be independent`() {
        val projectA = KiroProjectSettings.State(
            modelOverride = "claude-opus-4.5",
            mcpConfigDir = "/project-a/.kiro"
        )
        val projectB = KiroProjectSettings.State(
            modelOverride = "claude-sonnet-4",
            mcpConfigDir = "/project-b/.kiro"
        )
        val global = KiroSettings.State()

        assertNotEquals(projectA.getEffectiveModel(global), projectB.getEffectiveModel(global))
        assertNotEquals(projectA.getEffectiveMcpConfigDir(global), projectB.getEffectiveMcpConfigDir(global))
    }

    @Test
    fun `state copy should be independent`() {
        val original = KiroProjectSettings.State(modelOverride = "model-a")
        val copy = original.copy(modelOverride = "model-b")

        assertEquals("model-a", original.modelOverride)
        assertEquals("model-b", copy.modelOverride)
    }
}
