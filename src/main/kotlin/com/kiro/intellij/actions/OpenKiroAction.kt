package com.kiro.intellij.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

class OpenKiroAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Kiro") ?: return
        if (toolWindow.isVisible) {
            toolWindow.activate(null)
        } else {
            toolWindow.show()
        }
    }
}
