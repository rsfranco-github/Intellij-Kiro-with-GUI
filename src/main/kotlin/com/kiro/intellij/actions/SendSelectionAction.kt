package com.kiro.intellij.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.wm.ToolWindowManager

class SendSelectionAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)

        val selectedText = editor.selectionModel.selectedText
        val text = if (!selectedText.isNullOrBlank()) {
            val fileName = file?.name ?: "unknown"
            "Review this code ($fileName):\n```\n$selectedText\n```"
        } else {
            val filePath = file?.path ?: return
            "@$filePath"
        }

        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Kiro") ?: return
        toolWindow.show {
            val chatPanel = KiroToolWindowUtil.getActiveChatPanel(toolWindow) ?: return@show
            chatPanel.sendToChat(text)
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && e.getData(CommonDataKeys.EDITOR) != null
    }
}
