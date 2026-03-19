package com.kiro.intellij.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class KiroToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val chatPanel = KiroChatPanel(project, toolWindow.disposable)
        val chatContent = ContentFactory.getInstance().createContent(chatPanel.component, "Chat", false)
        chatContent.putUserData(KiroChatPanel.KEY, chatPanel)
        toolWindow.contentManager.addContent(chatContent)

        val managePanel = KiroManagePanel(project, toolWindow.disposable)
        val manageContent = ContentFactory.getInstance().createContent(managePanel.component, "Manage", false)
        toolWindow.contentManager.addContent(manageContent)
    }
}
