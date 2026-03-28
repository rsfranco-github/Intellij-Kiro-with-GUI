package com.kiro.intellij.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.kiro.intellij.chat.ChatBackendServer
import com.kiro.intellij.chat.ChatTabPanel

class KiroToolWindowFactory : ToolWindowFactory {

    companion object {
        val CHAT_TAB_PANEL_KEY = Key.create<ChatTabPanel>("KiroChatTabPanel")
        val BACKEND_KEY = Key.create<ChatBackendServer>("KiroBackend")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // JCEF가 생성하는 빈 로그 파일 정리
        cleanupJcefLogs()
        
        val backend = ChatBackendServer(project, toolWindow.disposable)

        // Chat 탭 (내부에서 멀티 세션 탭 관리)
        val chatTabPanel = ChatTabPanel(project, toolWindow.disposable, backend)
        val chatContent = ContentFactory.getInstance().createContent(
            chatTabPanel.component, 
            KiroMessages["tab.chat"], 
            false
        ).apply {
            isCloseable = false
            putUserData(CHAT_TAB_PANEL_KEY, chatTabPanel)
        }
        toolWindow.contentManager.addContent(chatContent)

        // Manage 탭
        val managePanel = KiroManagePanel(project, toolWindow.disposable)
        val manageContent = ContentFactory.getInstance().createContent(
            managePanel.component, 
            KiroMessages["tab.manage"], 
            false
        ).apply {
            isCloseable = false
        }
        toolWindow.contentManager.addContent(manageContent)
    }

    /**
     * JCEF(Chromium)가 홈 디렉토리에 생성하는 빈 로그 파일을 정리.
     * ~/.kiro/logs/jcef/ 디렉토리로 이동하여 홈 디렉토리를 깨끗하게 유지.
     */
    private fun cleanupJcefLogs() {
        try {
            val homeDir = java.io.File(System.getProperty("user.home"))
            val jcefLogDir = java.io.File(homeDir, ".kiro/logs/jcef")
            
            val jcefLogs = homeDir.listFiles { f -> 
                f.name.matches(Regex("jcef_\\d+\\.log")) 
            } ?: return
            
            if (jcefLogs.isEmpty()) return
            
            jcefLogDir.mkdirs()
            
            for (logFile in jcefLogs) {
                val dest = java.io.File(jcefLogDir, logFile.name)
                logFile.renameTo(dest)
            }
        } catch (_: Exception) {
            // 이동 실패해도 무시
        }
    }
}
