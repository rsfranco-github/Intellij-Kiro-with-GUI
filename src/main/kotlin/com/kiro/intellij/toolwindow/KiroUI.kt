package com.kiro.intellij.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.*

/**
 * Kiro 플러그인 공통 UI 컴포넌트 및 디자인 시스템
 * JetBrains UI 가이드라인 준수
 */
object KiroUI {

    // ========== 색상 시스템 ==========
    object Colors {
        val cardBackground = JBColor.namedColor("Kiro.Card.background", JBColor(0xFFFFFF, 0x3C3F41))
        val cardHeaderBackground = JBColor.namedColor("Kiro.Card.headerBackground", JBColor(0xF8F9FA, 0x373A3C))
        val activeBackground = JBColor.namedColor("Kiro.Active.background", JBColor(0xE6F5FF, 0x28405A))
        val activeBorder = JBColor.namedColor("Kiro.Active.border", JBColor(0x0078D7, 0x508CC8))
        val navBackground = JBColor.namedColor("Kiro.Nav.background", JBColor(0xF5F5F5, 0x323232))
        val navSelectedBackground = JBColor.namedColor("Kiro.Nav.selectedBackground", JBColor(0xE6F0FF, 0x3C5064))
        val navSelectedForeground = JBColor.namedColor("Kiro.Nav.selectedForeground", JBColor(0x0064C8, 0x96C8FF))
        val successForeground = JBColor.namedColor("Kiro.Success.foreground", JBColor(0x008000, 0x64C864))
        val errorForeground = JBColor.namedColor("Kiro.Error.foreground", JBColor(0xCC0000, 0xFF6B6B))
    }

    // ========== 아이콘 시스템 (AllIcons 사용) ==========
    object Icons {
        // 네비게이션 탭
        val auth: Icon = AllIcons.Ide.LocalScope
        val settings: Icon = AllIcons.General.Settings
        val mcp: Icon = AllIcons.Actions.Lightning
        val skills: Icon = AllIcons.Nodes.Plugin
        val agent: Icon = AllIcons.Actions.Execute

        // 상태 아이콘
        val statusOk: Icon = AllIcons.General.InspectionsOK
        val statusError: Icon = AllIcons.General.Error
        val statusRunning: Icon = AllIcons.Actions.Execute

        // 액션 아이콘
        val refresh: Icon = AllIcons.Actions.Refresh
        val add: Icon = AllIcons.General.Add
        val remove: Icon = AllIcons.General.Remove
        val edit: Icon = AllIcons.Actions.Edit

        // 기타
        val info: Icon = AllIcons.General.Information
        val folder: Icon = AllIcons.Nodes.Folder
        val web: Icon = AllIcons.General.Web
        val user: Icon = AllIcons.General.User
    }

    // ========== 간격 시스템 (HiDPI 대응) ==========
    object Spacing {
        val small: Int get() = JBUI.scale(4)
        val medium: Int get() = JBUI.scale(8)
        val large: Int get() = JBUI.scale(12)
        val xlarge: Int get() = JBUI.scale(16)
        val xxlarge: Int get() = JBUI.scale(20)

        val navWidth: Int get() = JBUI.scale(100)
        val navItemHeight: Int get() = JBUI.scale(44)
        val cardMaxHeight: Int get() = JBUI.scale(120)
    }

    // ========== 카드 컴포넌트 ==========
    
    /**
     * 카드 패널 생성
     */
    fun createCard(): JPanel {
        return JPanel(BorderLayout()).apply {
            background = Colors.cardBackground
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border(), 1),
                JBUI.Borders.empty()
            )
        }
    }

    /**
     * 카드 헤더 생성
     */
    fun createCardHeader(title: String, icon: Icon? = null, actions: JComponent? = null): JPanel {
        return JPanel(BorderLayout()).apply {
            background = Colors.cardHeaderBackground
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border()),
                JBUI.Borders.empty(Spacing.medium, Spacing.large)
            )

            val titleLabel = JBLabel(title).apply {
                font = font.deriveFont(java.awt.Font.BOLD, 13f)
                if (icon != null) {
                    this.icon = icon
                    iconTextGap = Spacing.medium
                }
            }
            add(titleLabel, BorderLayout.WEST)

            if (actions != null) {
                add(actions, BorderLayout.EAST)
            }
        }
    }

    // ========== 상태 라벨 ==========

    /**
     * 상태 라벨 생성 (아이콘 + 텍스트)
     */
    fun createStatusLabel(isActive: Boolean, activeText: String, inactiveText: String): JBLabel {
        return JBLabel().apply {
            icon = if (isActive) Icons.statusOk else Icons.statusError
            text = if (isActive) activeText else inactiveText
            foreground = if (isActive) Colors.successForeground else Colors.errorForeground
            iconTextGap = Spacing.small
        }
    }

    /**
     * 상태 아이콘만 반환
     */
    fun getStatusIcon(isActive: Boolean): Icon {
        return if (isActive) Icons.statusOk else Icons.statusError
    }

    // ========== 다이얼로그 (JOptionPane 대체) ==========

    /**
     * 확인 다이얼로그 (Yes/No)
     */
    fun showConfirmDialog(parent: Component?, message: String, title: String): Boolean {
        return Messages.showYesNoDialog(
            message,
            title,
            Messages.getQuestionIcon()
        ) == Messages.YES
    }

    /**
     * 정보 다이얼로그
     */
    fun showInfoDialog(parent: Component?, message: String, title: String) {
        Messages.showInfoMessage(parent, message, title)
    }

    /**
     * 에러 다이얼로그
     */
    fun showErrorDialog(parent: Component?, message: String, title: String) {
        Messages.showErrorDialog(parent, message, title)
    }

    // ========== 유틸리티 ==========

    /**
     * 스케일된 Dimension 생성
     */
    fun scaledDimension(width: Int, height: Int): Dimension {
        return Dimension(JBUI.scale(width), JBUI.scale(height))
    }

    /**
     * 최대 크기 설정 (width는 무제한)
     */
    fun maxHeightDimension(height: Int): Dimension {
        return Dimension(Int.MAX_VALUE, JBUI.scale(height))
    }
}
