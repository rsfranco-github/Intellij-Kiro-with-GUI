package com.kiro.intellij.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class KiroSettingsConfigurable : Configurable {

    private var commandField: TextFieldWithBrowseButton? = null
    private val models = arrayOf("Auto", "claude-opus-4.6", "claude-opus-4.5", "claude-sonnet-4.5", "claude-sonnet-4.0", "claude-haiku-4.5")
    private var modelCombo: javax.swing.JComboBox<String>? = null

    override fun getDisplayName(): String = "Kiro"

    override fun createComponent(): JComponent {
        val settings = KiroSettings.getInstance().state
        commandField = TextFieldWithBrowseButton().apply {
            text = settings.kiroCommand
            addBrowseFolderListener(null,
                FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                    .withDescription("Path to kiro-cli executable"))
        }
        modelCombo = javax.swing.JComboBox(models).apply {
            selectedItem = settings.defaultModel
        }

        return panel {
            row("Kiro command:") { cell(commandField!!) }
            row("Default model:") { cell(modelCombo!!) }
        }
    }

    override fun isModified(): Boolean {
        val settings = KiroSettings.getInstance().state
        return commandField?.text != settings.kiroCommand
                || modelCombo?.selectedItem != settings.defaultModel
    }

    override fun apply() {
        val settings = KiroSettings.getInstance()
        settings.loadState(KiroSettings.State(
            kiroCommand = commandField?.text ?: "kiro-cli",
            defaultModel = modelCombo?.selectedItem as? String ?: "Auto"
        ))
    }

    override fun reset() {
        val settings = KiroSettings.getInstance().state
        commandField?.text = settings.kiroCommand
        modelCombo?.selectedItem = settings.defaultModel
    }

    override fun disposeUIResources() {
        commandField = null
        modelCombo = null
    }
}
