package com.kiro.intellij.chat

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.kiro.intellij.settings.KiroSettings
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * kiro-cli를 매 메시지마다 --no-interactive로 실행.
 * --resume으로 대화를 이어간다.
 * 출력을 실시간 스트리밍하여 콜백으로 전달.
 */
class KiroCliProcess(private val project: Project) {

    private val log = Logger.getInstance(KiroCliProcess::class.java)
    private var isFirstMessage = true
    private val isBusy = AtomicBoolean(false)
    var model: String? = null

    fun sendMessage(message: String, onChunk: (String) -> Unit, onDone: () -> Unit, onError: (String) -> Unit) {
        if (!isBusy.compareAndSet(false, true)) {
            onError("이전 요청이 아직 처리 중입니다.")
            return
        }

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
                val process = ProcessBuilder(command)
                    .directory(File(workingDir))
                    .redirectErrorStream(true)
                    .start()

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val charBuf = CharArray(256)

                while (true) {
                    val count = reader.read(charBuf)
                    if (count == -1) break
                    val raw = String(charBuf, 0, count)
                    val clean = stripAnsi(raw)
                    if (clean.isNotBlank()) {
                        onChunk(clean)
                    }
                }

                val exitCode = process.waitFor()
                isFirstMessage = false

                if (exitCode != 0) {
                    onError("kiro-cli exited with code $exitCode")
                }
            } catch (e: Exception) {
                log.warn("kiro-cli execution failed", e)
                onError(e.message ?: "Unknown error")
            } finally {
                isBusy.set(false)
                onDone()
            }
        }, "kiro-cli-send").apply { isDaemon = true }.start()
    }

    fun resetSession() {
        isFirstMessage = true
    }

    companion object {
        private val ANSI_REGEX = Regex("\u001B\\[[0-9;]*[a-zA-Z]|\u001B\\[\\?[0-9]*[a-zA-Z]|\u001B\\[[0-9]*G")

        fun stripAnsi(text: String): String {
            return ANSI_REGEX.replace(text, "")
                .replace("\r", "")
                .let { s ->
                    // "> " 프롬프트 제거 (응답 시작 부분)
                    if (s.startsWith("> ")) s.substring(2) else s
                }
        }
    }
}
