package com.kiro.intellij.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.wm.ToolWindowManager

class InsertFileRefAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        val selection = editor.selectionModel
        val document = editor.document
        val ref = if (selection.hasSelection()) {
            val startLine = document.getLineNumber(selection.selectionStart) + 1
            val endLine = document.getLineNumber(selection.selectionEnd) + 1
            if (startLine == endLine) "@${file.path}#L$startLine"
            else "@${file.path}#L$startLine-$endLine"
        } else {
            val line = document.getLineNumber(editor.caretModel.offset) + 1
            "@${file.path}#L$line"
        }

        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Kiro") ?: return
        toolWindow.show {
            val chatPanel = KiroToolWindowUtil.getChatPanel(toolWindow) ?: return@show
            chatPanel.sendToTerminal(ref)
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
                && e.getData(CommonDataKeys.EDITOR) != null
                && e.getData(CommonDataKeys.VIRTUAL_FILE) != null
    }
}
