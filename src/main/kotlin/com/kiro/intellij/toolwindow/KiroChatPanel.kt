package com.kiro.intellij.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.components.JBLabel
import com.kiro.intellij.settings.KiroSettings
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner
import org.jetbrains.plugins.terminal.ShellStartupOptions
import java.awt.BorderLayout
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

class KiroChatPanel(private val project: Project) : Disposable {

    private val models = arrayOf("Auto", "claude-opus-4.6", "claude-opus-4.5", "claude-sonnet-4.5", "claude-sonnet-4.0", "claude-haiku-4.5")
    private val modelCombo = JComboBox(models)
    private val mainPanel = JPanel(BorderLayout())
    private var terminalWidget: TerminalWidget? = null

    val component: JComponent get() = mainPanel

    init {
        val settings = KiroSettings.getInstance().state
        modelCombo.selectedItem = settings.defaultModel
        modelCombo.addActionListener { onModelChanged() }

        val toolbar = JPanel().apply {
            add(JBLabel("Model:"))
            add(modelCombo)
        }
        mainPanel.add(toolbar, BorderLayout.NORTH)

        startTerminal()
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
            val widget = runner.startShellTerminalWidget(this, options, false)
            terminalWidget = widget
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

    private fun onModelChanged() {
        val model = modelCombo.selectedItem as? String ?: return
        val widget = terminalWidget ?: return
        widget.sendCommandToExecute("/model $model")
    }

    override fun dispose() {
        terminalWidget?.let { Disposer.dispose(it) }
    }
}
