package com.kiro.intellij.toolwindow

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.kiro.intellij.chat.ChatBackendServer
import com.kiro.intellij.chat.ChatPanel

class KiroToolWindowFactory : ToolWindowFactory {

    companion object {
        val CHAT_PANEL_KEY = Key.create<ChatPanel>("KiroChatPanel")
        val BACKEND_KEY = Key.create<ChatBackendServer>("KiroBackend")
        private var chatCounter = 0
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val backend = ChatBackendServer(toolWindow.disposable)

        // 첫 채팅 탭
        addChatTab(project, toolWindow, backend)

        // Manage 탭
        val managePanel = KiroManagePanel(project, toolWindow.disposable)
        val manageContent = ContentFactory.getInstance().createContent(managePanel.component, "Manage", false)
        toolWindow.contentManager.addContent(manageContent)

        // 톱니바퀴 메뉴에 "New Chat" 추가
        toolWindow.setAdditionalGearActions(DefaultActionGroup().apply {
            add(object : AnAction("New Chat") {
                override fun actionPerformed(e: AnActionEvent) {
                    addChatTab(project, toolWindow, backend)
                }
            })
        })
    }

    private fun addChatTab(project: Project, toolWindow: ToolWindow, backend: ChatBackendServer) {
        chatCounter++
        val tabName = if (chatCounter == 1) "Chat" else "Chat $chatCounter"
        val chatPanel = ChatPanel(project, toolWindow.disposable, backend, tabName)
        val content = ContentFactory.getInstance().createContent(chatPanel.component, tabName, false).apply {
            isCloseable = chatCounter > 1
            putUserData(CHAT_PANEL_KEY, chatPanel)
        }
        // Manage 탭 앞에 삽입
        val manageIdx = (0 until toolWindow.contentManager.contentCount)
            .firstOrNull { toolWindow.contentManager.getContent(it)?.displayName == "Manage" }
            ?: toolWindow.contentManager.contentCount
        toolWindow.contentManager.addContent(content, manageIdx)
        toolWindow.contentManager.setSelectedContent(content)
    }
}
