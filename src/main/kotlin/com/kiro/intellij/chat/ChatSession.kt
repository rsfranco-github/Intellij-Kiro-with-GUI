package com.kiro.intellij.chat

import com.intellij.openapi.project.Project

/**
 * 하나의 채팅 세션. KiroCliProcess를 관리하고 SSE로 응답 스트리밍.
 */
class ChatSession(
    val id: String,
    private val project: Project
) {
    private val cliProcess = KiroCliProcess(project)
    private var streamWriter: ((event: String, data: String) -> Unit)? = null

    var model: String?
        get() = cliProcess.model
        set(value) { cliProcess.model = value }

    fun setStreamWriter(writer: ((event: String, data: String) -> Unit)?) {
        this.streamWriter = writer
    }

    /** 해당 writer가 현재 등록된 writer일 때만 해제 (재연결로 등록된 새 writer 보호) */
    fun clearStreamWriter(writer: (event: String, data: String) -> Unit) {
        if (this.streamWriter === writer) {
            this.streamWriter = null
        }
    }

    fun sendMessage(message: String) {
        streamWriter?.invoke("start", "")

        cliProcess.sendMessage(
            message = message,
            onChunk = { chunk ->
                streamWriter?.invoke("chunk", chunk)
            },
            onDone = {
                streamWriter?.invoke("done", "")
            },
            onError = { error ->
                streamWriter?.invoke("error", error)
            }
        )
    }

    fun stopGeneration() {
        cliProcess.stop()
        streamWriter?.invoke("done", "")
    }

    fun resetSession() {
        cliProcess.resetSession()
    }
}
