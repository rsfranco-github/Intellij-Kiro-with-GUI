package com.kiro.intellij.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit

/**
 * kiro-cli에서 사용 가능한 모델 목록을 조회하고 캐싱한다.
 * (kiro-cli chat --list-models --format json, 호출당 ~2초)
 *
 * 지연 은닉 전략:
 * - 툴윈도우가 열릴 때 warmUp()으로 백그라운드에서 미리 1회 조회
 * - UI는 getCached()(즉시, fallback 가능)로 먼저 그리고, 조회가 끝나면 갱신
 * - 결과는 메모리 캐시(TTL 30분) — 채팅 전송 경로와는 무관
 */
object KiroModelProvider {

    data class ModelInfo(val id: String, val label: String, val description: String)

    // prettyLabel보다 먼저 초기화되어야 함 (FALLBACK이 prettyLabel을 사용)
    private val BRAND_CASING = mapOf(
        "deepseek" to "DeepSeek",
        "minimax" to "MiniMax",
        "glm" to "GLM",
        "gpt" to "GPT"
    )

    // kiro-cli 조회 실패 시(미설치/미인증/타임아웃) 사용하는 내장 목록
    val FALLBACK: List<ModelInfo> = listOf(
        "auto", "claude-sonnet-4.5", "claude-sonnet-4", "claude-haiku-4.5",
        "deepseek-3.2", "minimax-m2.5", "minimax-m2.1", "glm-5", "qwen3-coder-next"
    ).map { ModelInfo(it, prettyLabel(it), "") }

    private val log = Logger.getInstance(KiroModelProvider::class.java)
    private const val CACHE_TTL_MS = 30 * 60_000L

    @Volatile private var cached: List<ModelInfo>? = null
    @Volatile private var cachedAt = 0L

    private fun freshCache(): List<ModelInfo>? =
        cached?.takeIf { System.currentTimeMillis() - cachedAt < CACHE_TTL_MS }

    /** 즉시 반환 (캐시 없으면 fallback). 프로세스 호출 없음 — EDT에서 호출해도 안전. */
    fun getCached(): List<ModelInfo> = freshCache() ?: cached ?: FALLBACK

    /** 캐시가 없으면 CLI 조회가 끝날 때까지 대기. 백그라운드 스레드에서만 호출할 것. */
    fun getModelsBlocking(): List<ModelInfo> {
        freshCache()?.let { return it }
        synchronized(this) {
            freshCache()?.let { return it }
            val models = fetchFromCli()
            if (models != null) {
                cached = models
                cachedAt = System.currentTimeMillis()
            }
            return cached ?: FALLBACK
        }
    }

    /** 백그라운드에서 캐시를 미리 채운다. (단위 테스트 등 Application이 없는 환경에서는 no-op) */
    fun warmUp() {
        val app = ApplicationManager.getApplication() ?: return
        app.executeOnPooledThread { getModelsBlocking() }
    }

    private fun fetchFromCli(): List<ModelInfo>? {
        return try {
            val cliPath = KiroCliResolver.resolve()
            val pb = ProcessBuilder(cliPath, "chat", "--list-models", "--format", "json")
                .redirectErrorStream(true)
            KiroCliResolver.configureProcessBuilder(pb)
            val process = pb.start()
            val output = process.inputStream.bufferedReader().readText()
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            if (process.exitValue() != 0) return null
            parseModels(output)
        } catch (e: Exception) {
            log.debug("Failed to list models from kiro-cli", e)
            null
        }
    }

    /** --list-models JSON 파싱. 형식이 어긋나면 null (fallback 유지). */
    fun parseModels(output: String): List<ModelInfo>? {
        return try {
            // 출력 앞뒤에 경고 등이 섞일 수 있으므로 JSON 오브젝트 구간만 추출
            val start = output.indexOf('{')
            val end = output.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            val root = Json.parseToJsonElement(output.substring(start, end + 1)).jsonObject
            val models = root["models"]?.jsonArray ?: return null
            models.mapNotNull { el ->
                val obj = el.jsonObject
                val id = obj["model_id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val desc = obj["description"]?.jsonPrimitive?.content ?: ""
                ModelInfo(id, prettyLabel(id), desc)
            }.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            log.debug("Failed to parse model list", e)
            null
        }
    }

    /** 모델 id를 표시용 라벨로 변환. 예: claude-sonnet-4.5 → Claude Sonnet 4.5, glm-5 → GLM 5 */
    fun prettyLabel(id: String): String {
        if (id.equals("auto", ignoreCase = true)) return "Auto"
        return id.split('-').joinToString(" ") { token ->
            BRAND_CASING[token.lowercase()] ?: token.replaceFirstChar { it.uppercaseChar() }
        }
    }
}
