package com.kiro.intellij.settings

import java.io.File

/**
 * kiro-cli 실행 경로를 자동으로 찾아주는 유틸리티.
 * macOS GUI 앱에서는 터미널의 PATH를 상속받지 않으므로,
 * 일반적인 설치 경로들을 직접 탐색합니다.
 */
object KiroCliResolver {

    // kiro-cli가 설치될 수 있는 일반적인 경로들
    private val SEARCH_PATHS = listOf(
        "\${HOME}/.local/bin",
        "/usr/local/bin",
        "/opt/homebrew/bin",
        "\${HOME}/.nvm/versions/node/*/bin",
        "\${HOME}/.volta/bin",
        "/usr/bin",
        "\${HOME}/bin",
        "\${HOME}/.cargo/bin",
        "/opt/local/bin"
    )

    /**
     * kiro-cli 실행 가능한 전체 경로를 반환합니다.
     * 설정에 경로가 지정되어 있으면 그것을 사용하고,
     * 아니면 일반적인 경로들을 탐색합니다.
     */
    fun resolve(): String {
        val configured = KiroSettings.getInstance().state.kiroCommand
        
        // 절대 경로가 설정되어 있으면 그대로 사용
        if (configured.startsWith("/") && File(configured).canExecute()) {
            return configured
        }
        
        val commandName = configured.ifBlank { "kiro-cli" }
        return resolveCommand(commandName)
    }

    /**
     * 임의의 명령어(node, docker, kiro-cli 등)의 절대 경로를 탐색합니다.
     * SEARCH_PATHS에서 해당 명령어를 찾고, 못 찾으면 원래 이름을 반환합니다.
     */
    fun resolveCommand(commandName: String): String {
        val home = System.getProperty("user.home")

        for (pathPattern in SEARCH_PATHS) {
            val expanded = pathPattern.replace("\${HOME}", home)

            if (expanded.contains("*")) {
                val parts = expanded.split("*")
                if (parts.size == 2) {
                    val parentDir = File(parts[0].trimEnd('/'))
                    if (parentDir.isDirectory) {
                        parentDir.listFiles()?.forEach { dir ->
                            val candidate = File(dir.absolutePath + parts[1], commandName)
                            if (candidate.canExecute()) return candidate.absolutePath
                        }
                    }
                }
            } else {
                val candidate = File(expanded, commandName)
                if (candidate.canExecute()) return candidate.absolutePath
            }
        }

        return commandName
    }

    /**
     * ProcessBuilder에 확장된 PATH를 설정합니다.
     */
    fun configureProcessBuilder(pb: ProcessBuilder): ProcessBuilder {
        val home = System.getProperty("user.home")
        val env = pb.environment()
        val currentPath = env["PATH"] ?: ""
        
        val extraPaths = SEARCH_PATHS
            .map { it.replace("\${HOME}", home) }
            .filter { !it.contains("*") }
            .filter { File(it).isDirectory }
        
        if (extraPaths.isNotEmpty()) {
            env["PATH"] = (extraPaths + currentPath).joinToString(":")
        }
        
        return pb
    }
}
