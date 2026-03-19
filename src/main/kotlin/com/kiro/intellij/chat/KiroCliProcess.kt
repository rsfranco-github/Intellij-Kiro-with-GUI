package com.kiro.intellij.chat

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.kiro.intellij.settings.KiroSettings
import java.io.*
import java.util.concurrent.CompletableFuture

/**
 * kiro-cli 프로세스를 --no-interactive 모드로 실행하고
 * 입출력을 관리한다.
 */
class KiroCliProcess(private val project: Project) {

    private val log = Logger.getInstance(KiroCliProcess::class.java)
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var onOutput: ((String) -> Unit)? = null

    val isRunning: Boolean get() = process?.isAlive == true

    fun start(model: String? = null, onOutput: (String) -> Unit) {
        this.onOutput = onOutput
        val settings = KiroSettings.getInstance().state
        val command = mutableListOf(settings.kiroCommand, "chat", "--no-interactive", "--wrap", "never")
        if (model != null && model != "Auto") {
            command.addAll(listOf("--model", model))
        }

        val workingDir = project.basePath ?: System.getProperty("user.home")
        val pb = ProcessBuilder(command)
            .directory(File(workingDir))
            .redirectErrorStream(true)

        process = pb.start()
        writer = BufferedWriter(OutputStreamWriter(process!!.outputStream))

        // 출력 읽기 스레드
        Thread({
            try {
                val reader = BufferedReader(InputStreamReader(process!!.inputStream))
                val buffer = StringBuilder()
                val charBuf = CharArray(4096)
                while (process?.isAlive == true) {
                    val count = reader.read(charBuf)
                    if (count == -1) break
                    val raw = String(charBuf, 0, count)
                    val clean = stripAnsi(raw)
                    if (clean.isNotBlank()) {
                        onOutput(clean)
                    }
                }
            } catch (e: Exception) {
                if (process?.isAlive == true) log.warn("Output reader error", e)
            }
        }, "kiro-cli-output").apply { isDaemon = true }.start()
    }

    fun send(message: String) {
        try {
            writer?.write(message)
            writer?.newLine()
            writer?.flush()
        } catch (e: Exception) {
            log.warn("Failed to send message", e)
        }
    }

    fun sendAndCollect(message: String): CompletableFuture<String> {
        val future = CompletableFuture<String>()
        val responseBuffer = StringBuilder()
        var collecting = false
        var idleCount = 0

        val prevHandler = onOutput
        onOutput = { chunk ->
            responseBuffer.append(chunk)
            collecting = true
            idleCount = 0
        }

        // 타임아웃 체크 스레드: 출력이 2초간 없으면 완료로 간주
        Thread({
            Thread.sleep(500) // 초기 대기
            while (collecting || idleCount < 4) {
                Thread.sleep(500)
                if (collecting && responseBuffer.isNotEmpty()) {
                    idleCount++
                    if (idleCount >= 4) { // 2초간 추가 출력 없음
                        break
                    }
                }
                collecting = false
            }
            onOutput = prevHandler
            future.complete(responseBuffer.toString().trim())
        }, "kiro-response-collector").apply { isDaemon = true }.start()

        send(message)
        return future
    }

    fun stop() {
        try {
            writer?.close()
        } catch (_: Exception) {}
        process?.destroyForcibly()
        process = null
        writer = null
    }

    companion object {
        private val ANSI_REGEX = Regex("\\x1B\\[[0-9;]*[a-zA-Z]|\\x1B\\[\\?[0-9]*[a-zA-Z]|\\x1B\\[[0-9]*G")

        fun stripAnsi(text: String): String {
            return ANSI_REGEX.replace(text, "")
                .replace("\r", "")
                .lines()
                .filter { line ->
                    !line.trim().startsWith("▸ Time:") && line.trim() != ">"
                }
                .joinToString("\n")
                .trim()
        }
    }
}
