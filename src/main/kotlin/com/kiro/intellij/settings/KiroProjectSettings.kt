package com.kiro.intellij.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project

/**
 * 프로젝트 레벨 설정. 프로젝트마다 독립적으로 저장된다.
 * - 프로젝트별 MCP 설정 디렉토리
 * - 프로젝트별 모델 오버라이드
 * - 프로젝트별 자동 시작 설정
 */
@Service(Service.Level.PROJECT)
@State(name = "KiroProjectSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class KiroProjectSettings : PersistentStateComponent<KiroProjectSettings.State> {

    data class State(
        var modelOverride: String = "",
        var mcpConfigDir: String = ""
    ) {
        fun getEffectiveModel(global: KiroSettings.State): String {
            return modelOverride.ifBlank { global.defaultModel }
        }

        fun getEffectiveMcpConfigDir(global: KiroSettings.State): String {
            return mcpConfigDir.ifBlank { global.kiroConfigDir }
        }
    }

    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) { myState = state }

    companion object {
        fun getInstance(project: Project): KiroProjectSettings =
            project.getService(KiroProjectSettings::class.java)
    }
}
