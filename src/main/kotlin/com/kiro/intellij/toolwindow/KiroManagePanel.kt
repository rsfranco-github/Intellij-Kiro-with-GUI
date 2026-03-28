package com.kiro.intellij.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*

/**
 * 관리 패널 - 좌측 아이콘 네비게이션 + 우측 콘텐츠 레이아웃
 * JetBrains UI 가이드라인 준수
 */
class KiroManagePanel(private val project: Project, private val parentDisposable: Disposable) : Disposable {

    companion object {
        val KEY = Key.create<KiroManagePanel>("KiroManagePanel")
    }

    private val mainPanel = JPanel(BorderLayout())
    private val contentPanel = JPanel(CardLayout())
    private val navButtons = mutableListOf<NavButton>()
    
    private val authPanel = AuthPanel(project)
    private val settingsPanel = SettingsPanel(project)
    private val mcpPanel = McpPanel(project)
    private val skillsPanel = SkillsPanel(project)
    private val agentPanel = AgentPanel(project)

    val component: JComponent get() = mainPanel

    init {
        Disposer.register(parentDisposable, this)
        // AuthPanel에서 설정 탭으로 이동하는 콜백 연결
        authPanel.onNavigateToSettings = { selectNav("settings") }
        setupUI()
        
        // 관리 탭이 보일 때마다 인증 상태 자동 갱신
        mainPanel.addHierarchyListener { e ->
            if ((e.changeFlags and java.awt.event.HierarchyEvent.SHOWING_CHANGED.toLong()) != 0L && mainPanel.isShowing) {
                authPanel.refreshAuthStatus()
            }
        }
    }

    private fun setupUI() {
        // 좌측 네비게이션 (아이콘만)
        val navPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = KiroUI.Colors.navBackground
            border = JBUI.Borders.customLine(JBColor.border(), 0, 0, 0, 1)
            preferredSize = Dimension(JBUI.scale(44), 0) // 아이콘만이므로 좁게
        }

        // AllIcons 사용 + 다국어 툴팁
        val navItems = listOf(
            NavItem("auth", KiroMessages["nav.auth"], KiroUI.Icons.auth, authPanel.component),
            NavItem("settings", KiroMessages["nav.settings"], KiroUI.Icons.settings, settingsPanel.component),
            NavItem("mcp", KiroMessages["nav.mcp"], KiroUI.Icons.mcp, mcpPanel.component),
            NavItem("skills", KiroMessages["nav.skills"], KiroUI.Icons.skills, skillsPanel.component),
            NavItem("agent", KiroMessages["nav.agent"], KiroUI.Icons.agent, agentPanel.component)
        )

        navItems.forEach { item ->
            contentPanel.add(item.panel, item.id)
            val btn = NavButton(item.icon, item.tooltip) {
                selectNav(item.id)
            }
            navButtons.add(btn)
            navPanel.add(btn)
        }
        navPanel.add(Box.createVerticalGlue())

        // 첫 번째 항목 선택
        selectNav("auth")

        mainPanel.add(navPanel, BorderLayout.WEST)
        mainPanel.add(contentPanel, BorderLayout.CENTER)
    }

    private fun selectNav(id: String) {
        val layout = contentPanel.layout as CardLayout
        layout.show(contentPanel, id)
        
        navButtons.forEachIndexed { index, btn ->
            val navId = listOf("auth", "settings", "mcp", "skills", "agent")[index]
            btn.setNavSelected(navId == id)
        }
        
        // auth 탭 선택 시 인증 상태 자동 갱신
        if (id == "auth") {
            authPanel.refreshAuthStatus()
        }
    }

    override fun dispose() {}

    private data class NavItem(val id: String, val tooltip: String, val icon: Icon, val panel: JComponent)

    /**
     * 네비게이션 버튼 - 아이콘만 표시, 호버 시 툴팁
     */
    private class NavButton(navIcon: Icon, tooltip: String, onClick: () -> Unit) : JButton() {
        private var selected: Boolean = false

        fun setNavSelected(value: Boolean) {
            selected = value
            updateStyle()
        }

        init {
            setIcon(navIcon)
            toolTipText = tooltip
            horizontalAlignment = SwingConstants.CENTER
            isFocusPainted = false
            isBorderPainted = false
            isContentAreaFilled = false
            border = JBUI.Borders.empty(KiroUI.Spacing.medium)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            preferredSize = Dimension(JBUI.scale(44), JBUI.scale(44))
            maximumSize = Dimension(JBUI.scale(44), JBUI.scale(44))
            minimumSize = Dimension(JBUI.scale(44), JBUI.scale(44))
            addActionListener { onClick() }
            updateStyle()
        }

        private fun updateStyle() {
            background = if (selected) KiroUI.Colors.navSelectedBackground else KiroUI.Colors.navBackground
            isOpaque = selected
        }

        override fun paintComponent(g: Graphics) {
            if (selected) {
                g.color = background
                g.fillRect(0, 0, width, height)
                
                // 선택된 항목에 왼쪽 강조선
                g.color = KiroUI.Colors.activeBorder
                g.fillRect(0, 0, JBUI.scale(3), height)
            }
            super.paintComponent(g)
        }
    }
}
