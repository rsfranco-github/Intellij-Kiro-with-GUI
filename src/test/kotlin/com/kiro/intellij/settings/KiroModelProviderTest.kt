package com.kiro.intellij.settings

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KiroModelProviderTest {

    // kiro-cli 2.13.0 실제 출력 형식
    private val sampleJson = """
        {"models":[
          {"model_name":"auto","description":"Models chosen by task","model_id":"auto","context_window_tokens":1000000,"rate_multiplier":1.0,"rate_unit":"Credit"},
          {"model_name":"claude-sonnet-4.5","description":"Claude Sonnet 4.5 model","model_id":"claude-sonnet-4.5","context_window_tokens":200000,"rate_multiplier":1.3,"rate_unit":"Credit"},
          {"model_name":"glm-5","description":"GLM-5 model","model_id":"glm-5","context_window_tokens":200000,"rate_multiplier":0.5,"rate_unit":"Credit"}
        ],"default_model":"auto"}
    """.trimIndent()

    @Test
    fun `parseModels extracts ids labels and descriptions`() {
        val models = KiroModelProvider.parseModels(sampleJson)!!
        assertEquals(listOf("auto", "claude-sonnet-4.5", "glm-5"), models.map { it.id })
        assertEquals(listOf("Auto", "Claude Sonnet 4.5", "GLM 5"), models.map { it.label })
        assertEquals("Claude Sonnet 4.5 model", models[1].description)
    }

    @Test
    fun `parseModels tolerates noise around the json object`() {
        val noisy = "All tools are now trusted (!)\n$sampleJson\ntrailing log"
        val models = KiroModelProvider.parseModels(noisy)!!
        assertEquals(3, models.size)
    }

    @Test
    fun `parseModels returns null for invalid input`() {
        assertNull(KiroModelProvider.parseModels("not json at all"))
        assertNull(KiroModelProvider.parseModels("{\"unexpected\":true}"))
        assertNull(KiroModelProvider.parseModels("{\"models\":[]}"))
    }

    @Test
    fun `prettyLabel formats known ids`() {
        assertEquals("Auto", KiroModelProvider.prettyLabel("auto"))
        assertEquals("Claude Haiku 4.5", KiroModelProvider.prettyLabel("claude-haiku-4.5"))
        assertEquals("DeepSeek 3.2", KiroModelProvider.prettyLabel("deepseek-3.2"))
        assertEquals("MiniMax M2.5", KiroModelProvider.prettyLabel("minimax-m2.5"))
        assertEquals("Qwen3 Coder Next", KiroModelProvider.prettyLabel("qwen3-coder-next"))
        assertEquals("GLM 5", KiroModelProvider.prettyLabel("glm-5"))
    }

    @Test
    fun `fallback list starts with auto and is not empty`() {
        assertTrue(KiroModelProvider.FALLBACK.isNotEmpty())
        assertEquals("auto", KiroModelProvider.FALLBACK.first().id)
    }
}
