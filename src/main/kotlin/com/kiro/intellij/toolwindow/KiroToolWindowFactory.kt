package com.kiro.intellij.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class KiroToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val chatPanel = KiroChatPanel(project)
        val content = ContentFactory.getInstance().createContent(chatPanel.component, "Chat", false)
        toolWindow.contentManager.addContent(content)
    }
}
