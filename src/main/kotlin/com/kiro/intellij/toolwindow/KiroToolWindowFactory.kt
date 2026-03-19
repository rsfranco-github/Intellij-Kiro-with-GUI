package com.kiro.intellij.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.kiro.intellij.chat.ChatPanel

class KiroToolWindowFactory : ToolWindowFactory {

    companion object {
        val CHAT_PANEL_KEY = Key.create<ChatPanel>("KiroChatPanel")
        private var chatCounter = 0
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // 첫 번째 채팅 탭
        addChatTab(project, toolWindow)

        // Manage 탭
        val managePanel = KiroManagePanel(project, toolWindow.disposable)
        val manageContent = ContentFactory.getInstance().createContent(managePanel.component, "Manage", false)
        toolWindow.contentManager.addContent(manageContent)

        // + 버튼으로 새 채팅 탭 추가
        toolWindow.setAdditionalGearActions(createGearActions(project, toolWindow))
    }

    private fun addChatTab(project: Project, toolWindow: ToolWindow): ChatPanel {
        chatCounter++
        val tabName = if (chatCounter == 1) "Chat" else "Chat $chatCounter"
        val chatPanel = ChatPanel(project, toolWindow.disposable, tabName)
        val content = ContentFactory.getInstance().createContent(chatPanel.component, tabName, false).apply {
            isCloseable = chatCounter > 1
            putUserData(CHAT_PANEL_KEY, chatPanel)
        }
        toolWindow.contentManager.addContent(content, 0)
        toolWindow.contentManager.setSelectedContent(content)
        return chatPanel
    }

    private fun createGearActions(project: Project, toolWindow: ToolWindow): com.intellij.openapi.actionSystem.DefaultActionGroup {
        return com.intellij.openapi.actionSystem.DefaultActionGroup().apply {
            add(object : com.intellij.openapi.actionSystem.AnAction("New Chat Tab") {
                override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                    addChatTab(project, toolWindow)
                }
            })
        }
    }
}
