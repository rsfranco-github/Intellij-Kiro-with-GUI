package com.kiro.intellij.chat

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 내장 HTTP 서버. JCEF webview와 Kotlin 백엔드 간 통신.
 * SSE(Server-Sent Events)로 스트리밍 응답 전달.
 */
class ChatBackendServer(private val project: Project, parentDisposable: Disposable) : Disposable {

    private val log = Logger.getInstance(ChatBackendServer::class.java)
    private val server: HttpServer
    private val sessions = ConcurrentHashMap<String, ChatSession>()

    val port: Int

    init {
        Disposer.register(parentDisposable, this)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        // SSE 연결(/api/stream)이 스레드를 점유하므로 고정 풀 사용 시 고갈됨 → cached pool
        server.executor = Executors.newCachedThreadPool()
        port = server.address.port

        server.createContext("/api/send") { exchange -> handleSend(exchange) }
        server.createContext("/api/stream") { exchange -> handleStream(exchange) }
        server.createContext("/api/stop") { exchange -> handleStop(exchange) }
        server.createContext("/api/new-session") { exchange -> handleNewSession(exchange) }
        server.createContext("/api/open-files") { exchange -> handleOpenFiles(exchange) }
        server.createContext("/api/save-image") { exchange -> handleSaveImage(exchange) }
        server.createContext("/api/set-model") { exchange -> handleSetModel(exchange) }
        server.createContext("/api/health") { exchange -> handleHealth(exchange) }
        server.createContext("/api/project-files") { exchange -> handleProjectFiles(exchange) }
        server.createContext("/api/agents") { exchange -> handleAgents(exchange) }
        server.createContext("/api/i18n") { exchange -> handleI18n(exchange) }
        server.createContext("/ui") { exchange -> handleUi(exchange) }

        server.start()
        log.info("Chat backend server started on port $port")
    }

    fun registerSession(sessionId: String, session: ChatSession) {
        sessions[sessionId] = session
    }

    fun removeSession(sessionId: String) {
        sessions.remove(sessionId)
    }

    private fun handleSend(exchange: HttpExchange) {
        setCorsHeaders(exchange)
        if (exchange.requestMethod == "OPTIONS") { exchange.sendResponseHeaders(200, -1); return }

        try {
            val body = exchange.requestBody.bufferedReader().readText()
            if (body.isBlank()) {
                sendResponse(exchange, 400, "Empty request body")
                return
            }

            val parts = body.split("\n", limit = 2)
            val sessionId = parts.getOrNull(0) ?: ""
            val message = parts.getOrNull(1) ?: ""

            if (sessionId.isBlank()) {
                sendResponse(exchange, 400, "Missing session ID")
                return
            }
            if (message.isBlank()) {
                sendResponse(exchange, 400, "Empty message")
                return
            }

            val session = sessions[sessionId]
            if (session == null) {
                sendResponse(exchange, 404, "Session not found: $sessionId")
                return
            }

            session.sendMessage(message)
            sendResponse(exchange, 200, "ok")
        } catch (e: Exception) {
            log.warn("handleSend error", e)
            sendResponse(exchange, 500, e.message ?: "Internal server error")
        }
    }

    /**
     * SSE endpoint. webview가 연결하면 응답을 실시간 스트리밍.
     */
    private fun handleStream(exchange: HttpExchange) {
        setCorsHeaders(exchange)
        if (exchange.requestMethod == "OPTIONS") { exchange.sendResponseHeaders(200, -1); return }

        val sessionId = exchange.requestURI.query?.substringAfter("session=")?.substringBefore("&") ?: ""
        val session = sessions[sessionId]
        if (session == null) {
            sendResponse(exchange, 404, "Session not found")
            return
        }

        exchange.responseHeaders.set("Content-Type", "text/event-stream")
        exchange.responseHeaders.set("Cache-Control", "no-cache")
        exchange.responseHeaders.set("Connection", "keep-alive")
        exchange.sendResponseHeaders(200, 0)

        val out = exchange.responseBody
        val writer: (String, String) -> Unit = { event, data ->
            try {
                val escaped = data.replace("\n", "\ndata: ")
                out.write("event: $event\ndata: $escaped\n\n".toByteArray())
                out.flush()
            } catch (_: Exception) {
                // 연결 끊김
            }
        }
        session.setStreamWriter(writer)

        // 연결 유지 (webview가 끊을 때까지, ping 실패 시 종료)
        try {
            while (!Thread.currentThread().isInterrupted) {
                Thread.sleep(30000)
                out.write("event: ping\ndata: \n\n".toByteArray())
                out.flush()
            }
        } catch (_: Exception) {
        } finally {
            session.clearStreamWriter(writer)
            try { out.close() } catch (_: Exception) {}
        }
    }

    private fun handleNewSession(exchange: HttpExchange) {
        setCorsHeaders(exchange)
        if (exchange.requestMethod == "OPTIONS") { exchange.sendResponseHeaders(200, -1); return }
        sendResponse(exchange, 200, "ok")
    }

    private fun handleStop(exchange: HttpExchange) {
        setCorsHeaders(exchange)
        if (exchange.requestMethod == "OPTIONS") { exchange.sendResponseHeaders(200, -1); return }
        try {
            val sessionId = exchange.requestBody.bufferedReader().readText().trim()
            val session = sessions[sessionId]
            if (session == null) {
                sendResponse(exchange, 404, "Session not found: $sessionId")
                return
            }
            session.stopGeneration()
            sendResponse(exchange, 200, "ok")
        } catch (e: Exception) {
            log.warn("handleStop error", e)
            sendResponse(exchange, 500, e.message ?: "Internal server error")
        }
    }

    private fun handleSetModel(exchange: HttpExchange) {
        setCorsHeaders(exchange)
        if (exchange.requestMethod == "OPTIONS") { exchange.sendResponseHeaders(200, -1); return }
        try {
            val body = exchange.requestBody.bufferedReader().readText()
            val parts = body.split("\n", limit = 2)
            val sessionId = parts.getOrNull(0) ?: ""
            val model = parts.getOrNull(1) ?: ""
            val session = sessions[sessionId]
            if (session == null) { sendResponse(exchange, 404, "Session not found: $sessionId"); return }
            session.model = model.ifBlank { null }
            sendResponse(exchange, 200, "ok")
        } catch (e: Exception) {
            log.warn("handleSetModel error", e)
            sendResponse(exchange, 500, e.message ?: "Internal server error")
        }
    }

    private fun handleOpenFiles(exchange: HttpExchange) {
        setCorsHeaders(exchange)
        if (exchange.requestMethod == "OPTIONS") { exchange.sendResponseHeaders(200, -1); return }

        val result = StringBuilder()
        val latch = CountDownLatch(1)
        ApplicationManager.getApplication().invokeLater {
            try {
                val fem = FileEditorManager.getInstance(project)
                val basePath = project.basePath ?: ""
                val activeFile = fem.selectedTextEditor?.virtualFile?.path ?: ""
                val files = fem.openFiles.map { file ->
                    val relativePath = if (basePath.isNotEmpty() && file.path.startsWith(basePath))
                        file.path.removePrefix(basePath).removePrefix("/") else file.path
                    val isActive = file.path == activeFile
                    "{\"name\":\"${escapeJson(file.name)}\",\"path\":\"${escapeJson(file.path)}\",\"relativePath\":\"${escapeJson(relativePath)}\",\"active\":$isActive}"
                }
                result.append("[${files.joinToString(",")}]")
            } catch (e: Exception) {
                result.append("[]")
            }
            latch.countDown()
        }
        val completed = latch.await(5, TimeUnit.SECONDS)
        if (!completed) {
            log.warn("handleOpenFiles timed out after 5s")
        }
        exchange.responseHeaders.set("Content-Type", "application/json")
        sendResponse(exchange, 200, if (completed) result.toString() else "[]")
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    }

    private fun handleSaveImage(exchange: HttpExchange) {
        setCorsHeaders(exchange)
        if (exchange.requestMethod == "OPTIONS") { exchange.sendResponseHeaders(200, -1); return }

        try {
            val body = exchange.requestBody.bufferedReader().readText()
            // 간단한 JSON 파싱 (name, data 추출)
            val nameMatch = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").find(body)
            val dataMatch = Regex("\"data\"\\s*:\\s*\"(data:image/[^\"]+)\"").find(body)
            val name = nameMatch?.groupValues?.get(1) ?: "image.png"
            val dataUrl = dataMatch?.groupValues?.get(1) ?: ""

            if (dataUrl.isBlank()) {
                sendResponse(exchange, 400, "No image data")
                return
            }

            // base64 디코딩 후 임시 파일 저장
            val base64Data = dataUrl.substringAfter(",")
            val bytes = java.util.Base64.getDecoder().decode(base64Data)
            val ext = name.substringAfterLast(".", "png")
            val tmpFile = java.io.File.createTempFile("kiro-img-", ".$ext")
            tmpFile.writeBytes(bytes)
            tmpFile.deleteOnExit()

            sendResponse(exchange, 200, tmpFile.absolutePath)
        } catch (e: Exception) {
            sendResponse(exchange, 500, e.message ?: "error")
        }
    }

    private fun handleHealth(exchange: HttpExchange) {
        setCorsHeaders(exchange)
        if (exchange.requestMethod == "OPTIONS") { exchange.sendResponseHeaders(200, -1); return }
        try {
            val validation = KiroCliValidator.validate()
            val json = buildString {
                append("{")
                append("\"status\":\"ok\",")
                append("\"sessions\":${sessions.size},")
                append("\"cliFound\":${validation.cliFound},")
                append("\"cliPath\":\"${escapeJson(validation.cliPath ?: "")}\",")
                append("\"version\":\"${escapeJson(validation.version ?: "unknown")}\",")
                append("\"authenticated\":${validation.authenticated}")
                append("}")
            }
            exchange.responseHeaders.set("Content-Type", "application/json")
            sendResponse(exchange, 200, json)
        } catch (e: Exception) {
            sendResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"${escapeJson(e.message ?: "")}\"}")
        }
    }

    /**
     * 프로젝트 파일 목록 반환 (# 자동완성용)
     */
    private fun handleProjectFiles(exchange: HttpExchange) {
        setCorsHeaders(exchange)
        if (exchange.requestMethod == "OPTIONS") { exchange.sendResponseHeaders(200, -1); return }

        val query = exchange.requestURI.query?.let { q ->
            q.split("&").find { it.startsWith("q=") }?.substringAfter("q=")?.lowercase() ?: ""
        } ?: ""

        val result = StringBuilder()
        val latch = CountDownLatch(1)

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val files = mutableListOf<ProjectFileInfo>()
                val basePath = project.basePath ?: ""

                ReadAction.nonBlocking<Unit> {
                    val fileIndex = ProjectFileIndex.getInstance(project)
                    fileIndex.iterateContent { file ->
                        if (!file.isDirectory && file.isValid) {
                            val relativePath = if (basePath.isNotEmpty() && file.path.startsWith(basePath))
                                file.path.removePrefix(basePath).removePrefix("/") else file.path
                            val dir = relativePath.substringBeforeLast("/", "")

                            // 쿼리가 있으면 필터링
                            if (query.isEmpty() ||
                                file.name.lowercase().contains(query) ||
                                relativePath.lowercase().contains(query)) {
                                files.add(ProjectFileInfo(file.name, relativePath, dir, file.extension ?: ""))
                            }
                        }
                        files.size < 500 // 최대 500개까지만
                    }
                }.executeSynchronously()

                // 파일명 매칭 우선, 그 다음 경로 매칭
                val sorted = if (query.isNotEmpty()) {
                    files.sortedWith(compareBy(
                        { !it.name.lowercase().startsWith(query) },
                        { !it.name.lowercase().contains(query) },
                        { it.name.lowercase() }
                    ))
                } else {
                    files.sortedBy { it.name.lowercase() }
                }.take(50)

                val json = sorted.joinToString(",") { f ->
                    "{\"name\":\"${escapeJson(f.name)}\",\"path\":\"${escapeJson(f.path)}\",\"dir\":\"${escapeJson(f.dir)}\",\"ext\":\"${escapeJson(f.ext)}\"}"
                }
                result.append("[$json]")
            } catch (e: Exception) {
                log.warn("handleProjectFiles error", e)
                result.append("[]")
            }
            latch.countDown()
        }

        val completed = latch.await(10, TimeUnit.SECONDS)
        if (!completed) {
            log.warn("handleProjectFiles timed out after 10s")
        }
        exchange.responseHeaders.set("Content-Type", "application/json")
        sendResponse(exchange, 200, if (completed) result.toString() else "[]")
    }

    /**
     * 에이전트 목록 반환 (@ 자동완성용)
     */
    private fun handleAgents(exchange: HttpExchange) {
        setCorsHeaders(exchange)
        if (exchange.requestMethod == "OPTIONS") { exchange.sendResponseHeaders(200, -1); return }

        try {
            val agents = mutableListOf<AgentInfo>()

            // 프로젝트 레벨 에이전트 (.kiro/agents/)
            val projectAgentsDir = project.basePath?.let { File(it, ".kiro/agents") }
            if (projectAgentsDir?.exists() == true) {
                projectAgentsDir.listFiles { f -> f.extension == "md" }?.forEach { file ->
                    parseAgentFile(file)?.let { agents.add(it) }
                }
            }

            // 사용자 레벨 에이전트 (~/.kiro/agents/)
            val userAgentsDir = File(System.getProperty("user.home"), ".kiro/agents")
            if (userAgentsDir.exists()) {
                userAgentsDir.listFiles { f -> f.extension == "md" }?.forEach { file ->
                    // 프로젝트 레벨에 같은 이름이 없으면 추가
                    val agentInfo = parseAgentFile(file)
                    if (agentInfo != null && agents.none { it.name == agentInfo.name }) {
                        agents.add(agentInfo)
                    }
                }
            }

            val json = agents.sortedBy { it.name }.joinToString(",") { a ->
                "{\"name\":\"${escapeJson(a.name)}\",\"description\":\"${escapeJson(a.description)}\",\"path\":\"${escapeJson(a.path)}\"}"
            }
            exchange.responseHeaders.set("Content-Type", "application/json")
            sendResponse(exchange, 200, "[$json]")
        } catch (e: Exception) {
            log.warn("handleAgents error", e)
            sendResponse(exchange, 500, "[]")
        }
    }

    /**
     * 에이전트 파일 파싱 (frontmatter에서 name, description 추출)
     */
    private fun parseAgentFile(file: File): AgentInfo? {
        return try {
            val content = file.readText()
            val name = file.nameWithoutExtension

            // frontmatter 파싱 (---로 감싸진 YAML)
            val frontmatterMatch = Regex("^---\\s*\\n([\\s\\S]*?)\\n---").find(content)
            val description = if (frontmatterMatch != null) {
                val yaml = frontmatterMatch.groupValues[1]
                Regex("description:\\s*[\"']?(.+?)[\"']?\\s*$", RegexOption.MULTILINE)
                    .find(yaml)?.groupValues?.get(1)?.trim() ?: ""
            } else {
                // frontmatter가 없으면 첫 번째 줄을 설명으로 사용
                content.lines().firstOrNull { it.isNotBlank() && !it.startsWith("#") }?.take(100) ?: ""
            }

            AgentInfo(name, description, file.absolutePath)
        } catch (e: Exception) {
            log.warn("Failed to parse agent file: ${file.name}", e)
            null
        }
    }

    private data class ProjectFileInfo(val name: String, val path: String, val dir: String, val ext: String)
    private data class AgentInfo(val name: String, val description: String, val path: String)

    // 세션별 HTML 저장 (UI 서빙용)
    private val sessionHtmlMap = ConcurrentHashMap<String, String>()

    fun setSessionHtml(sessionId: String, html: String) {
        sessionHtmlMap[sessionId] = html
    }

    fun removeSessionHtml(sessionId: String) {
        sessionHtmlMap.remove(sessionId)
    }

    /**
     * 세션별 채팅 UI HTML 서빙. JCEF에서 same-origin fetch를 위해 사용.
     */
    private fun handleUi(exchange: HttpExchange) {
        setCorsHeaders(exchange)
        val sessionId = exchange.requestURI.query?.substringAfter("session=")?.substringBefore("&") ?: ""
        val html = sessionHtmlMap[sessionId]
        if (html == null) {
            sendResponse(exchange, 404, "Session not found")
            return
        }
        exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
        sendResponse(exchange, 200, html)
    }

    /**
     * 다국어 메시지 반환 (채팅 UI용)
     */
    private fun handleI18n(exchange: HttpExchange) {
        setCorsHeaders(exchange)
        if (exchange.requestMethod == "OPTIONS") { exchange.sendResponseHeaders(200, -1); return }

        try {
            val lang = com.kiro.intellij.settings.KiroSettings.getInstance().state.language
            val messages = if (lang == "en") {
                mapOf(
                    "placeholder" to "Enter message... (# file, @ agent, / command)",
                    "openFile" to "Open file",
                    "systemLog" to "System log"
                )
            } else {
                mapOf(
                    "placeholder" to "메시지를 입력하세요... (# 파일, @ 에이전트, / 커맨드)",
                    "openFile" to "열린 파일",
                    "systemLog" to "시스템 로그"
                )
            }
            val json = messages.entries.joinToString(",") { (k, v) ->
                "\"$k\":\"${escapeJson(v)}\""
            }
            exchange.responseHeaders.set("Content-Type", "application/json")
            sendResponse(exchange, 200, "{$json}")
        } catch (e: Exception) {
            log.warn("handleI18n error", e)
            sendResponse(exchange, 500, "{}")
        }
    }

    fun getSessionCount(): Int = sessions.size

    private fun setCorsHeaders(exchange: HttpExchange) {
        exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
        exchange.responseHeaders.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        exchange.responseHeaders.set("Access-Control-Allow-Headers", "Content-Type")
    }

    private fun sendResponse(exchange: HttpExchange, code: Int, body: String) {
        val bytes = body.toByteArray()
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.write(bytes)
        exchange.responseBody.close()
    }

    override fun dispose() {
        sessions.clear()
        server.stop(1)
        log.info("Chat backend server stopped (port $port, project: ${project.name})")
    }
}
