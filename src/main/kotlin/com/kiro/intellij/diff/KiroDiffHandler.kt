package com.kiro.intellij.diff

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileManager

/**
 * kiro-cli가 파일을 수정하면 감지하여 IDE diff viewer로 표시.
 * git diff 기반으로 원본과 수정본을 비교한다.
 */
class KiroDiffHandler(private val project: Project) {

    fun showDiff(filePath: String, originalContent: String, modifiedContent: String) {
        ApplicationManager.getApplication().invokeLater {
            val fileName = filePath.substringAfterLast('/')
            val request = SimpleDiffRequest(
                "Kiro: $fileName",
                DiffContentFactory.getInstance().create(originalContent),
                DiffContentFactory.getInstance().create(modifiedContent),
                "Original",
                "Kiro Suggestion"
            )
            DiffManager.getInstance().showDiff(project, request)
        }
    }

    fun showDiffForFile(filePath: String) {
        val vFile = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return
        val currentContent = ApplicationManager.getApplication().runReadAction<String> {
            FileDocumentManager.getInstance().getDocument(vFile)?.text ?: return@runReadAction ""
        }

        // git show HEAD:<file> 로 원본 가져오기
        val originalContent = getGitOriginal(filePath) ?: return

        if (originalContent != currentContent) {
            showDiff(filePath, originalContent, currentContent)
        }
    }

    private fun getGitOriginal(filePath: String): String? {
        return try {
            val basePath = project.basePath ?: return null
            val relativePath = filePath.removePrefix("$basePath/")
            val process = ProcessBuilder("git", "show", "HEAD:$relativePath")
                .directory(java.io.File(basePath))
                .redirectErrorStream(true)
                .start()
            val content = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode == 0) content else null
        } catch (e: Exception) {
            null
        }
    }
}
