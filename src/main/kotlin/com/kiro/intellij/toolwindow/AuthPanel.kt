package com.kiro.intellij.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import com.kiro.intellij.settings.KiroCliResolver
import com.kiro.intellij.settings.KiroSettings
import java.awt.*
import javax.swing.*

/**
 * 인증 패널 - 로그인 상태 표시 및 로그인/로그아웃
 */
class AuthPanel(private val project: Project) {

    private val panel = JPanel(BorderLayout())
    private val contentPanel = JPanel(CardLayout())
    
    // 로그인 전 화면
    private val welcomePanel = JPanel()
    
    // 로그인 후 화면
    private val loggedInPanel = JPanel()
    private val userInfoArea = JTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        isOpaque = false
        font = font.deriveFont(12f)
        foreground = JBColor.foreground()
        border = null
    }
    
    // CLI 못 찾음 화면
    private val cliNotFoundPanel = JPanel()
    
    // 설정 탭 이동 콜백
    var onNavigateToSettings: (() -> Unit)? = null

    private val loginButton = JButton()
    private val logoutButton = JButton()
    private val loadingLabel = JBLabel(AnimatedIcon.Default()).apply { isVisible = false }

    val component: JComponent get() = panel

    init {
        setupUI()
        refreshAuthStatus()
    }

    private fun setupUI() {
        panel.border = JBUI.Borders.empty(KiroUI.Spacing.xxlarge)
        panel.background = JBColor.background()

        setupWelcomePanel()
        setupLoggedInPanel()
        setupCliNotFoundPanel()
        
        contentPanel.add(welcomePanel, "welcome")
        contentPanel.add(loggedInPanel, "loggedIn")
        contentPanel.add(cliNotFoundPanel, "cliNotFound")
        
        panel.add(JBScrollPane(contentPanel).apply {
            border = null
            viewport.isOpaque = false
            isOpaque = false
        }, BorderLayout.CENTER)
    }

    private fun setupWelcomePanel() {
        welcomePanel.apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }

        // 웰컴 카드
        val welcomeCard = KiroUI.createCard().apply {
            val content = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                border = JBUI.Borders.empty(KiroUI.Spacing.xxlarge)
            }

            // 로고 영역 - Kiro 유령 아이콘 (40x40으로 스케일)
            val kiroGhostIcon = IconLoader.getIcon("/icons/kiro-ghost.svg", AuthPanel::class.java)
            val targetSize = JBUI.scale(48)
            val scaledIcon = IconUtil.scale(kiroGhostIcon, null, targetSize.toFloat() / kiroGhostIcon.iconWidth)
            val logoLabel = JBLabel(scaledIcon)
            logoLabel.alignmentX = Component.CENTER_ALIGNMENT
            
            content.add(Box.createVerticalStrut(KiroUI.Spacing.large))
            content.add(logoLabel)
            content.add(Box.createVerticalStrut(KiroUI.Spacing.xlarge))
            
            // 환영 메시지
            content.add(JBLabel(KiroMessages["auth.welcome"]).apply {
                font = font.deriveFont(Font.BOLD, 20f)
                alignmentX = Component.CENTER_ALIGNMENT
            })
            content.add(Box.createVerticalStrut(KiroUI.Spacing.medium))
            content.add(JBLabel(KiroMessages["auth.welcomeDesc"]).apply {
                foreground = JBColor.gray
                alignmentX = Component.CENTER_ALIGNMENT
            })
            content.add(Box.createVerticalStrut(KiroUI.Spacing.xxlarge))
            
            // 로그인 버튼
            loginButton.apply {
                text = KiroMessages["auth.login"]
                icon = AllIcons.Actions.Execute
                font = font.deriveFont(Font.BOLD, 14f)
                preferredSize = KiroUI.scaledDimension(200, 40)
                maximumSize = KiroUI.scaledDimension(200, 40)
                alignmentX = Component.CENTER_ALIGNMENT
                addActionListener { performLogin() }
            }
            content.add(loginButton)
            content.add(Box.createVerticalStrut(KiroUI.Spacing.large))
            
            add(content, BorderLayout.CENTER)
        }
        welcomeCard.alignmentX = Component.LEFT_ALIGNMENT

        welcomePanel.add(welcomeCard)
        welcomePanel.add(Box.createVerticalGlue())
    }

    private fun setupLoggedInPanel() {
        loggedInPanel.apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }

        // 사용자 정보 카드
        val userCard = KiroUI.createCard().apply {
            add(KiroUI.createCardHeader(KiroMessages["auth.currentStatus"], KiroUI.Icons.auth), BorderLayout.NORTH)
            
            val content = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                border = JBUI.Borders.empty(KiroUI.Spacing.xlarge)
            }

            // 상태 표시
            val statusPanel = JPanel(FlowLayout(FlowLayout.LEFT, KiroUI.Spacing.large, 0)).apply {
                isOpaque = false
            }
            statusPanel.add(JBLabel(AllIcons.General.InspectionsOK))
            statusPanel.add(JBLabel(KiroMessages["auth.loggedIn"]).apply {
                font = font.deriveFont(Font.BOLD, 16f)
                foreground = KiroUI.Colors.successForeground
            })
            content.add(statusPanel)
            content.add(Box.createVerticalStrut(KiroUI.Spacing.large))

            // 사용자 정보 영역 (높이 제한)
            val infoPanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.empty(0, KiroUI.Spacing.xxlarge, 0, 0)
            }
            
            val userIconLabel = JBLabel(AllIcons.General.User).apply {
                verticalAlignment = SwingConstants.TOP
                border = JBUI.Borders.emptyRight(KiroUI.Spacing.medium)
            }
            
            // TextArea 높이 제한 (3줄 정도)
            userInfoArea.apply {
                rows = 3
                preferredSize = Dimension(preferredSize.width, JBUI.scale(60))
                maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(80))
            }
            
            infoPanel.add(userIconLabel, BorderLayout.WEST)
            infoPanel.add(userInfoArea, BorderLayout.CENTER)
            content.add(infoPanel)

            add(content, BorderLayout.CENTER)
            
            // 버튼을 카드 하단(SOUTH)에 배치
            val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, KiroUI.Spacing.medium, 0)).apply {
                isOpaque = false
                border = JBUI.Borders.empty(KiroUI.Spacing.medium, KiroUI.Spacing.xlarge)
            }
            logoutButton.apply {
                text = KiroMessages["auth.logout"]
                icon = AllIcons.Actions.Exit
                addActionListener { performLogout() }
            }
            val refreshButton = JButton().apply {
                icon = KiroUI.Icons.refresh
                toolTipText = KiroMessages["common.refresh"]
                addActionListener { refreshAuthStatus() }
            }
            buttonPanel.add(logoutButton)
            buttonPanel.add(refreshButton)
            buttonPanel.add(loadingLabel)
            
            add(buttonPanel, BorderLayout.SOUTH)
        }
        userCard.alignmentX = Component.LEFT_ALIGNMENT

        loggedInPanel.add(userCard)
        loggedInPanel.add(Box.createVerticalGlue())
    }

    private fun setupCliNotFoundPanel() {
        cliNotFoundPanel.apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }

        val card = KiroUI.createCard().apply {
            val content = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                border = JBUI.Borders.empty(KiroUI.Spacing.xxlarge)
            }

            // 경고 아이콘
            content.add(JBLabel(AllIcons.General.Warning).apply {
                alignmentX = Component.CENTER_ALIGNMENT
            })
            content.add(Box.createVerticalStrut(KiroUI.Spacing.xlarge))

            // 메시지
            content.add(JBLabel(KiroMessages["auth.cliNotFound"]).apply {
                font = font.deriveFont(Font.BOLD, 16f)
                foreground = KiroUI.Colors.errorForeground
                alignmentX = Component.CENTER_ALIGNMENT
            })
            content.add(Box.createVerticalStrut(KiroUI.Spacing.medium))
            content.add(JBLabel(KiroMessages["auth.cliNotFoundDesc"]).apply {
                foreground = JBColor.gray
                alignmentX = Component.CENTER_ALIGNMENT
            })
            content.add(Box.createVerticalStrut(KiroUI.Spacing.xxlarge))

            // 설정 탭 이동 버튼
            val settingsButton = JButton(KiroMessages["auth.goToSettings"]).apply {
                icon = KiroUI.Icons.settings
                font = font.deriveFont(Font.BOLD, 14f)
                preferredSize = KiroUI.scaledDimension(200, 40)
                maximumSize = KiroUI.scaledDimension(200, 40)
                alignmentX = Component.CENTER_ALIGNMENT
                addActionListener { onNavigateToSettings?.invoke() }
            }
            content.add(settingsButton)
            content.add(Box.createVerticalStrut(KiroUI.Spacing.xlarge))

            // 재시도 버튼
            val retryButton = JButton(KiroMessages["common.refresh"]).apply {
                icon = KiroUI.Icons.refresh
                alignmentX = Component.CENTER_ALIGNMENT
                addActionListener { refreshAuthStatus() }
            }
            content.add(retryButton)

            add(content, BorderLayout.CENTER)
        }
        card.alignmentX = Component.LEFT_ALIGNMENT

        cliNotFoundPanel.add(card)
        cliNotFoundPanel.add(Box.createVerticalGlue())
    }

    private fun setLoading(loading: Boolean) {
        loadingLabel.isVisible = loading
        logoutButton.isEnabled = !loading
        loginButton.isEnabled = !loading
    }

    fun refreshAuthStatus() {
        setLoading(true)
        Thread {
            try {
                val kiroCommand = KiroCliResolver.resolve()
                val pb = KiroCliResolver.configureProcessBuilder(
                    ProcessBuilder(kiroCommand, "whoami").redirectErrorStream(true)
                )
                val proc = pb.start()
                val output = proc.inputStream.bufferedReader().readText()
                val exitCode = proc.waitFor()

                SwingUtilities.invokeLater {
                    setLoading(false)
                    parseAuthStatus(output, exitCode)
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    setLoading(false)
                    showCliNotFoundScreen()
                }
            }
        }.start()
    }

    private fun parseAuthStatus(output: String, exitCode: Int) {
        val isLoggedIn = exitCode == 0 && output.isNotBlank() && 
                         !output.contains("not logged in", ignoreCase = true) &&
                         !output.contains("error", ignoreCase = true)
        
        if (isLoggedIn) {
            showLoggedInScreen(output)
        } else {
            showWelcomeScreen()
        }
    }

    private fun showWelcomeScreen() {
        val layout = contentPanel.layout as CardLayout
        layout.show(contentPanel, "welcome")
    }

    private fun showCliNotFoundScreen() {
        val layout = contentPanel.layout as CardLayout
        layout.show(contentPanel, "cliNotFound")
    }

    private fun showLoggedInScreen(userInfo: String) {
        userInfoArea.text = userInfo.trim()
        
        val layout = contentPanel.layout as CardLayout
        layout.show(contentPanel, "loggedIn")
    }

    private fun performLogin() {
        loginButton.isEnabled = false
        loginButton.text = KiroMessages["auth.checking"]
        
        Thread {
            try {
                val kiroCommand = KiroCliResolver.resolve()
                val pb = KiroCliResolver.configureProcessBuilder(
                    ProcessBuilder(kiroCommand, "login").redirectErrorStream(true)
                )
                val proc = pb.start()
                // 출력을 소비하되 표시하지 않음 (스피너 로그 방지)
                proc.inputStream.bufferedReader().readText()
                proc.waitFor()
                SwingUtilities.invokeLater { 
                    loginButton.isEnabled = true
                    loginButton.text = KiroMessages["auth.login"]
                    refreshAuthStatus() 
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    loginButton.isEnabled = true
                    loginButton.text = KiroMessages["auth.login"]
                    KiroUI.showErrorDialog(panel, "${KiroMessages["auth.loginFailed"]}: ${e.message}", KiroMessages["common.error"])
                }
            }
        }.start()
    }

    private fun performLogout() {
        setLoading(true)
        Thread {
            try {
                val kiroCommand = KiroCliResolver.resolve()
                val pb = KiroCliResolver.configureProcessBuilder(
                    ProcessBuilder(kiroCommand, "logout").redirectErrorStream(true)
                )
                pb.start().waitFor()
                SwingUtilities.invokeLater { refreshAuthStatus() }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    KiroUI.showErrorDialog(panel, "${KiroMessages["auth.logoutFailed"]}: ${e.message}", KiroMessages["common.error"])
                }
            }
        }.start()
    }
}
