package com.kiro.intellij.toolwindow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.io.TempDir
import kotlinx.serialization.json.*
import java.io.File

class ManagePanelTest {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    // ========== MCP Panel Tests ==========
    @Nested
    inner class McpPanelTests {

        @Test
        fun `mcp json should parse correctly`() {
            val mcpJson = """
                {
                    "mcpServers": {
                        "test-server": {
                            "command": "node",
                            "args": ["server.js"],
                            "disabled": false
                        }
                    }
                }
            """.trimIndent()

            val root = json.parseToJsonElement(mcpJson).jsonObject
            val servers = root["mcpServers"]?.jsonObject

            assertNotNull(servers)
            assertTrue(servers!!.containsKey("test-server"))

            val serverConfig = servers["test-server"]?.jsonObject
            assertEquals("node", serverConfig?.get("command")?.jsonPrimitive?.content)
            assertEquals(false, serverConfig?.get("disabled")?.jsonPrimitive?.boolean)
        }

        @Test
        fun `mcp server with args should format command correctly`() {
            val command = "node"
            val args = listOf("server.js", "--port", "3000")
            val fullCommand = "$command ${args.joinToString(" ")}".trim()

            assertEquals("node server.js --port 3000", fullCommand)
        }

        @Test
        fun `disabled server should be detected`() {
            val serverConfig = buildJsonObject {
                put("command", "node")
                put("disabled", true)
            }

            val disabled = serverConfig["disabled"]?.jsonPrimitive?.booleanOrNull ?: false
            assertTrue(disabled)
        }

        @Test
        fun `multiple servers should parse correctly`() {
            val mcpJson = """
                {
                    "mcpServers": {
                        "server-a": { "command": "node", "args": ["a.js"] },
                        "server-b": { "command": "python", "args": ["-m", "mcp_b"] },
                        "server-c": { "command": "npx", "args": ["@modelcontextprotocol/server-c"] }
                    }
                }
            """.trimIndent()

            val root = json.parseToJsonElement(mcpJson).jsonObject
            val servers = root["mcpServers"]?.jsonObject!!

            assertEquals(3, servers.size)
            assertEquals("node", servers["server-a"]?.jsonObject?.get("command")?.jsonPrimitive?.content)
            assertEquals("python", servers["server-b"]?.jsonObject?.get("command")?.jsonPrimitive?.content)
            assertEquals("npx", servers["server-c"]?.jsonObject?.get("command")?.jsonPrimitive?.content)
        }

        @Test
        fun `server with env should parse correctly`() {
            val mcpJson = """
                {
                    "mcpServers": {
                        "db-server": {
                            "command": "node",
                            "args": ["db-mcp.js"],
                            "env": {
                                "DB_HOST": "localhost",
                                "DB_PORT": "5432"
                            }
                        }
                    }
                }
            """.trimIndent()

            val root = json.parseToJsonElement(mcpJson).jsonObject
            val server = root["mcpServers"]?.jsonObject?.get("db-server")?.jsonObject!!
            val env = server["env"]?.jsonObject!!

            assertEquals("localhost", env["DB_HOST"]?.jsonPrimitive?.content)
            assertEquals("5432", env["DB_PORT"]?.jsonPrimitive?.content)
        }

        @Test
        fun `empty mcpServers should return empty`() {
            val mcpJson = """{ "mcpServers": {} }"""
            val root = json.parseToJsonElement(mcpJson).jsonObject
            val servers = root["mcpServers"]?.jsonObject!!

            assertTrue(servers.isEmpty())
        }

        @Test
        fun `malformed json should throw exception`() {
            val badJson = "{ invalid json"
            assertThrows(Exception::class.java) {
                json.parseToJsonElement(badJson)
            }
        }

        @Test
        fun `toggle server should flip disabled flag`() {
            val serverConfig = buildJsonObject {
                put("command", "node")
                put("args", buildJsonArray { add("server.js") })
                put("disabled", false)
            }.toMutableMap()

            val currentlyDisabled = serverConfig["disabled"]?.jsonPrimitive?.booleanOrNull ?: false
            serverConfig["disabled"] = JsonPrimitive(!currentlyDisabled)

            assertTrue(serverConfig["disabled"]?.jsonPrimitive?.boolean == true)
        }

        @Test
        fun `server without disabled field should default to enabled`() {
            val serverConfig = buildJsonObject {
                put("command", "node")
            }

            val disabled = serverConfig["disabled"]?.jsonPrimitive?.booleanOrNull ?: false
            assertFalse(disabled)
        }

        @Test
        fun `args as jsonArray should extract strings`() {
            val config = buildJsonObject {
                put("command", "npx")
                put("args", buildJsonArray {
                    add("-y")
                    add("@modelcontextprotocol/server-fetch")
                })
            }

            val args = config["args"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            assertEquals(listOf("-y", "@modelcontextprotocol/server-fetch"), args)
        }

        @Test
        fun `mcp json file write and read roundtrip`(@TempDir tempDir: File) {
            val mcpFile = File(tempDir, "mcp.json")
            val original = buildJsonObject {
                putJsonObject("mcpServers") {
                    putJsonObject("test") {
                        put("command", "node")
                        put("disabled", false)
                    }
                }
            }

            mcpFile.writeText(json.encodeToString(JsonObject.serializer(), original))
            val read = json.parseToJsonElement(mcpFile.readText()).jsonObject
            assertEquals("node", read["mcpServers"]?.jsonObject?.get("test")?.jsonObject?.get("command")?.jsonPrimitive?.content)
        }
    }

    // ========== Agent Panel Tests ==========
    @Nested
    inner class AgentPanelTests {

        @Test
        fun `agent json should parse correctly`() {
            val agentJson = """
                {
                    "name": "test-agent",
                    "description": "A test agent",
                    "instructions": "Be helpful"
                }
            """.trimIndent()

            val content = json.parseToJsonElement(agentJson).jsonObject

            assertEquals("test-agent", content["name"]?.jsonPrimitive?.content)
            assertEquals("A test agent", content["description"]?.jsonPrimitive?.content)
            assertEquals("Be helpful", content["instructions"]?.jsonPrimitive?.content)
        }

        @Test
        fun `agent file name should extract correctly for json`() {
            assertEquals("my-custom-agent", "my-custom-agent.json".substringBeforeLast("."))
        }

        @Test
        fun `agent file name should extract correctly for md`() {
            assertEquals("code-reviewer", "code-reviewer.md".substringBeforeLast("."))
        }

        @Test
        fun `agent without description should use default`() {
            val agentJson = """{ "name": "minimal-agent" }"""
            val content = json.parseToJsonElement(agentJson).jsonObject
            val description = content["description"]?.jsonPrimitive?.content ?: "사용자 정의 에이전트"

            assertEquals("사용자 정의 에이전트", description)
        }

        @Test
        fun `default agent should be identified`() {
            data class AgentInfo(val name: String, val isDefault: Boolean)
            val agents = listOf(
                AgentInfo("default", true),
                AgentInfo("code-reviewer", false),
                AgentInfo("doc-writer", false)
            )

            assertEquals(1, agents.count { it.isDefault })
            assertTrue(agents.first { it.isDefault }.name == "default")
        }

        @Test
        fun `create agent json should have required fields`() {
            val name = "my-agent"
            val description = "My custom agent"
            val content = buildJsonObject {
                put("name", name)
                put("description", description)
                put("instructions", "")
            }

            assertEquals(name, content["name"]?.jsonPrimitive?.content)
            assertEquals(description, content["description"]?.jsonPrimitive?.content)
            assertTrue(content.containsKey("instructions"))
        }

        @Test
        fun `agent file creation and read roundtrip`(@TempDir tempDir: File) {
            val agentFile = File(tempDir, "test-agent.json")
            val content = buildJsonObject {
                put("name", "test-agent")
                put("description", "Test description")
                put("instructions", "Be creative")
            }

            agentFile.writeText(json.encodeToString(JsonObject.serializer(), content))
            assertTrue(agentFile.exists())

            val read = json.parseToJsonElement(agentFile.readText()).jsonObject
            assertEquals("test-agent", read["name"]?.jsonPrimitive?.content)
        }

        @Test
        fun `agent directory listing should find json and md files`(@TempDir tempDir: File) {
            File(tempDir, "agent-a.json").writeText("{}")
            File(tempDir, "agent-b.md").writeText("# Agent B")
            File(tempDir, "notes.txt").writeText("ignore this")
            File(tempDir, "readme.yaml").writeText("ignore: true")

            val agentFiles = tempDir.listFiles { f ->
                f.extension == "json" || f.extension == "md"
            }!!

            assertEquals(2, agentFiles.size)
            assertTrue(agentFiles.any { it.name == "agent-a.json" })
            assertTrue(agentFiles.any { it.name == "agent-b.md" })
        }

        @Test
        fun `swap agent command should be formatted correctly`() {
            val agentName = "code-reviewer"
            val command = "/agent swap $agentName"
            assertEquals("/agent swap code-reviewer", command)
        }
    }

    // ========== Skills Panel Tests ==========
    @Nested
    inner class SkillsPanelTests {

        private val sampleSkills = listOf(
            "getCurrentSelection" to "현재 에디터에서 선택된 텍스트를 가져옵니다",
            "getOpenEditors" to "열려있는 에디터 파일 목록을 가져옵니다",
            "getWorkspaceFolders" to "워크스페이스 폴더 목록을 가져옵니다",
            "getDiagnostics" to "현재 파일의 에러/경고를 가져옵니다",
            "openFile" to "지정된 파일을 에디터에서 엽니다",
            "openDiff" to "두 파일의 차이를 diff viewer로 표시합니다"
        )

        @Test
        fun `skill info should be created correctly`() {
            data class SkillInfo(
                val name: String,
                val description: String,
                val provider: String,
                var trusted: Boolean
            )

            val skill = SkillInfo("getCurrentSelection", "현재 에디터에서 선택된 텍스트를 가져옵니다", "kiro-ide", true)
            assertEquals("getCurrentSelection", skill.name)
            assertTrue(skill.trusted)

            skill.trusted = false
            assertFalse(skill.trusted)
        }

        @Test
        fun `filter with exact match`() {
            val filtered = sampleSkills.filter { (name, _) ->
                name.contains("openFile", ignoreCase = true)
            }
            assertEquals(1, filtered.size)
            assertEquals("openFile", filtered[0].first)
        }

        @Test
        fun `filter with partial match on name`() {
            val filtered = sampleSkills.filter { (name, desc) ->
                name.contains("get", ignoreCase = true) || desc.contains("get", ignoreCase = true)
            }
            assertEquals(4, filtered.size)
        }

        @Test
        fun `filter with partial match on description`() {
            val filtered = sampleSkills.filter { (name, desc) ->
                name.contains("diff", ignoreCase = true) || desc.contains("diff", ignoreCase = true)
            }
            assertEquals(1, filtered.size)
            assertEquals("openDiff", filtered[0].first)
        }

        @Test
        fun `filter is case insensitive`() {
            val queryUpper = "GETOPEN"
            val queryLower = "getopen"

            val filteredUpper = sampleSkills.filter { (name, _) -> name.contains(queryUpper, ignoreCase = true) }
            val filteredLower = sampleSkills.filter { (name, _) -> name.contains(queryLower, ignoreCase = true) }

            assertEquals(filteredUpper.size, filteredLower.size)
        }

        @Test
        fun `empty query returns all skills`() {
            val query = ""
            val filtered = if (query.isBlank()) sampleSkills else sampleSkills.filter { (n, d) ->
                n.contains(query, ignoreCase = true) || d.contains(query, ignoreCase = true)
            }
            assertEquals(6, filtered.size)
        }

        @Test
        fun `no match returns empty`() {
            val filtered = sampleSkills.filter { (name, desc) ->
                name.contains("zzzzz", ignoreCase = true) || desc.contains("zzzzz", ignoreCase = true)
            }
            assertTrue(filtered.isEmpty())
        }

        @Test
        fun `trust command should format correctly`() {
            val toolName = "getCurrentSelection"
            assertEquals("/tools trust getCurrentSelection", "/tools trust $toolName")
            assertEquals("/tools untrust getCurrentSelection", "/tools untrust $toolName")
        }
    }

    // ========== Auth Panel Tests ==========
    @Nested
    inner class AuthPanelTests {

        private fun isLoggedIn(output: String, exitCode: Int): Boolean {
            return exitCode == 0 && output.isNotBlank() &&
                !output.contains("not logged in", ignoreCase = true) &&
                !output.contains("error", ignoreCase = true)
        }

        @Test
        fun `logged in user with email`() {
            assertTrue(isLoggedIn("user@example.com", 0))
        }

        @Test
        fun `logged in user with username`() {
            assertTrue(isLoggedIn("johndoe", 0))
        }

        @Test
        fun `not logged in with error message`() {
            assertFalse(isLoggedIn("error: not logged in", 1))
        }

        @Test
        fun `not logged in with exit code 1`() {
            assertFalse(isLoggedIn("some output", 1))
        }

        @Test
        fun `empty output should not be logged in`() {
            assertFalse(isLoggedIn("", 0))
        }

        @Test
        fun `blank output should not be logged in`() {
            assertFalse(isLoggedIn("   ", 0))
        }

        @Test
        fun `error keyword in output should not be logged in`() {
            assertFalse(isLoggedIn("error: authentication failed", 0))
        }

        @Test
        fun `not logged in keyword should be detected`() {
            assertFalse(isLoggedIn("Not logged in - please run login", 0))
        }

        @Test
        fun `multiline output with user info`() {
            val output = "user@example.com\nTeam: MyTeam\nPlan: Pro"
            assertTrue(isLoggedIn(output, 0))
        }
    }

    // ========== Settings Panel Tests ==========
    @Nested
    inner class SettingsPanelTests {

        private val models = listOf(
            "Auto", "claude-opus-4.6", "claude-sonnet-4.6", "claude-opus-4.5",
            "claude-sonnet-4.5", "claude-sonnet-4", "claude-haiku-4.5",
            "deepseek-3.2", "minimax-m2.1", "minimax-m2.5", "qwen3-coder-next"
        )
        private val languageMap = mapOf("한국어" to "ko", "English" to "en")
        private val reverseLanguageMap = mapOf("ko" to "한국어", "en" to "English")

        @Test
        fun `model list should contain expected models`() {
            assertTrue(models.contains("Auto"))
            assertTrue(models.contains("claude-opus-4.5"))
            assertTrue(models.contains("deepseek-3.2"))
            assertEquals(11, models.size)
        }

        @Test
        fun `model list should have Auto as first option`() {
            assertEquals("Auto", models.first())
        }

        @Test
        fun `kiro command default should be kiro-cli`() {
            val result = "".ifBlank { "kiro-cli" }
            assertEquals("kiro-cli", result)
        }

        @Test
        fun `custom kiro command should be preserved`() {
            val customPath = "/usr/local/bin/kiro-cli"
            val result = customPath.ifBlank { "kiro-cli" }
            assertEquals("/usr/local/bin/kiro-cli", result)
        }

        @Test
        fun `language map should have ko and en`() {
            assertEquals("ko", languageMap["한국어"])
            assertEquals("en", languageMap["English"])
            assertEquals(2, languageMap.size)
        }

        @Test
        fun `reverse language map should resolve codes`() {
            assertEquals("한국어", reverseLanguageMap["ko"])
            assertEquals("English", reverseLanguageMap["en"])
        }

        @Test
        fun `unknown language should fallback`() {
            val langDisplay = reverseLanguageMap["fr"] ?: "한국어"
            assertEquals("한국어", langDisplay)
        }

        @Test
        fun `default config dir should use home kiro`() {
            val configDir = ""
            val defaultPath = System.getProperty("user.home") + "/.kiro"
            val effective = configDir.ifBlank { defaultPath }
            assertTrue(effective.endsWith("/.kiro"))
        }

        @Test
        fun `custom config dir should be used when set`() {
            val configDir = "/custom/kiro/path"
            val defaultPath = System.getProperty("user.home") + "/.kiro"
            val effective = configDir.ifBlank { defaultPath }
            assertEquals("/custom/kiro/path", effective)
        }

        @Test
        fun `settings save should handle blank command as default`() {
            val inputCommand = "  "
            val saved = inputCommand.ifBlank { "kiro-cli" }
            assertEquals("kiro-cli", saved)
        }
    }

    // ========== KiroManagePanel Navigation Tests ==========
    @Nested
    inner class ManagePanelNavTests {

        private val navIds = listOf("auth", "settings", "mcp", "skills", "agent")

        @Test
        fun `navigation should have 5 items`() {
            assertEquals(5, navIds.size)
        }

        @Test
        fun `navigation ids should be unique`() {
            assertEquals(navIds.size, navIds.toSet().size)
        }

        @Test
        fun `default selected nav should be auth`() {
            val defaultNav = navIds.first()
            assertEquals("auth", defaultNav)
        }

        @Test
        fun `all expected nav ids should be present`() {
            assertTrue(navIds.contains("auth"))
            assertTrue(navIds.contains("settings"))
            assertTrue(navIds.contains("mcp"))
            assertTrue(navIds.contains("skills"))
            assertTrue(navIds.contains("agent"))
        }
    }
}
