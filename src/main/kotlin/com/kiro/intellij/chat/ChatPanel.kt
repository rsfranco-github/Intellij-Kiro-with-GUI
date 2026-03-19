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
    private val cliProcess = KiroCliProcess(project)
    private var browser: JBCefBrowser? = null
    private var sendQuery: JBCefJSQuery? = null
    private var isSending = false

    val component: JComponent get() = mainPanel

    init {
        Disposer.register(parentDisposable, this)
        cliProcess.model = KiroSettings.getInstance().state.defaultModel
        initBrowser()
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
                override fun onLoadEnd(cefBrowser: CefBrowser?, frame: org.cef.browser.CefFrame?, httpStatusCode: Int) {
                    if (frame?.isMain == true) {
                        injectSendFunction()
                    }
                }
            }, b.cefBrowser)

            b.loadHTML(buildHtml())
            mainPanel.add(b.component, BorderLayout.CENTER)
        }
    }

    private fun injectSendFunction() {
        val js = "window._sendToKotlin = function(msg) { ${sendQuery?.inject("msg")} };"
        browser?.cefBrowser?.executeJavaScript(js, "", 0)
    }

    private fun handleUserInput(message: String) {
        if (message.isBlank() || isSending) return
        isSending = true

        val userMsg = ChatMessage(ChatMessage.Role.USER, message.trim())
        messages.add(userMsg)
        execJs("addMessage('user', '${escapeJs(userMsg.content)}')")
        execJs("setInputEnabled(false)")

        // assistant placeholder
        messages.add(ChatMessage(ChatMessage.Role.ASSISTANT, ""))
        execJs("addMessage('assistant', '')")

        cliProcess.sendMessage(message.trim()) { chunk ->
            ApplicationManager.getApplication().invokeLater {
                val last = messages.lastOrNull()
                if (last?.role == ChatMessage.Role.ASSISTANT) {
                    val updated = last.copy(content = last.content + chunk)
                    messages[messages.lastIndex] = updated
                    execJs("updateLastMessage('${escapeJs(updated.content)}')")
                }
            }
        }.thenAccept {
            ApplicationManager.getApplication().invokeLater {
                isSending = false
                execJs("setInputEnabled(true)")
            }
        }.exceptionally { e ->
            ApplicationManager.getApplication().invokeLater {
                isSending = false
                execJs("setInputEnabled(true)")
                execJs("updateLastMessage('Error: ${escapeJs(e.message ?: "unknown error")}')")
            }
            null
        }
    }

    fun sendToChat(text: String) {
        execJs("document.getElementById('input').value = '${escapeJs(text)}'")
    }

    private fun execJs(js: String) {
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
#input:disabled { opacity: 0.5; }
#send-btn {
    background: #007acc; color: #fff; border: none; border-radius: 8px;
    padding: 8px 16px; cursor: pointer; font-size: 13px; align-self: flex-end;
}
#send-btn:hover { background: #005a9e; }
#send-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.typing::after { content: '●●●'; animation: blink 1s infinite; }
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
const sendBtn = document.getElementById('send-btn');

input.addEventListener('input', function() {
    this.style.height = 'auto';
    this.style.height = Math.min(this.scrollHeight, 120) + 'px';
});

function sendMessage() {
    const text = input.value.trim();
    if (!text || input.disabled) return;
    input.value = '';
    input.style.height = 'auto';
    if (window._sendToKotlin) window._sendToKotlin(text);
}

function addMessage(role, text) {
    const div = document.createElement('div');
    div.className = 'msg ' + role;
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
        if (text) {
            last.innerHTML = typeof marked !== 'undefined' ? marked.parse(text) : text;
        } else {
            last.innerHTML = '<span class="typing"></span>';
        }
        messagesDiv.scrollTop = messagesDiv.scrollHeight;
    }
}

function setInputEnabled(enabled) {
    input.disabled = !enabled;
    sendBtn.disabled = !enabled;
    if (enabled) input.focus();
}
</script>
</body>
</html>
        """.trimIndent()
    }

    override fun dispose() {
        cliProcess.stop()
    }
}
