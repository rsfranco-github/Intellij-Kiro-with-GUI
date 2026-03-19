package com.kiro.intellij.chat

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.kiro.intellij.settings.KiroSettings
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.CompletableFuture

/**
 * kiro-cli를 매 메시지마다 --no-interactive로 실행하고,
 * --resume으로 대화를 이어간다.
 */
class KiroCliProcess(private val project: Project) {

    private val log = Logger.getInstance(KiroCliProcess::class.java)
    private var isFirstMessage = true
    var model: String? = null

    fun sendMessage(message: String, onChunk: (String) -> Unit): CompletableFuture<String> {
        val future = CompletableFuture<String>()

        Thread({
            try {
                val settings = KiroSettings.getInstance().state
                val command = mutableListOf(settings.kiroCommand, "chat", "--no-interactive", "--wrap", "never")

                if (!isFirstMessage) {
                    command.add("--resume")
                }

                val effectiveModel = model ?: settings.defaultModel
                if (effectiveModel != "Auto") {
                    command.addAll(listOf("--model", effectiveModel))
                }

                command.add(message)

                val workingDir = project.basePath ?: System.getProperty("user.home")
                val pb = ProcessBuilder(command)
                    .directory(File(workingDir))
                    .redirectErrorStream(true)

                val process = pb.start()
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val fullResponse = StringBuilder()

                val charBuf = CharArray(1024)
                while (true) {
                    val count = reader.read(charBuf)
                    if (count == -1) break
                    val raw = String(charBuf, 0, count)
                    val clean = stripAnsi(raw)
                    if (clean.isNotBlank()) {
                        fullResponse.append(clean)
                        onChunk(clean)
                    }
                }

                process.waitFor()
                isFirstMessage = false
                future.complete(fullResponse.toString().trim())
            } catch (e: Exception) {
                log.warn("kiro-cli execution failed", e)
                onChunk("Error: ${e.message}")
                future.completeExceptionally(e)
            }
        }, "kiro-cli-send").apply { isDaemon = true }.start()

        return future
    }

    fun stop() {
        // 프로세스는 매번 종료되므로 별도 정리 불필요
    }

    companion object {
        private val ANSI_REGEX = Regex("\\x1B\\[[0-9;]*[a-zA-Z]|\\x1B\\[\\?[0-9]*[a-zA-Z]|\\x1B\\[[0-9]*G")

        fun stripAnsi(text: String): String {
            return ANSI_REGEX.replace(text, "")
                .replace("\r", "")
                .lines()
                .filter { line ->
                    val t = line.trim()
                    !t.startsWith("▸ Time:") && t != ">"
                }
                .joinToString("\n")
                .trim()
        }
    }
}
