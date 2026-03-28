package com.kiro.intellij.actions

import com.intellij.openapi.wm.ToolWindow
import com.kiro.intellij.chat.ChatPanel
import com.kiro.intellij.chat.ChatTabPanel
import com.kiro.intellij.toolwindow.KiroToolWindowFactory

object KiroToolWindowUtil {
    fun getActiveChatPanel(toolWindow: ToolWindow): ChatPanel? {
        return toolWindow.contentManager.contents
            .firstNotNullOfOrNull { it.getUserData(KiroToolWindowFactory.CHAT_TAB_PANEL_KEY) }
            ?.getActiveChatPanel()
    }
}
