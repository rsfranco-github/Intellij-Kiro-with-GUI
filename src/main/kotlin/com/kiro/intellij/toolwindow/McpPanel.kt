package com.kiro.intellij.toolwindow

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.kiro.intellij.settings.KiroCliResolver
import com.kiro.intellij.settings.KiroSettings
import com.kiro.intellij.settings.KiroProjectSettings
import kotlinx.serialization.json.*
import java.awt.*
import java.io.File
import javax.swing.*

/**
 * MCP 서버 관리 패널 - 카드 형태 UI
 * JetBrains UI 가이드라인 준수
 */
class McpPanel(private val project: Project) {

    private val panel = JPanel(BorderLayout())
    private val serversPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val loadingLabel = JBLabel(AnimatedIcon.Default()).apply { isVisible = false }

    val component: JComponent get() = panel

    init {
        setupUI()
        refreshServers()
    }

    private fun setupUI() {
        panel.border = JBUI.Borders.empty(KiroUI.Spacing.xxlarge)
        panel.background = JBColor.background()

        // 헤더 - 세로 배치로 변경 (가로 사이즈 줄여도 겹치지 않음)
        val headerPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = JBUI.Borders.emptyBottom(KiroUI.Spacing.xlarge)
        }
        
        // 제목 - 왼쪽 정렬
        headerPanel.add(JBLabel(KiroMessages["mcp.title"]).apply {
            font = font.deriveFont(Font.BOLD, 18f)
            alignmentX = Component.LEFT_ALIGNMENT
        })
        headerPanel.add(Box.createVerticalStrut(KiroUI.Spacing.medium))

        // 버튼 패널 - 왼쪽 정렬
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, KiroUI.Spacing.small, 0)).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            // FlowLayout 기본 여백 제거
            (layout as FlowLayout).hgap = KiroUI.Spacing.small
            (layout as FlowLayout).vgap = 0
        }
        
        buttonPanel.add(JButton().apply {
            icon = KiroUI.Icons.refresh
            toolTipText = KiroMessages["common.refresh"]
            addActionListener { refreshServers() }
        })
        buttonPanel.add(JButton(KiroMessages["mcp.add"]).apply {
            icon = KiroUI.Icons.add
            addActionListener { showAddServerDialog() }
        })
        buttonPanel.add(JButton(KiroMessages["mcp.edit"]).apply {
            icon = KiroUI.Icons.edit
            addActionListener { openMcpJson() }
        })
        buttonPanel.add(loadingLabel)
        
        headerPanel.add(buttonPanel)

        // 서버 목록
        val scrollPane = JBScrollPane(serversPanel).apply {
            border = null
            viewport.isOpaque = false
            isOpaque = false
        }

        panel.add(headerPanel, BorderLayout.NORTH)
        panel.add(scrollPane, BorderLayout.CENTER)
    }

    private fun refreshServers() {
        loadingLabel.isVisible = true
        SwingUtilities.invokeLater {
            serversPanel.removeAll()
        
        for ((scope, file) in getMcpJsonFiles()) {
            if (!file.exists()) continue
            try {
                val root = json.parseToJsonElement(file.readText()).jsonObject
                val servers = root["mcpServers"]?.jsonObject ?: continue
                
                if (servers.isNotEmpty()) {
                    // Scope 헤더 (AllIcons 사용)
                    val scopeIcon = if (scope == "workspace") KiroUI.Icons.folder else KiroUI.Icons.web
                    val scopeText = if (scope == "workspace") KiroMessages["mcp.workspace"] else KiroMessages["mcp.global"]
                    serversPanel.add(JBLabel(scopeText).apply {
                        icon = scopeIcon
                        font = font.deriveFont(Font.BOLD, 14f)
                        foreground = JBColor.gray
                        border = JBUI.Borders.empty(KiroUI.Spacing.medium, 0)
                        iconTextGap = KiroUI.Spacing.medium
                    })
                    
                    for ((name, config) in servers) {
                        serversPanel.add(createServerCard(name, config.jsonObject, scope))
                        serversPanel.add(Box.createVerticalStrut(KiroUI.Spacing.medium))
                    }
                }
            } catch (e: Exception) {
                val errorLabel = JBLabel("$scope ${KiroMessages["mcp.parseError"]}: ${e.message}").apply {
                    icon = KiroUI.Icons.statusError
                    iconTextGap = KiroUI.Spacing.medium
                }
                serversPanel.add(errorLabel)
            }
        }
        
        if (serversPanel.componentCount == 0) {
            serversPanel.add(JBLabel(KiroMessages["mcp.noServers"]).apply {
                foreground = JBColor.gray
            })
        }
        
        serversPanel.add(Box.createVerticalGlue())
        serversPanel.revalidate()
        serversPanel.repaint()
        loadingLabel.isVisible = false
        }
    }

    private fun createServerCard(name: String, config: JsonObject, scope: String): JPanel {
        val card = KiroUI.createCard().apply {
            maximumSize = KiroUI.maxHeightDimension(120)
        }

        // 상단: 이름 + 상태 (AllIcons 사용)
        val headerPanel = JPanel(BorderLayout()).apply { 
            isOpaque = false 
            border = JBUI.Borders.empty(KiroUI.Spacing.large)
        }
        
        val disabled = config["disabled"]?.jsonPrimitive?.booleanOrNull ?: false
        val statusIcon = KiroUI.getStatusIcon(!disabled)
        val statusText = if (disabled) KiroMessages["mcp.inactive"] else KiroMessages["mcp.active"]
        
        headerPanel.add(JBLabel(name).apply {
            icon = statusIcon
            font = font.deriveFont(Font.BOLD, 14f)
            iconTextGap = KiroUI.Spacing.medium
        }, BorderLayout.WEST)
        
        headerPanel.add(JBLabel(statusText).apply {
            foreground = if (disabled) KiroUI.Colors.errorForeground else KiroUI.Colors.successForeground
        }, BorderLayout.EAST)

        // 중앙: 명령어
        val command = config["command"]?.jsonPrimitive?.content ?: ""
        val args = config["args"]?.jsonArray?.joinToString(" ") { it.jsonPrimitive.content } ?: ""
        val fullCommand = "$command $args".trim()
        
        val infoPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = JBUI.Borders.empty(0, KiroUI.Spacing.large)
        }
        
        infoPanel.add(JBLabel("${KiroMessages["mcp.commandPrefix"]} $fullCommand").apply {
            foreground = JBColor.gray
            font = font.deriveFont(12f)
        })

        // 하단: 버튼
        val actionPanel = JPanel(FlowLayout(FlowLayout.RIGHT, KiroUI.Spacing.small, 0)).apply {
            isOpaque = false
            border = JBUI.Borders.empty(KiroUI.Spacing.medium, KiroUI.Spacing.large)
        }
        
        actionPanel.add(JButton(if (disabled) KiroMessages["mcp.activate"] else KiroMessages["mcp.deactivate"]).apply {
            font = font.deriveFont(11f)
            addActionListener { toggleServer(name, scope, disabled) }
        })
        actionPanel.add(JButton().apply {
            icon = KiroUI.Icons.remove
            toolTipText = KiroMessages["common.remove"]
            addActionListener { removeServer(name, scope) }
        })

        card.add(headerPanel, BorderLayout.NORTH)
        card.add(infoPanel, BorderLayout.CENTER)
        card.add(actionPanel, BorderLayout.SOUTH)
        
        return card
    }

    private fun showAddServerDialog() {
        val nameField = JTextField(20)
        val commandField = JTextField(30)
        val scopeCombo = JComboBox(arrayOf("workspace", "global"))
        
        val dialogPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(KiroUI.Spacing.small, KiroUI.Spacing.small, KiroUI.Spacing.small, KiroUI.Spacing.small)
            anchor = GridBagConstraints.WEST
        }
        
        gbc.gridx = 0; gbc.gridy = 0
        dialogPanel.add(JLabel(KiroMessages["mcp.name"]), gbc)
        gbc.gridx = 1
        dialogPanel.add(nameField, gbc)
        
        gbc.gridx = 0; gbc.gridy = 1
        dialogPanel.add(JLabel(KiroMessages["mcp.commandLabel"]), gbc)
        gbc.gridx = 1
        dialogPanel.add(commandField, gbc)
        
        gbc.gridx = 0; gbc.gridy = 2
        dialogPanel.add(JLabel(KiroMessages["mcp.scope"]), gbc)
        gbc.gridx = 1
        dialogPanel.add(scopeCombo, gbc)

        val result = JOptionPane.showConfirmDialog(
            panel, dialogPanel, KiroMessages["mcp.addTitle"], 
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
        )
        
        if (result == JOptionPane.OK_OPTION) {
            val name = nameField.text.trim()
            val command = commandField.text.trim()
            val scope = scopeCombo.selectedItem as String
            
            if (name.isNotBlank() && command.isNotBlank()) {
                addServer(name, command, scope)
            }
        }
    }

    private fun addServer(name: String, command: String, scope: String) {
        Thread {
            try {
                val kiroCommand = KiroCliResolver.resolve()
                val pb = KiroCliResolver.configureProcessBuilder(
                    ProcessBuilder(
                        kiroCommand, "mcp", "add",
                        "--name", name, "--command", command, "--scope", scope, "--force"
                    ).directory(project.basePath?.let { File(it) }).redirectErrorStream(true)
                )
                pb.start().waitFor()
                SwingUtilities.invokeLater { refreshServers() }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    KiroUI.showErrorDialog(panel, "${KiroMessages["mcp.addFailed"]}: ${e.message}", KiroMessages["common.error"])
                }
            }
        }.start()
    }

    private fun removeServer(name: String, scope: String) {
        if (!KiroUI.showConfirmDialog(panel, "'$name' ${KiroMessages["mcp.removeConfirm"]}", KiroMessages["mcp.removeTitle"])) {
            return
        }
        
        Thread {
            try {
                val kiroCommand = KiroCliResolver.resolve()
                val pb = KiroCliResolver.configureProcessBuilder(
                    ProcessBuilder(
                        kiroCommand, "mcp", "remove", "--name", name, "--scope", scope
                    ).directory(project.basePath?.let { File(it) }).redirectErrorStream(true)
                )
                pb.start().waitFor()
                SwingUtilities.invokeLater { refreshServers() }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    KiroUI.showErrorDialog(panel, "${KiroMessages["mcp.removeFailed"]}: ${e.message}", KiroMessages["common.error"])
                }
            }
        }.start()
    }

    private fun toggleServer(name: String, scope: String, currentlyDisabled: Boolean) {
        val file = if (scope == "workspace") {
            project.basePath?.let { File(it, ".kiro/settings/mcp.json") }
        } else {
            File(System.getProperty("user.home"), ".kiro/settings/mcp.json")
        } ?: return
        
        try {
            val root = json.parseToJsonElement(file.readText()).jsonObject.toMutableMap()
            val servers = root["mcpServers"]?.jsonObject?.toMutableMap() ?: return
            val serverConfig = servers[name]?.jsonObject?.toMutableMap() ?: return
            
            serverConfig["disabled"] = JsonPrimitive(!currentlyDisabled)
            servers[name] = JsonObject(serverConfig)
            root["mcpServers"] = JsonObject(servers)
            
            file.writeText(json.encodeToString(JsonObject.serializer(), JsonObject(root)))
            refreshServers()
        } catch (e: Exception) {
            KiroUI.showErrorDialog(panel, "${KiroMessages["mcp.toggleFailed"]}: ${e.message}", KiroMessages["common.error"])
        }
    }

    private fun openMcpJson() {
        val files = getMcpJsonFiles().filter { it.second.exists() }
        
        if (files.isEmpty()) {
            KiroUI.showInfoDialog(panel, KiroMessages["mcp.notFound"], KiroMessages["settings.info"])
            return
        }
        
        if (files.size == 1) {
            // 파일이 하나만 있으면 바로 열기
            openFile(files[0].second)
            return
        }
        
        // 여러 파일이 있으면 선택 다이얼로그
        val options = files.map { (scope, file) ->
            val label = if (scope == "workspace") KiroMessages["mcp.workspace"] else KiroMessages["mcp.global"]
            "$label (${file.absolutePath})"
        }.toTypedArray()
        
        val choice = JOptionPane.showInputDialog(
            panel,
            KiroMessages["mcp.selectFile"],
            KiroMessages["mcp.selectTitle"],
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]
        )
        
        if (choice != null) {
            val index = options.indexOf(choice)
            if (index >= 0) {
                openFile(files[index].second)
            }
        }
    }
    
    private fun openFile(file: File) {
        val vFile = LocalFileSystem.getInstance().findFileByIoFile(file)
        if (vFile != null) {
            FileEditorManager.getInstance(project).openFile(vFile, true)
        } else {
            KiroUI.showErrorDialog(panel, "${KiroMessages["mcp.openFailed"]} ${file.absolutePath}", KiroMessages["common.error"])
        }
    }

    private fun getMcpJsonFiles(): List<Pair<String, File>> {
        val globalSettings = KiroSettings.getInstance().state
        val projectSettings = KiroProjectSettings.getInstance(project).state

        val configDir = projectSettings.getEffectiveMcpConfigDir(globalSettings).ifBlank { null }
        val globalDir = if (configDir != null) {
            File(configDir)
        } else {
            File(System.getProperty("user.home"), ".kiro")
        }
        val global = File(globalDir, "settings/mcp.json")

        val workspace = project.basePath?.let { File(it, ".kiro/settings/mcp.json") }

        return listOfNotNull(
            workspace?.let { "workspace" to it },
            "global" to global
        )
    }
}
