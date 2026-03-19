package com.kiro.intellij.chat

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import com.kiro.intellij.settings.KiroSettings
import org.cef.browser.CefBrowser
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class ChatPanel(
    private val project: Project,
    private val parentDisposable: Disposable,
    private val tabName: String
) : Disposable {

    private val mainPanel = JPanel(BorderLayout())
    private val messages = mutableListOf<ChatMessage>()
    private var cliProcess: KiroCliProcess? = null
    private var browser: JBCefBrowser? = null
    private var sendQuery: JBCefJSQuery? = null
    private var isLoaded = false

    val component: JComponent get() = mainPanel

    init {
        Disposer.register(parentDisposable, this)
        initBrowser()
        startProcess()
    }

    private fun initBrowser() {
        browser = JBCefBrowser().also { b ->
            Disposer.register(this, b)

            sendQuery = JBCefJSQuery.create(b).also { q ->
                Disposer.register(this, q)
                q.addHandler { message ->
                    handleUserInput(message)
                    null
                }
            }

            b.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
                override fun onLoadEnd(browser: CefBrowser?, frame: org.cef.browser.CefFrame?, httpStatusCode: Int) {
                    if (frame?.isMain == true) {
                        isLoaded = true
                        injectSendFunction()
                    }
                }
            }, b.cefBrowser)

            b.loadHTML(buildHtml())
            mainPanel.add(b.component, BorderLayout.CENTER)
        }
    }

    private fun injectSendFunction() {
        val js = """
            window._sendToKotlin = function(msg) {
                ${sendQuery?.inject("msg")}
            };
        """.trimIndent()
        browser?.cefBrowser?.executeJavaScript(js, "", 0)
    }

    private fun startProcess() {
        val settings = KiroSettings.getInstance().state
        cliProcess = KiroCliProcess(project).apply {
            start(settings.defaultModel) { chunk ->
                ApplicationManager.getApplication().invokeLater {
                    appendAssistantChunk(chunk)
                }
            }
        }
    }

    private fun handleUserInput(message: String) {
        if (message.isBlank()) return
        val msg = ChatMessage(ChatMessage.Role.USER, message.trim())
        messages.add(msg)
        renderMessage(msg)
        cliProcess?.send(message.trim())
        // 새 assistant 메시지 시작
        messages.add(ChatMessage(ChatMessage.Role.ASSISTANT, ""))
        renderMessage(messages.last())
    }

    private fun appendAssistantChunk(chunk: String) {
        val last = messages.lastOrNull()
        if (last?.role == ChatMessage.Role.ASSISTANT) {
            val updated = last.copy(content = last.content + chunk)
            messages[messages.lastIndex] = updated
            updateLastMessage(updated.content)
        } else {
            val msg = ChatMessage(ChatMessage.Role.ASSISTANT, chunk)
            messages.add(msg)
            renderMessage(msg)
        }
    }

    private fun renderMessage(msg: ChatMessage) {
        val escaped = escapeJs(msg.content)
        val role = msg.role.name.lowercase()
        val js = "addMessage('$role', '$escaped');"
        browser?.cefBrowser?.executeJavaScript(js, "", 0)
    }

    private fun updateLastMessage(content: String) {
        val escaped = escapeJs(content)
        val js = "updateLastMessage('$escaped');"
        browser?.cefBrowser?.executeJavaScript(js, "", 0)
    }

    fun sendToChat(text: String) {
        val js = "document.getElementById('input').value = ${escapeJs(text).let { "'$it'" }}; sendMessage();"
        browser?.cefBrowser?.executeJavaScript(js, "", 0)
    }

    private fun escapeJs(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("\t", "\\t")
    }

    private fun buildHtml(): String {
        return """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
    background: #1e1e1e; color: #d4d4d4;
    display: flex; flex-direction: column; height: 100vh;
}
#messages {
    flex: 1; overflow-y: auto; padding: 12px;
    display: flex; flex-direction: column; gap: 8px;
}
.msg {
    max-width: 80%; padding: 10px 14px; border-radius: 12px;
    font-size: 13px; line-height: 1.5; word-wrap: break-word;
}
.msg pre { background: #2d2d2d; padding: 8px; border-radius: 6px; overflow-x: auto; margin: 6px 0; }
.msg code { font-family: 'JetBrains Mono', monospace; font-size: 12px; }
.msg p { margin: 4px 0; }
.user {
    align-self: flex-end; background: #264f78; color: #fff; border-bottom-right-radius: 4px;
}
.assistant {
    align-self: flex-start; background: #2d2d2d; color: #d4d4d4; border-bottom-left-radius: 4px;
}
#input-area {
    padding: 8px 12px; background: #252526; border-top: 1px solid #3c3c3c;
    display: flex; gap: 8px;
}
#input {
    flex: 1; background: #1e1e1e; color: #d4d4d4; border: 1px solid #3c3c3c;
    border-radius: 8px; padding: 8px 12px; font-size: 13px; resize: none;
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
    min-height: 36px; max-height: 120px;
}
#input:focus { outline: none; border-color: #007acc; }
#send-btn {
    background: #007acc; color: #fff; border: none; border-radius: 8px;
    padding: 8px 16px; cursor: pointer; font-size: 13px; align-self: flex-end;
}
#send-btn:hover { background: #005a9e; }
.typing::after {
    content: '●●●'; animation: blink 1s infinite;
}
@keyframes blink { 50% { opacity: 0.3; } }
</style>
</head>
<body>
<div id="messages"></div>
<div id="input-area">
    <textarea id="input" rows="1" placeholder="메시지를 입력하세요..." 
        onkeydown="if(event.key==='Enter'&&!event.shiftKey){event.preventDefault();sendMessage();}"></textarea>
    <button id="send-btn" onclick="sendMessage()">전송</button>
</div>
<script>
const messagesDiv = document.getElementById('messages');
const input = document.getElementById('input');

// auto-resize textarea
input.addEventListener('input', function() {
    this.style.height = 'auto';
    this.style.height = Math.min(this.scrollHeight, 120) + 'px';
});

function sendMessage() {
    const text = input.value.trim();
    if (!text) return;
    input.value = '';
    input.style.height = 'auto';
    if (window._sendToKotlin) window._sendToKotlin(text);
}

function addMessage(role, text) {
    const div = document.createElement('div');
    div.className = 'msg ' + role;
    div.id = 'msg-' + messagesDiv.children.length;
    if (role === 'assistant' && !text) {
        div.innerHTML = '<span class="typing"></span>';
    } else if (role === 'assistant') {
        div.innerHTML = typeof marked !== 'undefined' ? marked.parse(text) : text;
    } else {
        div.textContent = text;
    }
    messagesDiv.appendChild(div);
    messagesDiv.scrollTop = messagesDiv.scrollHeight;
}

function updateLastMessage(text) {
    const last = messagesDiv.lastElementChild;
    if (last && last.classList.contains('assistant')) {
        last.innerHTML = typeof marked !== 'undefined' ? marked.parse(text) : text;
        messagesDiv.scrollTop = messagesDiv.scrollHeight;
    }
}
</script>
</body>
</html>
        """.trimIndent()
    }

    override fun dispose() {
        cliProcess?.stop()
        cliProcess = null
    }
}
