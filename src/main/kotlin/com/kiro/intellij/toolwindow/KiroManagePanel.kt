package com.kiro.intellij.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.kiro.intellij.settings.KiroSettings
import kotlinx.serialization.json.*
import java.awt.BorderLayout
import java.io.File
import javax.swing.*
import javax.swing.table.DefaultTableModel

class KiroManagePanel(private val project: Project, private val parentDisposable: Disposable) : Disposable {

    companion object {
        val KEY = Key.create<KiroManagePanel>("KiroManagePanel")
    }

    private val tabbedPane = JTabbedPane()
    val component: JComponent get() = tabbedPane

    init {
        Disposer.register(parentDisposable, this)
        tabbedPane.addTab("MCP", createMcpPanel())
        tabbedPane.addTab("Skills", createSkillsPanel())
        tabbedPane.addTab("Agent", createAgentPanel())
    }

    // --- MCP Tab ---
    private fun createMcpPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        val tableModel = DefaultTableModel(arrayOf("Name", "Command", "Scope"), 0)
        val table = JBTable(tableModel)

        // mcp.json 읽기
        loadMcpServers(tableModel)

        val buttonPanel = JPanel().apply {
            add(JButton("Refresh").apply { addActionListener { loadMcpServers(tableModel) } })
            add(JButton("Add...").apply { addActionListener { addMcpServer(tableModel) } })
            add(JButton("Remove").apply { addActionListener { removeMcpServer(table, tableModel) } })
            add(JButton("Edit mcp.json").apply { addActionListener { openMcpJson() } })
        }

        panel.add(JBScrollPane(table), BorderLayout.CENTER)
        panel.add(buttonPanel, BorderLayout.SOUTH)
        return panel
    }

    private fun loadMcpServers(tableModel: DefaultTableModel) {
        tableModel.rowCount = 0
        for ((scope, file) in getMcpJsonFiles()) {
            if (!file.exists()) continue
            try {
                val root = Json.parseToJsonElement(file.readText()).jsonObject
                val servers = root["mcpServers"]?.jsonObject ?: continue
                for ((name, config) in servers) {
                    val command = config.jsonObject["command"]?.jsonPrimitive?.content ?: ""
                    val args = config.jsonObject["args"]?.jsonArray?.joinToString(" ") { it.jsonPrimitive.content } ?: ""
                    tableModel.addRow(arrayOf(name, "$command $args".trim(), scope))
                }
            } catch (_: Exception) {}
        }
    }

    private fun addMcpServer(tableModel: DefaultTableModel) {
        val name = JOptionPane.showInputDialog(tabbedPane, "Server name:") ?: return
        val command = JOptionPane.showInputDialog(tabbedPane, "Command (e.g. node server.js):") ?: return
        if (name.isBlank() || command.isBlank()) return

        try {
            val pb = ProcessBuilder(
                KiroSettings.getInstance().state.kiroCommand, "mcp", "add",
                "--name", name, "--command", command, "--scope", "workspace", "--force"
            ).directory(project.basePath?.let { File(it) }).redirectErrorStream(true)
            val proc = pb.start()
            proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            loadMcpServers(tableModel)
        } catch (_: Exception) {}
    }

    private fun removeMcpServer(table: JBTable, tableModel: DefaultTableModel) {
        val row = table.selectedRow
        if (row < 0) return
        val name = tableModel.getValueAt(row, 0) as String
        val scope = tableModel.getValueAt(row, 2) as String

        try {
            val pb = ProcessBuilder(
                KiroSettings.getInstance().state.kiroCommand, "mcp", "remove",
                "--name", name, "--scope", scope
            ).directory(project.basePath?.let { File(it) }).redirectErrorStream(true)
            val proc = pb.start()
            proc.waitFor()
            loadMcpServers(tableModel)
        } catch (_: Exception) {}
    }

    private fun openMcpJson() {
        for ((_, file) in getMcpJsonFiles()) {
            if (file.exists()) {
                val vFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByIoFile(file)
                if (vFile != null) {
                    com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFile(vFile, true)
                    return
                }
            }
        }
    }

    private fun getMcpJsonFiles(): List<Pair<String, File>> {
        val workspace = project.basePath?.let { File(it, ".kiro/settings/mcp.json") }
        val global = File(System.getProperty("user.home"), ".kiro/settings/mcp.json")
        return listOfNotNull(
            workspace?.let { "workspace" to it },
            "global" to global
        )
    }

    // --- Skills Tab ---
    private fun createSkillsPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        val tableModel = DefaultTableModel(arrayOf("Tool", "Permission"), 0)
        val table = JBTable(tableModel)

        val buttonPanel = JPanel().apply {
            add(JButton("Refresh").apply { addActionListener { refreshSkills(tableModel) } })
            add(JButton("Trust").apply {
                addActionListener {
                    val row = table.selectedRow
                    if (row >= 0) {
                        val tool = tableModel.getValueAt(row, 0) as String
                        sendKiroCommand("/tools trust $tool")
                        tableModel.setValueAt("Trusted", row, 1)
                    }
                }
            })
            add(JButton("Untrust").apply {
                addActionListener {
                    val row = table.selectedRow
                    if (row >= 0) {
                        val tool = tableModel.getValueAt(row, 0) as String
                        sendKiroCommand("/tools untrust $tool")
                        tableModel.setValueAt("Ask", row, 1)
                    }
                }
            })
        }

        panel.add(JBScrollPane(table), BorderLayout.CENTER)
        panel.add(buttonPanel, BorderLayout.SOUTH)
        panel.add(JBLabel("Use Refresh to load tools from active kiro-cli session"), BorderLayout.NORTH)
        return panel
    }

    private fun refreshSkills(tableModel: DefaultTableModel) {
        tableModel.rowCount = 0
        tableModel.addRow(arrayOf("(Use /tools in Kiro chat to see tools)", ""))
    }

    // --- Agent Tab ---
    private fun createAgentPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        val agentList = DefaultListModel<String>()
        val list = JList(agentList)

        loadAgents(agentList)

        val buttonPanel = JPanel().apply {
            add(JButton("Refresh").apply { addActionListener { loadAgents(agentList) } })
            add(JButton("Swap").apply {
                addActionListener {
                    val selected = list.selectedValue ?: return@addActionListener
                    sendKiroCommand("/agent swap $selected")
                }
            })
        }

        panel.add(JBScrollPane(list), BorderLayout.CENTER)
        panel.add(buttonPanel, BorderLayout.SOUTH)
        return panel
    }

    private fun loadAgents(model: DefaultListModel<String>) {
        model.clear()
        // ~/.kiro/agents/ 와 .kiro/agents/ 스캔
        val dirs = listOfNotNull(
            project.basePath?.let { File(it, ".kiro/agents") },
            File(System.getProperty("user.home"), ".kiro/agents")
        )
        for (dir in dirs) {
            if (!dir.isDirectory) continue
            dir.listFiles { f -> f.extension == "json" }?.forEach { f ->
                model.addElement(f.nameWithoutExtension)
            }
        }
        if (model.isEmpty) model.addElement("(default)")
    }

    private fun sendKiroCommand(command: String) {
        // KiroChatPanel을 통해 터미널에 명령 전송
        val toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("Kiro")
        val content = toolWindow?.contentManager?.getContent(0)
        val chatPanel = content?.getUserData(KiroChatPanel.KEY)
        chatPanel?.sendToTerminal(command)
    }

    override fun dispose() {}
}
