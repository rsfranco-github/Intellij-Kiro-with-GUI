package com.kiro.intellij.mcp

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.kiro.intellij.settings.KiroCliResolver
import kotlinx.serialization.json.*
import java.io.*
import java.net.ServerSocket
import java.net.Socket

/**
 * IDE 내부에서 TCP 서버를 열고, 별도의 MCP bridge 스크립트가
 * stdin/stdout(MCP stdio) ↔ TCP(IDE) 를 중계한다.
 */
class McpServer(private val project: Project, parentDisposable: Disposable) : Disposable {

    private val log = Logger.getInstance(McpServer::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val toolHandler = McpToolHandler(project)

    private var serverSocket: ServerSocket? = null
    private var bridgeProcess: Process? = null
    private var running = false

    val port: Int get() = serverSocket?.localPort ?: 0

    init {
        Disposer.register(parentDisposable, this)
    }

    fun start() {
        serverSocket = ServerSocket(0) // 랜덤 포트
        running = true
        log.info("MCP IDE server started on port $port")

        // TCP 클라이언트(bridge) 연결 대기 스레드
        Thread({
            while (running) {
                try {
                    val client = serverSocket?.accept() ?: break
                    handleClient(client)
                } catch (e: Exception) {
                    if (running) log.warn("MCP server accept error", e)
                }
            }
        }, "kiro-mcp-server").apply { isDaemon = true }.start()
    }

    fun startBridge() {
        // bridge 스크립트 추출
        val bridgeScript = extractBridgeScript()
        val nodePath = KiroCliResolver.resolveCommand("node")
        val pb = ProcessBuilder(nodePath, bridgeScript.absolutePath, port.toString())
            .redirectErrorStream(true)
        KiroCliResolver.configureProcessBuilder(pb)
        bridgeProcess = pb.start()
        log.info("MCP bridge started, connecting to port $port")
    }

    fun registerWithKiro() {
        val bridgeScript = extractBridgeScript()
        val nodePath = KiroCliResolver.resolveCommand("node")
        val command = "$nodePath ${bridgeScript.absolutePath} $port"
        try {
            val cliPath = KiroCliResolver.resolveCommand("kiro-cli")
            val pb = ProcessBuilder(cliPath, "mcp", "add",
                "--name", "kiro-ide",
                "--command", command,
                "--scope", "workspace",
                "--force")
                .directory(project.basePath?.let { File(it) })
                .redirectErrorStream(true)
            KiroCliResolver.configureProcessBuilder(pb)
            val proc = pb.start()
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            log.info("Registered MCP server with kiro-cli: $output")
        } catch (e: Exception) {
            log.warn("Failed to register MCP server with kiro-cli", e)
        }
    }

    fun unregisterFromKiro() {
        try {
            val cliPath = KiroCliResolver.resolveCommand("kiro-cli")
            val pb = ProcessBuilder(cliPath, "mcp", "remove",
                "--name", "kiro-ide",
                "--scope", "workspace")
                .directory(project.basePath?.let { File(it) })
                .redirectErrorStream(true)
            KiroCliResolver.configureProcessBuilder(pb)
            val proc = pb.start()
            proc.waitFor()
        } catch (e: Exception) {
            log.warn("Failed to unregister MCP server", e)
        }
    }

    private fun handleClient(client: Socket) {
        Thread({
            try {
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                val writer = BufferedWriter(OutputStreamWriter(client.getOutputStream()))

                while (running && !client.isClosed) {
                    val line = reader.readLine() ?: break
                    val response = processMessage(line)
                    if (response != null) {
                        writer.write(response)
                        writer.newLine()
                        writer.flush()
                    }
                }
            } catch (e: Exception) {
                if (running) log.warn("MCP client handler error", e)
            } finally {
                client.close()
            }
        }, "kiro-mcp-client").apply { isDaemon = true }.start()
    }

    private fun processMessage(line: String): String? {
        return try {
            val request = json.decodeFromString<JsonRpcRequest>(line)
            val response = handleRequest(request)
            json.encodeToString(JsonRpcResponse.serializer(), response)
        } catch (e: Exception) {
            log.warn("Failed to process MCP message: $line", e)
            null
        }
    }

    private fun handleRequest(request: JsonRpcRequest): JsonRpcResponse {
        return when (request.method) {
            "initialize" -> JsonRpcResponse(
                id = request.id,
                result = buildJsonObject {
                    putJsonObject("capabilities") {
                        putJsonObject("tools") {}
                    }
                    putJsonObject("serverInfo") {
                        put("name", "kiro-ide")
                        put("version", "0.1.0")
                    }
                }
            )
            "tools/list" -> JsonRpcResponse(
                id = request.id,
                result = buildJsonObject {
                    put("tools", toolHandler.getToolDefinitions())
                }
            )
            "tools/call" -> {
                val params = request.params?.jsonObject
                val toolName = params?.get("name")?.jsonPrimitive?.content ?: ""
                val arguments = params?.get("arguments")

                // IDE API는 EDT에서 실행해야 함
                var toolResult: JsonElement = JsonNull
                ApplicationManager.getApplication().invokeAndWait {
                    toolResult = toolHandler.handleToolCall(toolName, arguments)
                }

                JsonRpcResponse(
                    id = request.id,
                    result = buildJsonObject {
                        putJsonArray("content") {
                            add(buildJsonObject {
                                put("type", "text")
                                put("text", toolResult.toString())
                            })
                        }
                    }
                )
            }
            "notifications/initialized", "initialized" -> null.let {
                // notification, no response needed
                JsonRpcResponse(id = request.id, result = buildJsonObject {})
            }
            else -> JsonRpcResponse(
                id = request.id,
                error = JsonRpcError(-32601, "Method not found: ${request.method}")
            )
        }
    }

    private fun extractBridgeScript(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "kiro-ide-mcp")
        dir.mkdirs()
        val scriptFile = File(dir, "bridge.js")
        if (!scriptFile.exists()) {
            val content = javaClass.getResourceAsStream("/mcp/bridge.js")?.bufferedReader()?.readText()
                ?: throw IllegalStateException("bridge.js not found in resources")
            scriptFile.writeText(content)
        }
        return scriptFile
    }

    override fun dispose() {
        running = false
        unregisterFromKiro()
        bridgeProcess?.destroyForcibly()
        serverSocket?.close()
    }
}
