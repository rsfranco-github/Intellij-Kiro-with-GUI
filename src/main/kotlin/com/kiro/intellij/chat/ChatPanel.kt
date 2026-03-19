package com.kiro.intellij.chat

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import com.kiro.intellij.settings.KiroSettings
import java.awt.BorderLayout
import java.util.UUID
import javax.swing.JComponent
import javax.swing.JPanel

class ChatPanel(
    private val project: Project,
    private val parentDisposable: Disposable,
    private val backendServer: ChatBackendServer,
    val tabName: String
) : Disposable {

    private val mainPanel = JPanel(BorderLayout())
    private var browser: JBCefBrowser? = null
    val sessionId = UUID.randomUUID().toString()
    private val session = ChatSession(sessionId, project)

    val component: JComponent get() = mainPanel

    init {
        Disposer.register(parentDisposable, this)
        session.model = KiroSettings.getInstance().state.defaultModel
        backendServer.registerSession(sessionId, session)
        initBrowser()
    }

    private fun initBrowser() {
        browser = JBCefBrowser().also { b ->
            Disposer.register(this, b)
            b.loadHTML(buildHtml(backendServer.port, sessionId))
            mainPanel.add(b.component, BorderLayout.CENTER)
        }
    }

    fun sendToChat(text: String) {
        val escaped = text.replace("\\", "\\\\").replace("`", "\\`").replace("\n", "\\n")
        browser?.cefBrowser?.executeJavaScript("setInput(`$escaped`)", "", 0)
    }

    fun changeModel(model: String) {
        session.model = model
    }

    override fun dispose() {
        backendServer.removeSession(sessionId)
    }

    private fun buildHtml(port: Int, sessionId: String): String = """
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
    max-width: 85%; padding: 10px 14px; border-radius: 12px;
    font-size: 13px; line-height: 1.6; word-wrap: break-word;
}
.msg pre {
    background: #0d1117; padding: 10px; border-radius: 6px;
    overflow-x: auto; margin: 6px 0; border: 1px solid #30363d;
}
.msg code {
    font-family: 'JetBrains Mono', 'Fira Code', monospace; font-size: 12px;
}
.msg :not(pre) > code {
    background: #343942; padding: 2px 5px; border-radius: 3px;
}
.msg p { margin: 4px 0; }
.msg ul, .msg ol { padding-left: 20px; margin: 4px 0; }
.user {
    align-self: flex-end; background: #264f78; color: #e8e8e8;
    border-bottom-right-radius: 4px;
}
.assistant {
    align-self: flex-start; background: #2d2d2d; color: #d4d4d4;
    border-bottom-left-radius: 4px;
}
.assistant.streaming { border-left: 2px solid #007acc; }
#input-area {
    padding: 10px 12px; background: #252526; border-top: 1px solid #3c3c3c;
    display: flex; gap: 8px; align-items: flex-end;
}
#input {
    flex: 1; background: #1e1e1e; color: #d4d4d4; border: 1px solid #3c3c3c;
    border-radius: 8px; padding: 10px 12px; font-size: 13px; resize: none;
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
    min-height: 40px; max-height: 120px; line-height: 1.4;
}
#input:focus { outline: none; border-color: #007acc; }
#input:disabled { opacity: 0.4; }
#send-btn {
    background: #007acc; color: #fff; border: none; border-radius: 8px;
    padding: 10px 18px; cursor: pointer; font-size: 13px; font-weight: 500;
    white-space: nowrap;
}
#send-btn:hover { background: #005a9e; }
#send-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.typing-indicator { display: inline-flex; gap: 4px; padding: 4px 0; }
.typing-indicator span {
    width: 6px; height: 6px; background: #888; border-radius: 50%;
    animation: bounce 1.4s infinite ease-in-out;
}
.typing-indicator span:nth-child(1) { animation-delay: 0s; }
.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }
@keyframes bounce {
    0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
    40% { transform: scale(1); opacity: 1; }
}
</style>
</head>
<body>
<div id="messages"></div>
<div id="input-area">
    <textarea id="input" rows="1" placeholder="메시지를 입력하세요... (Shift+Enter: 줄바꿈)"
        onkeydown="handleKey(event)"></textarea>
    <button id="send-btn" onclick="sendMessage()">전송</button>
</div>
<script>
const API = 'http://127.0.0.1:$port';
const SESSION = '$sessionId';
const messagesDiv = document.getElementById('messages');
const input = document.getElementById('input');
const sendBtn = document.getElementById('send-btn');
let isStreaming = false;
let currentAssistantDiv = null;
let currentContent = '';

// auto-resize
input.addEventListener('input', () => {
    input.style.height = 'auto';
    input.style.height = Math.min(input.scrollHeight, 120) + 'px';
});

function handleKey(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendMessage();
    }
}

function setInput(text) {
    input.value = text;
    input.dispatchEvent(new Event('input'));
    input.focus();
}

function sendMessage() {
    const text = input.value.trim();
    if (!text || isStreaming) return;

    addUserMessage(text);
    input.value = '';
    input.style.height = 'auto';
    setEnabled(false);

    // POST 메시지
    fetch(API + '/api/send', {
        method: 'POST',
        body: SESSION + '\n' + text
    }).catch(err => {
        addErrorMessage('전송 실패: ' + err.message);
        setEnabled(true);
    });
}

function addUserMessage(text) {
    const div = document.createElement('div');
    div.className = 'msg user';
    div.textContent = text;
    messagesDiv.appendChild(div);
    scrollToBottom();
}

function startAssistantMessage() {
    currentContent = '';
    currentAssistantDiv = document.createElement('div');
    currentAssistantDiv.className = 'msg assistant streaming';
    currentAssistantDiv.innerHTML = '<div class="typing-indicator"><span></span><span></span><span></span></div>';
    messagesDiv.appendChild(currentAssistantDiv);
    scrollToBottom();
}

function appendChunk(chunk) {
    currentContent += chunk;
    if (currentAssistantDiv) {
        try {
            currentAssistantDiv.innerHTML = typeof marked !== 'undefined'
                ? marked.parse(currentContent) : escapeHtml(currentContent);
        } catch(e) {
            currentAssistantDiv.textContent = currentContent;
        }
        scrollToBottom();
    }
}

function finishAssistantMessage() {
    if (currentAssistantDiv) {
        currentAssistantDiv.classList.remove('streaming');
    }
    currentAssistantDiv = null;
    setEnabled(true);
}

function addErrorMessage(text) {
    if (currentAssistantDiv) {
        currentAssistantDiv.innerHTML = '<span style="color:#f44">⚠ ' + escapeHtml(text) + '</span>';
        currentAssistantDiv.classList.remove('streaming');
        currentAssistantDiv = null;
    }
}

function setEnabled(enabled) {
    isStreaming = !enabled;
    input.disabled = !enabled;
    sendBtn.disabled = !enabled;
    if (enabled) input.focus();
}

function scrollToBottom() {
    messagesDiv.scrollTop = messagesDiv.scrollHeight;
}

function escapeHtml(s) {
    return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}

// SSE 연결
function connectSSE() {
    const es = new EventSource(API + '/api/stream?session=' + SESSION);

    es.addEventListener('start', () => {
        startAssistantMessage();
    });

    es.addEventListener('chunk', (e) => {
        appendChunk(e.data);
    });

    es.addEventListener('done', () => {
        finishAssistantMessage();
    });

    es.addEventListener('error', (e) => {
        if (e.data) addErrorMessage(e.data);
        finishAssistantMessage();
    });

    es.addEventListener('ping', () => {});

    es.onerror = () => {
        // 재연결 시도
        setTimeout(connectSSE, 2000);
    };
}

// marked 설정
if (typeof marked !== 'undefined') {
    marked.setOptions({ breaks: true, gfm: true });
}

connectSSE();
input.focus();
</script>
</body>
</html>
    """.trimIndent()
}
