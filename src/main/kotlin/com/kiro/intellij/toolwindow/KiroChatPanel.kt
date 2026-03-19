package com.kiro.intellij.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.components.JBLabel
import com.kiro.intellij.mcp.McpServer
import com.kiro.intellij.settings.KiroSettings
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import java.awt.BorderLayout
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

class KiroChatPanel(private val project: Project, private val parentDisposable: Disposable) : Disposable {

    companion object {
        val KEY = Key.create<KiroChatPanel>("KiroChatPanel")
        private val log = Logger.getInstance(KiroChatPanel::class.java)
    }

    private val models = arrayOf("Auto", "claude-opus-4.6", "claude-opus-4.5", "claude-sonnet-4.5", "claude-sonnet-4.0", "claude-haiku-4.5")
    private val modelCombo = JComboBox(models)
    private val mainPanel = JPanel(BorderLayout())
    private var shellWidget: ShellTerminalWidget? = null
    private var mcpServer: McpServer? = null

    val component: JComponent get() = mainPanel

    init {
        Disposer.register(parentDisposable, this)

        val settings = KiroSettings.getInstance().state
        modelCombo.selectedItem = settings.defaultModel
        modelCombo.addActionListener { onModelChanged() }

        val toolbar = JPanel().apply {
            add(JBLabel("Model:"))
            add(modelCombo)
        }
        mainPanel.add(toolbar, BorderLayout.NORTH)

        startMcpServer()
        startTerminal()
    }

    private fun startMcpServer() {
        try {
            mcpServer = McpServer(project, parentDisposable).apply {
                start()
                registerWithKiro()
            }
            log.info("MCP IDE server started on port ${mcpServer?.port}")
        } catch (e: Exception) {
            log.warn("Failed to start MCP server", e)
        }
    }

    private fun startTerminal() {
        try {
            val settings = KiroSettings.getInstance().state
            val command = listOf(settings.kiroCommand, "chat")
            val workingDir = project.basePath ?: System.getProperty("user.home")

            val runner = LocalTerminalDirectRunner.createTerminalRunner(project)
            val options = ShellStartupOptions.Builder()
                .shellCommand(command)
                .workingDirectory(workingDir)
                .build()
            val widget = runner.startShellTerminalWidget(parentDisposable, options, false)
            shellWidget = widget as? ShellTerminalWidget
            mainPanel.add(widget.component, BorderLayout.CENTER)
            mainPanel.revalidate()
        } catch (e: Exception) {
            val errorLabel = JBLabel("<html>kiro-cli를 찾을 수 없습니다.<br>" +
                    "Settings → Tools → Kiro에서 경로를 설정하거나<br>" +
                    "<a href='https://kiro.dev/docs/cli/'>kiro-cli를 설치</a>해주세요.</html>")
            mainPanel.add(errorLabel, BorderLayout.CENTER)
            mainPanel.revalidate()
        }
    }

    fun sendToTerminal(text: String) {
        shellWidget?.executeCommand(text)
    }

    private fun onModelChanged() {
        val model = modelCombo.selectedItem as? String ?: return
        sendToTerminal("/model $model")
    }

    override fun dispose() {
        shellWidget = null
        mcpServer = null
    }
}
