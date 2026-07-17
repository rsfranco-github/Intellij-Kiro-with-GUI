package com.kiro.intellij.chat

import com.intellij.openapi.project.Project

/**
 * 하나의 채팅 세션. KiroCliProcess를 관리하고 응답을 이벤트 버퍼에 적재한다.
 * webview는 /api/events 폴링으로 커서(seq) 이후의 이벤트를 가져간다.
 * (SSE는 Remote Development의 포워딩 계층을 통과하지 못해 폴링으로 대체)
 */
class ChatSession(
    val id: String,
    private val project: Project
) {
    data class SessionEvent(val seq: Long, val event: String, val data: String)

    private val cliProcess = KiroCliProcess(project)
    private val events = ArrayDeque<SessionEvent>()
    private var nextSeq = 1L
    private val lock = Any()

    var model: String?
        get() = cliProcess.model
        set(value) { cliProcess.model = value }

    private fun appendEvent(event: String, data: String) {
        synchronized(lock) {
            events.addLast(SessionEvent(nextSeq++, event, data))
            while (events.size > MAX_EVENTS) events.removeFirst()
        }
    }

    /** seq보다 큰 이벤트를 순서대로 반환 */
    fun eventsAfter(seq: Long): List<SessionEvent> = synchronized(lock) {
        events.filter { it.seq > seq }
    }

    fun sendMessage(message: String) {
        appendEvent("start", "")

        cliProcess.sendMessage(
            message = message,
            onChunk = { chunk ->
                appendEvent("chunk", chunk)
            },
            onDone = {
                appendEvent("done", "")
            },
            onError = { error ->
                appendEvent("error", error)
            }
        )
    }

    fun stopGeneration() {
        cliProcess.stop()
        appendEvent("done", "")
    }

    fun resetSession() {
        cliProcess.resetSession()
    }

    companion object {
        private const val MAX_EVENTS = 5000
    }
}
