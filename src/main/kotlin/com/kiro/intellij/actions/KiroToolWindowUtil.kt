package com.kiro.intellij.actions

import com.intellij.openapi.wm.ToolWindow
import com.kiro.intellij.chat.ChatPanel
import com.kiro.intellij.toolwindow.KiroToolWindowFactory

object KiroToolWindowUtil {
    fun getActiveChatPanel(toolWindow: ToolWindow): ChatPanel? {
        val content = toolWindow.contentManager.selectedContent ?: return null
        return content.getUserData(KiroToolWindowFactory.CHAT_PANEL_KEY)
            ?: toolWindow.contentManager.contents
                .firstNotNullOfOrNull { it.getUserData(KiroToolWindowFactory.CHAT_PANEL_KEY) }
    }
}
