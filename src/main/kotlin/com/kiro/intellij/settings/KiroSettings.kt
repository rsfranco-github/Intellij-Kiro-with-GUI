package com.kiro.intellij.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(name = "KiroSettings", storages = [Storage("KiroWithGui.xml")])
class KiroSettings : PersistentStateComponent<KiroSettings.State> {

    data class State(
        var kiroCommand: String = "kiro-cli",
        var defaultModel: String = "Auto",
        var autoStart: Boolean = true
    )

    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) { myState = state }

    companion object {
        fun getInstance(): KiroSettings =
            ApplicationManager.getApplication().getService(KiroSettings::class.java)
    }
}
