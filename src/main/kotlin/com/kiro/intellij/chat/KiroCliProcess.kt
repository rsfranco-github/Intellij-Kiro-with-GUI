package com.kiro.intellij.chat

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.kiro.intellij.settings.KiroCliResolver
import com.kiro.intellij.settings.KiroSettings
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * kiro-cli를 매 메시지마다 --no-interactive로 실행.
 * --resume으로 대화를 이어간다.
 * 출력을 실시간 스트리밍하여 콜백으로 전달.
 */
class KiroCliProcess(private val project: Project) {

    private val log = Logger.getInstance(KiroCliProcess::class.java)
    private var isFirstMessage = true
    private val isBusy = AtomicBoolean(false)
    private val consecutiveErrors = AtomicInteger(0)
    private var currentProcess: Process? = null
    var model: String? = null

    fun sendMessage(message: String, onChunk: (String) -> Unit, onDone: () -> Unit, onError: (String) -> Unit) {
        if (!isBusy.compareAndSet(false, true)) {
            onError("Previous request is still in progress.")
            return
        }

        val trimmedMsg = message.trim()
        if (trimmedMsg.startsWith("/")) {
            val handled = handleSlashCommand(trimmedMsg, onChunk, onDone, onError)
            if (handled) {
                isBusy.set(false)
                return
            }
        }

        Thread({
            try {
                val validation = KiroCliValidator.validate()
                if (!validation.cliFound) {
                    onError(validation.errorMessage ?: "kiro-cli not found.")
                    onDone()
                    return@Thread
                }

                val settings = KiroSettings.getInstance().state
                val cliPath = validation.cliPath ?: settings.kiroCommand
                val command = mutableListOf(cliPath, "chat", "--no-interactive", "--trust-all-tools")

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
                KiroCliResolver.configureProcessBuilder(pb)
                // 터미널 너비를 넓게 설정하여 코드블럭 줄바꿈 방지
                pb.environment()["COLUMNS"] = "500"
                pb.environment()["TERM"] = "dumb"
                val process = pb.start()
                
                currentProcess = process

                val inputStream = process.inputStream
                var hasOutput = false
                val lineBuffer = StringBuilder()

                val buf = ByteArray(1024)
                var bytesRead: Int
                while (inputStream.read(buf).also { bytesRead = it } != -1) {
                    val text = String(buf, 0, bytesRead, Charsets.UTF_8)
                    for (c in text) {
                        lineBuffer.append(c)
                        if (c == '\n') {
                            val raw = lineBuffer.toString()
                            val clean = stripAnsi(raw)
                            if (clean.isNotBlank()) {
                                // ANSI 원본 기반으로 시스템 로그 판별
                                if (isSystemOutput(raw)) {
                                    onChunk("[SYS]" + clean)
                                } else {
                                    onChunk(clean)
                                }
                                hasOutput = true
                            }
                            lineBuffer.clear()
                        }
                    }
                    if (lineBuffer.length > 200) {
                        val raw = lineBuffer.toString()
                        val clean = stripAnsi(raw)
                        if (clean.isNotBlank()) {
                            if (isSystemOutput(raw)) {
                                onChunk("[SYS]" + clean)
                            } else {
                                onChunk(clean)
                            }
                            hasOutput = true
                        }
                        lineBuffer.clear()
                    }
                }
                if (lineBuffer.isNotEmpty()) {
                    val clean = stripAnsi(lineBuffer.toString())
                    if (clean.isNotBlank()) {
                        onChunk(clean)
                        hasOutput = true
                    }
                }

                val completed = process.waitFor(300, TimeUnit.SECONDS)

                if (!completed) {
                    process.destroyForcibly()
                    onError("kiro-cli response timed out (5 min). Process terminated.")
                    consecutiveErrors.incrementAndGet()
                } else {
                    val exitCode = process.exitValue()
                    isFirstMessage = false

                    if (exitCode != 0 && exitCode != 130 && exitCode != 143) {
                        val errorMsg = classifyExitCode(exitCode, hasOutput)
                        onError(errorMsg)
                        consecutiveErrors.incrementAndGet()
                    } else {
                        consecutiveErrors.set(0)
                    }
                }
            } catch (e: Exception) {
                log.warn("kiro-cli execution failed", e)
                val userMessage = classifyException(e)
                onError(userMessage)
                consecutiveErrors.incrementAndGet()
                KiroCliValidator.invalidateCache()
            } finally {
                currentProcess = null
                isBusy.set(false)
                onDone()
            }
        }, "kiro-cli-send").apply { isDaemon = true }.start()
    }

    fun stop() {
        currentProcess?.let { process ->
            try {
                process.destroy()
                if (process.isAlive) {
                    process.destroyForcibly()
                }
            } catch (e: Exception) {
                log.warn("Failed to stop kiro-cli process", e)
            }
        }
        currentProcess = null
        isBusy.set(false)
    }

    private fun handleSlashCommand(cmd: String, onChunk: (String) -> Unit, onDone: () -> Unit, onError: (String) -> Unit): Boolean {
        val parts = cmd.split(" ", limit = 2)
        val command = parts[0].lowercase()
        val args = if (parts.size > 1) parts[1] else ""

        when (command) {
            "/model" -> {
                if (args.isBlank()) {
                    val currentModel = model ?: KiroSettings.getInstance().state.defaultModel
                    onChunk("Current model: $currentModel\n")
                    onChunk("Use the model selector button at the bottom of the input to change models.\n")
                    onChunk("Or specify directly with /model <model-name>.\n")
                    onChunk("Example: /model claude-sonnet-4\n")
                    onDone()
                    return true
                } else {
                    model = args.trim()
                    onChunk("Model changed to '${args.trim()}'.\n")
                    onDone()
                    return true
                }
            }
            "/clear" -> {
                resetSession()
                onChunk("Conversation history cleared.\n")
                onDone()
                return true
            }
            "/help" -> {
                onChunk("Available slash commands:\n")
                onChunk("/model [model-name] - View or change current model\n")
                onChunk("/clear - Clear conversation history\n")
                onChunk("/context - Manage context files\n")
                onChunk("/tools - View tools and permissions\n")
                onChunk("/usage - Usage information\n")
                onChunk("/mcp - List MCP servers\n")
                onChunk("/compact - Summarize conversation\n")
                onChunk("/help - Show help\n")
                onDone()
                return true
            }
            "/status" -> {
                val validation = KiroCliValidator.validate(forceRefresh = true)
                onChunk("=== Kiro CLI Status ===\n")
                onChunk("CLI found: ${if (validation.cliFound) "✓" else "✗"}\n")
                if (validation.cliPath != null) onChunk("Path: ${validation.cliPath}\n")
                if (validation.version != null) onChunk("Version: ${validation.version}\n")
                onChunk("Auth: ${if (validation.authenticated) "✓ Logged in" else "✗ Not authenticated"}\n")
                onChunk("Consecutive errors: ${consecutiveErrors.get()}\n")
                if (validation.errorMessage != null) onChunk("Error: ${validation.errorMessage}\n")
                onDone()
                return true
            }
        }
        return false
    }

    fun resetSession() {
        isFirstMessage = true
        consecutiveErrors.set(0)
    }

    fun getConsecutiveErrors(): Int = consecutiveErrors.get()

    companion object {
        private val ANSI_REGEX = Regex("\u001B\\[[0-9;]*[a-zA-Z]|\u001B\\[\\?[0-9]*[a-zA-Z]|\u001B\\[[0-9]*G")

        fun stripAnsi(text: String): String {
            return ANSI_REGEX.replace(text, "")
                .replace("\r", "")
                .let { s ->
                    if (s.startsWith("> ")) s.substring(2) else s
                }
        }

        /**
         * ANSI 원본 기반으로 시스템 로그 판별.
         * kiro-cli 출력 패턴:
         * - (using tool: xxx) → 도구 사용 표시
         * - ^[[38;5;244m → 회색 텍스트 (시스템 메시지)
         * - Completed in → 완료 시간
         * - Loading... → 로딩 스피너
         * - ▸ Time: → 실행 시간
         * - ^[[?25l / ^[[?25h → 커서 숨김/표시 (터미널 제어)
         * - ------  → 구분선
         * - ^M → 캐리지 리턴 (스피너 업데이트)
         */
        fun isSystemOutput(raw: String): Boolean {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return false
            
            // 터미널 제어 시퀀스만 있는 줄
            val cleaned = stripAnsi(trimmed)
            if (cleaned.isBlank()) return true
            
            // (using tool: xxx) 패턴
            if (raw.contains("(using tool:")) return true
            // Completed in 패턴
            if (cleaned.startsWith("- Completed in")) return true
            // trust-all-tools 경고 메시지
            if (cleaned.startsWith("All tools are now trusted")) return true
            // Agents can sometimes 경고
            if (cleaned.startsWith("Agents can sometimes")) return true
            // Learn more at 링크
            if (cleaned.startsWith("Learn more at https://")) return true
            // 로딩 스피너 (캐리지 리턴 포함)
            if (raw.contains("\r")) return true
            // 시간 표시
            if (cleaned.contains("▸ Time:")) return true
            // 구분선
            if (cleaned.matches(Regex("^-{3,}$"))) return true
            // 도구 출력: 들여쓰기된 번호 목록 (  1. Function xxx at ...)
            if (cleaned.matches(Regex("^\\s+\\d+\\.\\s+(Function|Class|Method|Interface|Object|Property)\\s.*at\\s.*:\\d+:\\d+\\s*$"))) return true
            // Found X of Y symbols
            if (cleaned.matches(Regex("^Found \\d+ of \\d+ symbols.*$"))) return true
            // Searching for symbols
            if (cleaned.startsWith("Searching for symbols")) return true
            // Looking up symbols
            if (cleaned.startsWith("Looking up symbols")) return true
            // kiro-cli 시작 메시지 (회색 ANSI: 38;5;244)
            if (raw.contains("\u001B[38;5;244m") && !raw.contains("\u001B[38;5;10m")) return true
            // > 프롬프트
            if (cleaned.matches(Regex("^>\\s*$"))) return true
            // 커서 제어만 있는 줄
            if (raw.contains("\u001B[?25l") || raw.contains("\u001B[?25h")) {
                if (cleaned.length < 5) return true
            }
            
            return false
        }

        fun classifyExitCode(exitCode: Int, hasOutput: Boolean): String {
            return when {
                exitCode == 1 && !hasOutput -> "kiro-cli execution failed. Check authentication status. (/status)"
                exitCode == 1 -> "kiro-cli exited with error (code: $exitCode)"
                exitCode == 2 -> "kiro-cli command arguments are invalid."
                exitCode == 126 -> "No permission to execute kiro-cli. Check file permissions."
                exitCode == 127 -> "kiro-cli not found. Check installation path."
                exitCode == 130 -> "Interrupted by user."
                exitCode == 137 -> "kiro-cli process was forcibly terminated (possibly out of memory)."
                exitCode == 143 -> "kiro-cli process received termination signal."
                else -> "kiro-cli exited abnormally (exit code: $exitCode)"
            }
        }

        fun classifyException(e: Exception): String {
            val message = e.message ?: ""
            return when {
                e is java.io.IOException && message.contains("No such file", ignoreCase = true) ->
                    "kiro-cli not found. Check the path in settings."
                e is java.io.IOException && message.contains("Permission denied", ignoreCase = true) ->
                    "No permission to execute kiro-cli."
                e is java.io.IOException ->
                    "I/O error while running kiro-cli: $message"
                e is InterruptedException ->
                    "Request was interrupted."
                else ->
                    "Unexpected error: $message"
            }
        }
    }
}
