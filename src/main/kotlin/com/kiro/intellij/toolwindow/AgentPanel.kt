package com.kiro.intellij.toolwindow

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.kiro.intellij.actions.KiroToolWindowUtil
import kotlinx.serialization.json.*
import java.awt.*
import java.io.File
import javax.swing.*

/**
 * Agent 관리 패널 - 에이전트 목록 및 전환
 * JetBrains UI 가이드라인 준수
 */
class AgentPanel(private val project: Project) {

    private val panel = JPanel(BorderLayout())
    private val agentsPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }
    private var currentAgent = "default"
    private val json = Json { ignoreUnknownKeys = true }
    private val loadingLabel = JBLabel(AnimatedIcon.Default()).apply { isVisible = false }

    val component: JComponent get() = panel

    init {
        setupUI()
        refreshAgents()
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
        headerPanel.add(JBLabel("Agent").apply {
            font = font.deriveFont(Font.BOLD, 18f)
            alignmentX = Component.LEFT_ALIGNMENT
        })
        headerPanel.add(Box.createVerticalStrut(KiroUI.Spacing.medium))

        // 버튼 패널 - 왼쪽 정렬
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, KiroUI.Spacing.small, 0)).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            (layout as FlowLayout).hgap = KiroUI.Spacing.small
            (layout as FlowLayout).vgap = 0
        }
        
        buttonPanel.add(JButton().apply {
            icon = KiroUI.Icons.refresh
            toolTipText = KiroMessages["common.refresh"]
            addActionListener { refreshAgents() }
        })
        buttonPanel.add(JButton(KiroMessages["agent.create"]).apply {
            icon = KiroUI.Icons.add
            addActionListener { createNewAgent() }
        })
        buttonPanel.add(loadingLabel)
        
        headerPanel.add(buttonPanel)

        // 현재 에이전트 표시
        val currentPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            border = JBUI.Borders.empty(KiroUI.Spacing.medium, 0, KiroUI.Spacing.large, 0)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        currentPanel.add(JBLabel(KiroMessages["agent.currentActive"]).apply {
            foreground = JBColor.gray
        })
        currentPanel.add(JBLabel(currentAgent).apply {
            font = font.deriveFont(Font.BOLD)
            foreground = KiroUI.Colors.navSelectedForeground
        })

        // 에이전트 목록
        val scrollPane = JBScrollPane(agentsPanel).apply {
            border = null
            viewport.isOpaque = false
            isOpaque = false
        }

        val topPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        topPanel.add(headerPanel)
        topPanel.add(currentPanel)

        panel.add(topPanel, BorderLayout.NORTH)
        panel.add(scrollPane, BorderLayout.CENTER)
    }

    private fun refreshAgents() {
        loadingLabel.isVisible = true
        SwingUtilities.invokeLater {
            agentsPanel.removeAll()
        
        val agents = mutableListOf<AgentInfo>()
        
        // 기본 에이전트
        agents.add(AgentInfo("default", KiroMessages["agent.defaultDesc"], null, true))
        
        // 워크스페이스 에이전트
        project.basePath?.let { basePath ->
            val workspaceAgentsDir = File(basePath, ".kiro/agents")
            if (workspaceAgentsDir.isDirectory) {
                workspaceAgentsDir.listFiles { f -> f.extension == "json" || f.extension == "md" }?.forEach { file ->
                    val info = parseAgentFile(file)
                    if (info != null) agents.add(info)
                }
            }
        }
        
        // 글로벌 에이전트
        val globalAgentsDir = File(System.getProperty("user.home"), ".kiro/agents")
        if (globalAgentsDir.isDirectory) {
            globalAgentsDir.listFiles { f -> f.extension == "json" || f.extension == "md" }?.forEach { file ->
                val info = parseAgentFile(file)
                if (info != null) agents.add(info)
            }
        }
        
        if (agents.isEmpty()) {
            agentsPanel.add(JBLabel(KiroMessages["agent.noAgents"]).apply {
                foreground = JBColor.gray
            })
        } else {
            agents.forEach { agent ->
                agentsPanel.add(createAgentCard(agent))
                agentsPanel.add(Box.createVerticalStrut(KiroUI.Spacing.medium))
            }
        }
        
        agentsPanel.add(Box.createVerticalGlue())
        agentsPanel.revalidate()
        agentsPanel.repaint()
        loadingLabel.isVisible = false
        }
    }

    private fun parseAgentFile(file: File): AgentInfo? {
        return try {
            val name = file.nameWithoutExtension
            val description = if (file.extension == "json") {
                val content = json.parseToJsonElement(file.readText()).jsonObject
                content["description"]?.jsonPrimitive?.content ?: KiroMessages["agent.customDesc"]
            } else {
                KiroMessages["agent.customDesc"]
            }
            AgentInfo(name, description, file, false)
        } catch (e: Exception) {
            null
        }
    }

    private fun createAgentCard(agent: AgentInfo): JPanel {
        val isActive = agent.name == currentAgent
        
        val card = JPanel(BorderLayout()).apply {
            background = if (isActive) KiroUI.Colors.activeBackground else KiroUI.Colors.cardBackground
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                    if (isActive) KiroUI.Colors.activeBorder else JBColor.border(),
                    if (isActive) 2 else 1
                ),
                JBUI.Borders.empty(KiroUI.Spacing.large)
            )
            maximumSize = KiroUI.maxHeightDimension(100)
        }

        // 상단: 이름 + 상태 (AllIcons 사용)
        val headerPanel = JPanel(BorderLayout()).apply { isOpaque = false }
        
        val icon = if (agent.isDefault) KiroUI.Icons.agent else KiroUI.Icons.user
        headerPanel.add(JBLabel(agent.name).apply {
            setIcon(icon)
            font = font.deriveFont(Font.BOLD, 14f)
            if (isActive) foreground = KiroUI.Colors.navSelectedForeground
            iconTextGap = KiroUI.Spacing.medium
        }, BorderLayout.WEST)

        if (isActive) {
            headerPanel.add(JBLabel(KiroMessages["agent.active"]).apply {
                setIcon(KiroUI.Icons.statusOk)
                foreground = KiroUI.Colors.successForeground
                font = font.deriveFont(Font.BOLD, 12f)
                iconTextGap = KiroUI.Spacing.small
            }, BorderLayout.EAST)
        }

        // 중앙: 설명
        val infoPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = JBUI.Borders.emptyTop(KiroUI.Spacing.medium)
        }
        
        infoPanel.add(JBLabel(agent.description).apply {
            foreground = JBColor.gray
            font = font.deriveFont(12f)
        })

        // 하단: 버튼
        val actionPanel = JPanel(FlowLayout(FlowLayout.RIGHT, KiroUI.Spacing.small, 0)).apply {
            isOpaque = false
            border = JBUI.Borders.emptyTop(KiroUI.Spacing.medium)
        }
        
        if (!isActive) {
            actionPanel.add(JButton(KiroMessages["agent.switch"]).apply {
                font = font.deriveFont(11f)
                addActionListener { swapAgent(agent.name) }
            })
        }
        
        if (!agent.isDefault && agent.file != null) {
            actionPanel.add(JButton().apply {
                setIcon(KiroUI.Icons.edit)
                toolTipText = KiroMessages["common.edit"]
                addActionListener { editAgent(agent.file) }
            })
            actionPanel.add(JButton().apply {
                setIcon(KiroUI.Icons.remove)
                toolTipText = KiroMessages["common.delete"]
                addActionListener { deleteAgent(agent) }
            })
        }

        card.add(headerPanel, BorderLayout.NORTH)
        card.add(infoPanel, BorderLayout.CENTER)
        card.add(actionPanel, BorderLayout.SOUTH)
        
        return card
    }

    private fun swapAgent(agentName: String) {
        val command = "/agent swap $agentName"
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Kiro")
        if (toolWindow != null) {
            val chatPanel = KiroToolWindowUtil.getActiveChatPanel(toolWindow)
            chatPanel?.sendToChat(command)
        }
        currentAgent = agentName
        refreshAgents()
    }

    private fun editAgent(file: File) {
        val vFile = LocalFileSystem.getInstance().findFileByIoFile(file)
        if (vFile != null) {
            FileEditorManager.getInstance(project).openFile(vFile, true)
        }
    }

    private fun deleteAgent(agent: AgentInfo) {
        if (KiroUI.showConfirmDialog(panel, "'${agent.name}' ${KiroMessages["agent.deleteConfirm"]}", KiroMessages["agent.deleteTitle"])) {
            agent.file?.delete()
            refreshAgents()
        }
    }

    private fun createNewAgent() {
        val nameField = JTextField(20)
        val descField = JTextField(30)
        
        val dialogPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(KiroUI.Spacing.small, KiroUI.Spacing.small, KiroUI.Spacing.small, KiroUI.Spacing.small)
            anchor = GridBagConstraints.WEST
        }
        
        gbc.gridx = 0; gbc.gridy = 0
        dialogPanel.add(JLabel(KiroMessages["agent.nameLabel"]), gbc)
        gbc.gridx = 1
        dialogPanel.add(nameField, gbc)
        
        gbc.gridx = 0; gbc.gridy = 1
        dialogPanel.add(JLabel(KiroMessages["agent.descLabel"]), gbc)
        gbc.gridx = 1
        dialogPanel.add(descField, gbc)

        val result = JOptionPane.showConfirmDialog(
            panel, dialogPanel, KiroMessages["agent.createTitle"], 
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
        )
        
        if (result == JOptionPane.OK_OPTION) {
            val name = nameField.text.trim()
            val description = descField.text.trim()
            
            if (name.isNotBlank()) {
                createAgentFile(name, description)
            }
        }
    }

    private fun createAgentFile(name: String, description: String) {
        val agentsDir = project.basePath?.let { File(it, ".kiro/agents") }
            ?: File(System.getProperty("user.home"), ".kiro/agents")
        
        agentsDir.mkdirs()
        
        val agentFile = File(agentsDir, "$name.json")
        val content = buildJsonObject {
            put("name", name)
            put("description", description.ifBlank { KiroMessages["agent.customDesc"] })
            put("instructions", "")
        }
        
        agentFile.writeText(json.encodeToString(JsonObject.serializer(), content))
        
        val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(agentFile)
        if (vFile != null) {
            FileEditorManager.getInstance(project).openFile(vFile, true)
        }
        
        refreshAgents()
    }

    private data class AgentInfo(
        val name: String,
        val description: String,
        val file: File?,
        val isDefault: Boolean
    )
}
