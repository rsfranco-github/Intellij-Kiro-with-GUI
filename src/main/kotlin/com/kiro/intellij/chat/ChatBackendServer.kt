package com.kiro.intellij.chat

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * 내장 HTTP 서버. JCEF webview와 Kotlin 백엔드 간 통신.
 * SSE(Server-Sent Events)로 스트리밍 응답 전달.
 */
class ChatBackendServer(parentDisposable: Disposable) : Disposable {

    private val log = Logger.getInstance(ChatBackendServer::class.java)
    private val server: HttpServer
    private val sessions = ConcurrentHashMap<String, ChatSession>()

    val port: Int

    init {
        Disposer.register(parentDisposable, this)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newFixedThreadPool(4)
        port = server.address.port

        server.createContext("/api/send") { exchange -> handleSend(exchange) }
        server.createContext("/api/stream") { exchange -> handleStream(exchange) }
        server.createContext("/api/new-session") { exchange -> handleNewSession(exchange) }

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
            val parts = body.split("\n", limit = 2)
            val sessionId = parts.getOrNull(0) ?: ""
            val message = parts.getOrNull(1) ?: ""

            val session = sessions[sessionId]
            if (session == null) {
                sendResponse(exchange, 404, "Session not found")
                return
            }

            session.sendMessage(message)
            sendResponse(exchange, 200, "ok")
        } catch (e: Exception) {
            sendResponse(exchange, 500, e.message ?: "error")
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
        session.setStreamWriter { event, data ->
            try {
                val escaped = data.replace("\n", "\ndata: ")
                out.write("event: $event\ndata: $escaped\n\n".toByteArray())
                out.flush()
            } catch (_: Exception) {
                // 연결 끊김
            }
        }

        // 연결 유지 (webview가 끊을 때까지)
        try {
            while (!Thread.currentThread().isInterrupted) {
                Thread.sleep(30000)
                out.write("event: ping\ndata: \n\n".toByteArray())
                out.flush()
            }
        } catch (_: Exception) {
        } finally {
            session.setStreamWriter(null)
            out.close()
        }
    }

    private fun handleNewSession(exchange: HttpExchange) {
        setCorsHeaders(exchange)
        if (exchange.requestMethod == "OPTIONS") { exchange.sendResponseHeaders(200, -1); return }
        sendResponse(exchange, 200, "ok")
    }

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
        server.stop(0)
    }
}
