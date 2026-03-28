package com.kiro.intellij.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.kiro.intellij.actions.KiroToolWindowUtil
import java.awt.*
import javax.swing.*

/**
 * Skills 관리 패널 - 도구 목록 및 trust/untrust 토글
 * JetBrains UI 가이드라인 준수
 */
class SkillsPanel(private val project: Project) {

    private val panel = JPanel(BorderLayout())
    private val skillsPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }
    private val searchField = SearchTextField()
    private val loadingLabel = JBLabel(AnimatedIcon.Default()).apply { isVisible = false }
    private var allSkills = listOf<SkillInfo>()

    val component: JComponent get() = panel

    init {
        setupUI()
        loadSampleSkills()
    }

    private fun setupUI() {
        panel.border = JBUI.Borders.empty(KiroUI.Spacing.xxlarge)
        panel.background = JBColor.background()

        // 헤더
        val headerPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyBottom(KiroUI.Spacing.xlarge)
        }
        
        headerPanel.add(JBLabel("Skills").apply {
            font = font.deriveFont(Font.BOLD, 18f)
        }, BorderLayout.WEST)

        // 버튼 패널
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, KiroUI.Spacing.medium, 0)).apply {
            isOpaque = false
        }
        
        buttonPanel.add(loadingLabel)
        buttonPanel.add(JButton().apply {
            icon = KiroUI.Icons.refresh
            toolTipText = KiroMessages["common.refresh"]
            addActionListener { refreshSkills() }
        })
        
        headerPanel.add(buttonPanel, BorderLayout.EAST)

        // 검색 필드
        val searchPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyBottom(KiroUI.Spacing.large)
        }
        searchField.textEditor.emptyText.text = KiroMessages["skills.search"]
        searchField.addDocumentListener(object : com.intellij.ui.DocumentAdapter() {
            override fun textChanged(e: javax.swing.event.DocumentEvent) {
                filterSkills(searchField.text)
            }
        })
        searchPanel.add(searchField, BorderLayout.CENTER)

        // 안내 메시지 (AllIcons 사용)
        val infoPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            isOpaque = false
            border = JBUI.Borders.emptyBottom(KiroUI.Spacing.large)
        }
        infoPanel.add(JBLabel(KiroMessages["skills.hint"]).apply {
            icon = KiroUI.Icons.info
            foreground = JBColor.gray
            font = font.deriveFont(12f)
            iconTextGap = KiroUI.Spacing.medium
        })

        // 스킬 목록
        val scrollPane = JBScrollPane(skillsPanel).apply {
            border = null
            viewport.isOpaque = false
            isOpaque = false
        }

        val topPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        topPanel.add(headerPanel)
        topPanel.add(searchPanel)
        topPanel.add(infoPanel)

        panel.add(topPanel, BorderLayout.NORTH)
        panel.add(scrollPane, BorderLayout.CENTER)
    }

    private fun loadSampleSkills() {
        allSkills = listOf(
            SkillInfo("getCurrentSelection", KiroMessages["skills.desc.getCurrentSelection"], "kiro-ide", true),
            SkillInfo("getOpenEditors", KiroMessages["skills.desc.getOpenEditors"], "kiro-ide", true),
            SkillInfo("getWorkspaceFolders", KiroMessages["skills.desc.getWorkspaceFolders"], "kiro-ide", true),
            SkillInfo("getDiagnostics", KiroMessages["skills.desc.getDiagnostics"], "kiro-ide", true),
            SkillInfo("openFile", KiroMessages["skills.desc.openFile"], "kiro-ide", true),
            SkillInfo("openDiff", KiroMessages["skills.desc.openDiff"], "kiro-ide", true)
        )
        displaySkills(allSkills)
    }

    private fun refreshSkills() {
        loadingLabel.isVisible = true
        SwingUtilities.invokeLater {
            loadSampleSkills()
            loadingLabel.isVisible = false
        }
    }

    private fun filterSkills(query: String) {
        val filtered = if (query.isBlank()) {
            allSkills
        } else {
            allSkills.filter { 
                it.name.contains(query, ignoreCase = true) || 
                it.description.contains(query, ignoreCase = true) 
            }
        }
        displaySkills(filtered)
    }

    private fun displaySkills(skills: List<SkillInfo>) {
        skillsPanel.removeAll()
        
        if (skills.isEmpty()) {
            skillsPanel.add(JBLabel(KiroMessages["skills.noResults"]).apply {
                foreground = JBColor.gray
            })
        } else {
            skills.forEach { skill ->
                skillsPanel.add(createSkillCard(skill))
                skillsPanel.add(Box.createVerticalStrut(KiroUI.Spacing.medium))
            }
        }
        
        skillsPanel.add(Box.createVerticalGlue())
        skillsPanel.revalidate()
        skillsPanel.repaint()
    }

    private fun createSkillCard(skill: SkillInfo): JPanel {
        val card = KiroUI.createCard().apply {
            maximumSize = KiroUI.maxHeightDimension(100)
        }

        // 상단: 이름 + 토글 (AllIcons 사용)
        val headerPanel = JPanel(BorderLayout()).apply { 
            isOpaque = false 
            border = JBUI.Borders.empty(KiroUI.Spacing.large)
        }
        
        headerPanel.add(JBLabel(skill.name).apply {
            icon = KiroUI.Icons.skills
            font = font.deriveFont(Font.BOLD, 13f)
            iconTextGap = KiroUI.Spacing.medium
        }, BorderLayout.WEST)

        val toggleButton = JToggleButton(if (skill.trusted) "Trusted" else "Ask").apply {
            isSelected = skill.trusted
            foreground = if (skill.trusted) KiroUI.Colors.successForeground else JBColor.foreground()
            addActionListener {
                skill.trusted = isSelected
                text = if (isSelected) "Trusted" else "Ask"
                foreground = if (isSelected) KiroUI.Colors.successForeground else JBColor.foreground()
                sendTrustCommand(skill.name, isSelected)
            }
        }
        headerPanel.add(toggleButton, BorderLayout.EAST)

        // 중앙: 설명
        val infoPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = JBUI.Borders.empty(0, KiroUI.Spacing.large, KiroUI.Spacing.large, KiroUI.Spacing.large)
        }
        
        infoPanel.add(JBLabel(skill.description).apply {
            foreground = JBColor.gray
            font = font.deriveFont(12f)
        })
        infoPanel.add(Box.createVerticalStrut(KiroUI.Spacing.small))
        infoPanel.add(JBLabel("${KiroMessages["skills.provider"]}: ${skill.provider}").apply {
            foreground = JBColor(Color(100, 100, 100), Color(150, 150, 150))
            font = font.deriveFont(11f)
        })

        card.add(headerPanel, BorderLayout.NORTH)
        card.add(infoPanel, BorderLayout.CENTER)
        
        return card
    }

    private fun sendTrustCommand(toolName: String, trust: Boolean) {
        val command = if (trust) "/tools trust $toolName" else "/tools untrust $toolName"
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Kiro")
        if (toolWindow != null) {
            val chatPanel = KiroToolWindowUtil.getActiveChatPanel(toolWindow)
            chatPanel?.sendToChat(command)
        }
    }

    private data class SkillInfo(
        val name: String,
        val description: String,
        val provider: String,
        var trusted: Boolean
    )
}
