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
            onError("이전 요청이 아직 처리 중입니다.")
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
                    onError(validation.errorMessage ?: "kiro-cli를 찾을 수 없습니다.")
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
                    onError("kiro-cli 응답 시간이 초과되었습니다 (5분). 프로세스를 종료합니다.")
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
                    onChunk("현재 모델: $currentModel\n")
                    onChunk("모델을 변경하려면 입력창 하단의 모델 선택 버튼을 사용하세요.\n")
                    onChunk("또는 /model <모델명> 형식으로 직접 지정할 수 있습니다.\n")
                    onChunk("예: /model claude-sonnet-4\n")
                    onDone()
                    return true
                } else {
                    model = args.trim()
                    onChunk("모델이 '${args.trim()}'(으)로 변경되었습니다.\n")
                    onDone()
                    return true
                }
            }
            "/clear" -> {
                resetSession()
                onChunk("대화 기록이 초기화되었습니다.\n")
                onDone()
                return true
            }
            "/help" -> {
                onChunk("사용 가능한 슬래시 커맨드:\n")
                onChunk("/model [모델명] - 현재 모델 확인 또는 변경\n")
                onChunk("/clear - 대화 기록 초기화\n")
                onChunk("/context - 컨텍스트 파일 관리\n")
                onChunk("/tools - 도구 및 권한 보기\n")
                onChunk("/usage - 사용량 정보\n")
                onChunk("/mcp - MCP 서버 목록\n")
                onChunk("/compact - 대화 요약\n")
                onChunk("/help - 도움말\n")
                onDone()
                return true
            }
            "/status" -> {
                val validation = KiroCliValidator.validate(forceRefresh = true)
                onChunk("=== Kiro CLI 상태 ===\n")
                onChunk("CLI 발견: ${if (validation.cliFound) "✓" else "✗"}\n")
                if (validation.cliPath != null) onChunk("경로: ${validation.cliPath}\n")
                if (validation.version != null) onChunk("버전: ${validation.version}\n")
                onChunk("인증: ${if (validation.authenticated) "✓ 로그인됨" else "✗ 미인증"}\n")
                onChunk("연속 에러: ${consecutiveErrors.get()}회\n")
                if (validation.errorMessage != null) onChunk("오류: ${validation.errorMessage}\n")
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
                exitCode == 1 && !hasOutput -> "kiro-cli 실행에 실패했습니다. 인증 상태를 확인하세요. (/status)"
                exitCode == 1 -> "kiro-cli가 오류와 함께 종료되었습니다 (code: $exitCode)"
                exitCode == 2 -> "kiro-cli 명령어 인수가 올바르지 않습니다."
                exitCode == 126 -> "kiro-cli 실행 권한이 없습니다. 파일 권한을 확인하세요."
                exitCode == 127 -> "kiro-cli를 찾을 수 없습니다. 설치 경로를 확인하세요."
                exitCode == 130 -> "사용자에 의해 중단되었습니다."
                exitCode == 137 -> "kiro-cli 프로세스가 강제 종료되었습니다 (메모리 부족 가능성)."
                exitCode == 143 -> "kiro-cli 프로세스가 종료 신호를 받았습니다."
                else -> "kiro-cli가 비정상 종료되었습니다 (exit code: $exitCode)"
            }
        }

        fun classifyException(e: Exception): String {
            val message = e.message ?: ""
            return when {
                e is java.io.IOException && message.contains("No such file", ignoreCase = true) ->
                    "kiro-cli를 찾을 수 없습니다. 설정에서 경로를 확인하세요."
                e is java.io.IOException && message.contains("Permission denied", ignoreCase = true) ->
                    "kiro-cli 실행 권한이 없습니다."
                e is java.io.IOException ->
                    "kiro-cli 실행 중 I/O 오류: $message"
                e is InterruptedException ->
                    "요청이 중단되었습니다."
                else ->
                    "예상치 못한 오류: $message"
            }
        }
    }
}
