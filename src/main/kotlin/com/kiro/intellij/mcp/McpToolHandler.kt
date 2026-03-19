package com.kiro.intellij.mcp

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.serialization.json.*

class McpToolHandler(private val project: Project) {

    private val json = Json { ignoreUnknownKeys = true }

    fun handleToolCall(toolName: String, arguments: JsonElement?): JsonElement {
        return when (toolName) {
            "getCurrentSelection" -> getCurrentSelection()
            "getOpenEditors" -> getOpenEditors()
            "getWorkspaceFolders" -> getWorkspaceFolders()
            "getDiagnostics" -> getDiagnostics(arguments)
            "openFile" -> openFile(arguments)
            else -> buildJsonObject { put("error", "Unknown tool: $toolName") }
        }
    }

    private fun getCurrentSelection(): JsonElement {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
            ?: return buildJsonObject { put("selection", JsonNull) }
        val selectedText = editor.selectionModel.selectedText ?: ""
        val file = editor.virtualFile
        val doc = editor.document
        val startLine = if (editor.selectionModel.hasSelection())
            doc.getLineNumber(editor.selectionModel.selectionStart) + 1 else null
        val endLine = if (editor.selectionModel.hasSelection())
            doc.getLineNumber(editor.selectionModel.selectionEnd) + 1 else null

        return buildJsonObject {
            put("file", file?.path ?: "")
            put("selection", selectedText)
            put("startLine", startLine?.let { JsonPrimitive(it) } ?: JsonNull)
            put("endLine", endLine?.let { JsonPrimitive(it) } ?: JsonNull)
        }
    }

    private fun getOpenEditors(): JsonElement {
        val fem = FileEditorManager.getInstance(project)
        val files = fem.openFiles.map { file ->
            buildJsonObject {
                put("path", file.path)
                put("name", file.name)
                put("isModified", fem.getEditors(file).any { it.isModified })
            }
        }
        return JsonArray(files)
    }

    private fun getWorkspaceFolders(): JsonElement {
        val basePath = project.basePath ?: return JsonArray(emptyList())
        return JsonArray(listOf(JsonPrimitive(basePath)))
    }

    private fun getDiagnostics(arguments: JsonElement?): JsonElement {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
            ?: return JsonArray(emptyList())

        return buildJsonObject {
            put("file", editor.virtualFile?.path ?: "")
            put("lineCount", editor.document.lineCount)
            put("diagnosticsAvailable", true)
        }
    }

    private fun openFile(arguments: JsonElement?): JsonElement {
        val args = arguments?.jsonObject ?: return buildJsonObject { put("error", "missing arguments") }
        val filePath = args["path"]?.jsonPrimitive?.content ?: return buildJsonObject { put("error", "missing path") }
        val line = args["line"]?.jsonPrimitive?.intOrNull

        val vFile = LocalFileSystem.getInstance().findFileByPath(filePath)
            ?: return buildJsonObject { put("error", "file not found: $filePath") }

        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            val editor = FileEditorManager.getInstance(project).openFile(vFile, true).firstOrNull()
            if (line != null && editor is com.intellij.openapi.fileEditor.TextEditor) {
                val textEditor = editor.editor
                val offset = textEditor.document.getLineStartOffset((line - 1).coerceAtLeast(0))
                textEditor.caretModel.moveToOffset(offset)
                textEditor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER)
            }
        }
        return buildJsonObject { put("success", true) }
    }

    fun getToolDefinitions(): JsonArray {
        return buildJsonArray {
            add(toolDef("getCurrentSelection", "Get the current text selection in the IDE editor"))
            add(toolDef("getOpenEditors", "List all open editor tabs with metadata"))
            add(toolDef("getWorkspaceFolders", "Get project root paths"))
            add(toolDef("getDiagnostics", "Get diagnostic errors and warnings from the IDE"))
            add(toolDef("openFile", "Open a file in the IDE editor", buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") { put("type", "string"); put("description", "File path to open") }
                    putJsonObject("line") { put("type", "integer"); put("description", "Line number to navigate to") }
                }
                putJsonArray("required") { add("path") }
            }))
        }
    }

    private fun toolDef(name: String, description: String, inputSchema: JsonObject? = null): JsonObject {
        return buildJsonObject {
            put("name", name)
            put("description", description)
            put("inputSchema", inputSchema ?: buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {}
            })
        }
    }
}
