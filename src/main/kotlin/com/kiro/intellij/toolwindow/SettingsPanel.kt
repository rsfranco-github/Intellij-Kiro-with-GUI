package com.kiro.intellij.toolwindow

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.kiro.intellij.settings.KiroCliResolver
import com.kiro.intellij.settings.KiroSettings
import java.awt.*
import javax.swing.*

/**
 * 설정 패널 - kiro-cli 경로, 기본 모델, 언어, 테마 등
 * JetBrains UI 가이드라인 준수
 */
class SettingsPanel(private val project: Project) {

    companion object {
        fun resolveEffectiveConfigDir(inputDir: String): String {
            return if (inputDir.isBlank()) {
                System.getProperty("user.home") + "/.kiro"
            } else {
                inputDir
            }
        }

        /**
         * Builds the config dir label text based on whether a custom dir is set.
         * Extracted for testability.
         */
        fun buildConfigDirLabel(configDir: String, currentPathPrefix: String, defaultSuffix: String): String {
            val effectivePath = resolveEffectiveConfigDir(configDir)
            val isCustom = configDir.isNotBlank()
            return if (isCustom) {
                "$currentPathPrefix $effectivePath"
            } else {
                "$currentPathPrefix $effectivePath $defaultSuffix"
            }
        }
    }

    private val panel = JPanel(BorderLayout())

    private val kiroPathField = TextFieldWithBrowseButton()
    private val kiroConfigDirField = TextFieldWithBrowseButton()
    private val modelComboBox = JComboBox(arrayOf(
        "Auto", "claude-opus-4.6", "claude-sonnet-4.6", "claude-opus-4.5",
        "claude-sonnet-4.5", "claude-sonnet-4", "claude-haiku-4.5",
        "deepseek-3.2", "minimax-m2.1", "minimax-m2.5", "qwen3-coder-next"
    ))
    private val languageComboBox = JComboBox(arrayOf(
        "Korean" to "ko",
        "English" to "en"
    ).map { it.first }.toTypedArray())
    private val languageMap = mapOf("Korean" to "ko", "English" to "en")
    private val reverseLanguageMap = mapOf("ko" to "Korean", "en" to "English")

    private val themeComboBox = JComboBox(arrayOf("Auto", "Light", "Dark"))
    private val themeMap = mapOf("Auto" to "auto", "Light" to "light", "Dark" to "dark")
    private val reverseThemeMap = mapOf("auto" to "Auto", "light" to "Light", "dark" to "Dark")

    private lateinit var configDirActivePathLabel: JBLabel

    val component: JComponent get() = panel

    init {
        setupUI()
        loadSettings()
    }

    private fun setupUI() {
        panel.border = JBUI.Borders.empty(KiroUI.Spacing.xxlarge)
        panel.background = JBColor.background()

        // 헤더
        val headerLabel = JBLabel(KiroMessages["settings.title"]).apply {
            font = font.deriveFont(Font.BOLD, 18f)
            border = JBUI.Borders.emptyBottom(KiroUI.Spacing.xlarge)
        }

        // kiro-cli 경로 카드
        val pathCard = KiroUI.createCard().apply {
            add(KiroUI.createCardHeader(KiroMessages["settings.cliPath"], KiroUI.Icons.settings), BorderLayout.NORTH)

            val contentPanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.empty(KiroUI.Spacing.large)
            }

            kiroPathField.addBrowseFolderListener(
                "kiro-cli",
                KiroMessages["settings.cliPathDesc"],
                project,
                FileChooserDescriptorFactory.createSingleFileDescriptor()
            )

            contentPanel.add(kiroPathField, BorderLayout.CENTER)

            // 실제 경로 표시
            val resolvedPath = resolveKiroCliPath(kiroPathField.text.ifBlank { "kiro-cli" })
            val pathInfoLabel = JBLabel(resolvedPath).apply {
                foreground = if (resolvedPath.startsWith("⚠")) KiroUI.Colors.errorForeground else JBColor.gray
                font = font.deriveFont(11f)
                border = JBUI.Borders.emptyTop(KiroUI.Spacing.medium)
            }
            contentPanel.add(pathInfoLabel, BorderLayout.SOUTH)

            add(contentPanel, BorderLayout.CENTER)
        }

        // kiro 설정 디렉토리 카드
        val configDirCard = KiroUI.createCard().apply {
            add(KiroUI.createCardHeader(KiroMessages["settings.configDir"], KiroUI.Icons.folder), BorderLayout.NORTH)

            val contentPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                border = JBUI.Borders.empty(KiroUI.Spacing.large)
            }

            kiroConfigDirField.addBrowseFolderListener(
                KiroMessages["settings.configDir"],
                KiroMessages["settings.configDirDesc"],
                project,
                FileChooserDescriptorFactory.createSingleFolderDescriptor()
            )
            kiroConfigDirField.alignmentX = Component.LEFT_ALIGNMENT
            contentPanel.add(kiroConfigDirField)
            contentPanel.add(Box.createVerticalStrut(KiroUI.Spacing.medium))

            val hintLabel = JBLabel(KiroMessages["settings.configDirHint"]).apply {
                foreground = JBColor.gray
                font = font.deriveFont(11f)
                alignmentX = Component.LEFT_ALIGNMENT
            }
            contentPanel.add(hintLabel)
            contentPanel.add(Box.createVerticalStrut(KiroUI.Spacing.small))

            configDirActivePathLabel = JBLabel().apply {
                icon = KiroUI.Icons.folder
                iconTextGap = KiroUI.Spacing.small
                font = font.deriveFont(11f)
                alignmentX = Component.LEFT_ALIGNMENT
            }
            updateConfigDirLabel(kiroConfigDirField.text)
            contentPanel.add(configDirActivePathLabel)

            add(contentPanel, BorderLayout.CENTER)
        }

        // 기본 모델 카드
        val modelCard = KiroUI.createCard().apply {
            add(KiroUI.createCardHeader(KiroMessages["settings.defaultModel"]), BorderLayout.NORTH)

            val contentPanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.empty(KiroUI.Spacing.large)
            }

            modelComboBox.maximumSize = KiroUI.scaledDimension(300, 30)
            contentPanel.add(modelComboBox, BorderLayout.NORTH)
            contentPanel.add(JBLabel(KiroMessages["settings.defaultModelDesc"]).apply {
                foreground = JBColor.gray
                border = JBUI.Borders.emptyTop(KiroUI.Spacing.medium)
            }, BorderLayout.CENTER)

            add(contentPanel, BorderLayout.CENTER)
        }

        // 언어 설정 카드
        val languageCard = KiroUI.createCard().apply {
            add(KiroUI.createCardHeader(KiroMessages["settings.language"]), BorderLayout.NORTH)

            val contentPanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.empty(KiroUI.Spacing.large)
            }

            languageComboBox.maximumSize = KiroUI.scaledDimension(200, 30)
            contentPanel.add(languageComboBox, BorderLayout.NORTH)
            contentPanel.add(JBLabel(KiroMessages["settings.languageDesc"]).apply {
                foreground = JBColor.gray
                border = JBUI.Borders.emptyTop(KiroUI.Spacing.medium)
            }, BorderLayout.CENTER)

            add(contentPanel, BorderLayout.CENTER)
        }

        // 테마 설정 카드
        val themeCard = KiroUI.createCard().apply {
            add(KiroUI.createCardHeader("Theme:"), BorderLayout.NORTH)

            val contentPanel = JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.empty(KiroUI.Spacing.large)
            }

            themeComboBox.maximumSize = KiroUI.scaledDimension(200, 30)
            contentPanel.add(themeComboBox, BorderLayout.NORTH)
            contentPanel.add(JBLabel("Chat panel color theme (Auto follows IDE theme)").apply {
                foreground = JBColor.gray
                border = JBUI.Borders.emptyTop(KiroUI.Spacing.medium)
            }, BorderLayout.CENTER)

            add(contentPanel, BorderLayout.CENTER)
        }

        // 버튼 패널
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, KiroUI.Spacing.medium, 0)).apply {
            isOpaque = false
            border = JBUI.Borders.emptyTop(KiroUI.Spacing.xlarge)
        }

        val saveButton = JButton(KiroMessages["settings.save"]).apply {
            addActionListener { saveSettings() }
        }
        val resetButton = JButton(KiroMessages["settings.reset"]).apply {
            addActionListener { resetSettings() }
        }
        val openConfigButton = JButton(KiroMessages["settings.openConfig"]).apply {
            icon = KiroUI.Icons.folder
            addActionListener { openConfigFile() }
        }

        buttonPanel.add(saveButton)
        buttonPanel.add(resetButton)
        buttonPanel.add(openConfigButton)

        // 조립
        val contentWrapper = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }

        headerLabel.alignmentX = Component.LEFT_ALIGNMENT
        pathCard.alignmentX = Component.LEFT_ALIGNMENT
        configDirCard.alignmentX = Component.LEFT_ALIGNMENT
        modelCard.alignmentX = Component.LEFT_ALIGNMENT
        languageCard.alignmentX = Component.LEFT_ALIGNMENT
        themeCard.alignmentX = Component.LEFT_ALIGNMENT
        buttonPanel.alignmentX = Component.LEFT_ALIGNMENT

        contentWrapper.add(headerLabel)
        contentWrapper.add(pathCard)
        contentWrapper.add(Box.createVerticalStrut(KiroUI.Spacing.large))
        contentWrapper.add(configDirCard)
        contentWrapper.add(Box.createVerticalStrut(KiroUI.Spacing.large))
        contentWrapper.add(modelCard)
        contentWrapper.add(Box.createVerticalStrut(KiroUI.Spacing.large))
        contentWrapper.add(languageCard)
        contentWrapper.add(Box.createVerticalStrut(KiroUI.Spacing.large))
        contentWrapper.add(themeCard)
        contentWrapper.add(buttonPanel)
        contentWrapper.add(Box.createVerticalGlue())

        panel.add(JBScrollPane(contentWrapper).apply {
            border = null
            viewport.isOpaque = false
            isOpaque = false
        }, BorderLayout.CENTER)
    }

    private fun loadSettings() {
        val state = KiroSettings.getInstance().state
        kiroPathField.text = state.kiroCommand
        kiroConfigDirField.text = state.kiroConfigDir
        modelComboBox.selectedItem = state.defaultModel
        languageComboBox.selectedItem = reverseLanguageMap[state.language] ?: "English"
        themeComboBox.selectedItem = reverseThemeMap[state.theme] ?: "Auto"
        updateConfigDirLabel(state.kiroConfigDir)
    }

    private fun saveSettings() {
        val settings = KiroSettings.getInstance()
        val oldLanguage = settings.state.language
        val newLanguage = languageMap[languageComboBox.selectedItem as String] ?: "en"
        val languageChanged = oldLanguage != newLanguage

        val oldTheme = settings.state.theme
        val newTheme = themeMap[themeComboBox.selectedItem as String] ?: "auto"
        val themeChanged = oldTheme != newTheme

        settings.state.kiroCommand = kiroPathField.text.ifBlank { "kiro-cli" }
        settings.state.kiroConfigDir = kiroConfigDirField.text
        settings.state.defaultModel = modelComboBox.selectedItem as String
        settings.state.language = newLanguage
        settings.state.theme = newTheme

        updateConfigDirLabel(settings.state.kiroConfigDir)

        if (themeChanged) {
            KiroSettings.notifyThemeChange()
        }

        if (languageChanged) {
            KiroUI.showInfoDialog(
                panel,
                "Settings saved.\n\nPlease restart IDE to apply language change.",
                "Restart Required"
            )
        } else {
            KiroUI.showInfoDialog(panel, KiroMessages["settings.saved"], KiroMessages["settings.saveComplete"])
        }
    }

    private fun resetSettings() {
        if (KiroUI.showConfirmDialog(panel, KiroMessages["settings.resetConfirm"], KiroMessages["settings.resetTitle"])) {
            val settings = KiroSettings.getInstance()
            settings.loadState(KiroSettings.State())
            loadSettings()
        }
    }

    private fun openConfigFile() {
        val effectiveDir = resolveEffectiveConfigDir(kiroConfigDirField.text)
        val configDir = java.io.File(effectiveDir, "settings")
        if (configDir.exists()) {
            Desktop.getDesktop().open(configDir)
        } else {
            KiroUI.showInfoDialog(panel, "${KiroMessages["settings.configNotFound"]}: ${configDir.absolutePath}", KiroMessages["settings.info"])
        }
    }

    private fun updateConfigDirLabel(configDir: String) {
        val effectivePath = resolveEffectiveConfigDir(configDir)
        val exists = java.io.File(effectivePath).exists()
        val isCustom = configDir.isNotBlank()

        configDirActivePathLabel.text = if (isCustom) {
            "${KiroMessages["settings.configDirCurrent"]} $effectivePath"
        } else {
            "${KiroMessages["settings.configDirCurrent"]} $effectivePath ${KiroMessages["settings.configDirDefault"]}"
        }
        configDirActivePathLabel.foreground = if (exists) {
            KiroUI.Colors.successForeground
        } else {
            KiroUI.Colors.errorForeground
        }
    }

    private fun resolveKiroCliPath(command: String): String {
        // KiroCliResolver로 실제 경로 확인
        val resolved = KiroCliResolver.resolve()
        return if (resolved.startsWith("/")) {
            "📍 $resolved"
        } else {
            "⚠️ ${KiroMessages["settings.cliNotFound"]}"
        }
    }
}
