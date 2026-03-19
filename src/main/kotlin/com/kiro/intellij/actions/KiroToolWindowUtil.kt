package com.kiro.intellij.actions

import com.intellij.openapi.wm.ToolWindow
import com.kiro.intellij.toolwindow.KiroChatPanel

object KiroToolWindowUtil {
    fun getChatPanel(toolWindow: ToolWindow): KiroChatPanel? {
        val content = toolWindow.contentManager.getContent(0) ?: return null
        return content.getUserData(KiroChatPanel.KEY)
    }
}
