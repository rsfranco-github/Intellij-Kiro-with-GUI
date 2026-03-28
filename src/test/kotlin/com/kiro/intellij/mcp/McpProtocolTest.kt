package com.kiro.intellij.mcp

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class McpProtocolTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `JsonRpcRequest should parse correctly`() {
        val requestJson = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "method": "tools/list",
                "params": {}
            }
        """.trimIndent()

        val request = json.decodeFromString<JsonRpcRequest>(requestJson)
        assertEquals(JsonPrimitive(1), request.id)
        assertEquals("tools/list", request.method)
    }

    @Test
    fun `JsonRpcRequest with string id should parse correctly`() {
        val requestJson = """
            {
                "jsonrpc": "2.0",
                "id": "abc-123",
                "method": "initialize"
            }
        """.trimIndent()

        val request = json.decodeFromString<JsonRpcRequest>(requestJson)
        assertEquals(JsonPrimitive("abc-123"), request.id)
        assertEquals("initialize", request.method)
    }

    @Test
    fun `JsonRpcResponse should serialize correctly`() {
        val response = JsonRpcResponse(
            id = JsonPrimitive(1),
            result = buildJsonObject {
                put("success", true)
            }
        )

        val serialized = json.encodeToString(JsonRpcResponse.serializer(), response)
        assertTrue(serialized.contains("\"id\":1"))
        assertTrue(serialized.contains("\"success\":true"))
    }

    @Test
    fun `JsonRpcError should serialize correctly`() {
        val response = JsonRpcResponse(
            id = JsonPrimitive(1),
            error = JsonRpcError(-32601, "Method not found")
        )

        val serialized = json.encodeToString(JsonRpcResponse.serializer(), response)
        assertTrue(serialized.contains("-32601"))
        assertTrue(serialized.contains("Method not found"))
    }

    @Test
    fun `tool call params should parse correctly`() {
        val paramsJson = """
            {
                "name": "getCurrentSelection",
                "arguments": {}
            }
        """.trimIndent()

        val params = json.parseToJsonElement(paramsJson).jsonObject
        assertEquals("getCurrentSelection", params["name"]?.jsonPrimitive?.content)
    }
}
