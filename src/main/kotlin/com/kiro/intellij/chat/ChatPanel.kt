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
            val html = buildHtml(backendServer.port, sessionId)
            backendServer.setSessionHtml(sessionId, html)
            b.loadURL("http://127.0.0.1:${backendServer.port}/ui?session=$sessionId")
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
        backendServer.removeSessionHtml(sessionId)
    }

    private fun buildHtml(port: Int, sessionId: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
    background: #1e1e1e; color: #d4d4d4;
    display: flex; flex-direction: column; height: 100vh;
}
#messages {
    flex: 1; overflow-y: auto; padding: 8px;
    display: flex; flex-direction: column; gap: 4px;
}
.msg {
    padding: 10px 14px; border-radius: 12px;
    font-size: 13px; line-height: 1.6; word-wrap: break-word;
}
.msg pre {
    background: #0d1117; padding: 10px; border-radius: 6px;
    overflow-x: auto; margin: 6px 0; border: 1px solid #30363d;
    white-space: pre; position: relative;
}
.msg pre .code-copy {
    position: absolute; top: 4px; right: 4px;
    background: rgba(255,255,255,0.1); border: none; color: #888;
    cursor: pointer; font-size: 11px; padding: 2px 6px;
    border-radius: 4px; opacity: 0; transition: opacity 0.15s;
}
.msg pre:hover .code-copy { opacity: 1; }
.msg pre .code-copy:hover { color: #fff; background: rgba(255,255,255,0.2); }
.msg code { font-family: 'JetBrains Mono', 'Fira Code', monospace; font-size: 12px; }
.msg :not(pre) > code { background: #343942; padding: 2px 5px; border-radius: 3px; }
.msg p { margin: 4px 0; }
.msg ul, .msg ol { padding-left: 20px; margin: 4px 0; }
.msg h1,.msg h2,.msg h3,.msg h4 { font-size: 13px; font-weight: bold; margin: 8px 0 4px; }
.msg strong { font-weight: normal; }
.msg blockquote { border-left: 3px solid #555; padding-left: 10px; color: #999; margin: 4px 0; }
.msg hr { display: none; }
.msg table { border-collapse: collapse; margin: 6px 0; width: 100%; display: table; }
.msg th,.msg td { border: 1px solid #444; padding: 6px 10px; font-size: 12px; text-align: left; }
.msg th { background: #333; font-weight: 600; }
.msg tr:nth-child(even) { background: rgba(255,255,255,0.03); }
.user { align-self: flex-end; background: #264f78; color: #e8e8e8; border-bottom-right-radius: 4px; }
.assistant { align-self: flex-start; background: #2d2d2d; color: #d4d4d4; border-bottom-left-radius: 4px; }
.assistant.streaming { border-left: 2px solid #007acc; }
.msg-meta { font-size: 11px; color: #666; padding: 2px 8px; align-self: flex-start; }
.msg-wrap { position: relative; max-width: 95%; align-self: flex-start; }
.msg-wrap.user-wrap { align-self: flex-end; }
.msg-wrap .copy-btn {
    position: absolute; top: 4px; right: 4px; background: rgba(255,255,255,0.08);
    border: none; color: #888; cursor: pointer; font-size: 12px; padding: 2px 5px;
    border-radius: 4px; opacity: 0; transition: opacity 0.15s;
}
.msg-wrap:hover .copy-btn { opacity: 1; }
.msg-wrap .copy-btn:hover { color: #fff; background: rgba(255,255,255,0.15); }
.sys-toggle { align-self: flex-start; max-width: 95%; margin: 2px 0; }
.sys-toggle summary {
    font-size: 11px; color: #666; cursor: pointer; padding: 2px 8px;
    list-style: none; user-select: none;
}
.sys-toggle summary::-webkit-details-marker { display: none; }
.sys-toggle summary::before { content: '▶ '; font-size: 9px; }
.sys-toggle[open] summary::before { content: '▼ '; font-size: 9px; }
.sys-toggle .sys-log-content {
    font-size: 11px; color: #777; padding: 2px 8px 4px 16px;
    font-family: 'JetBrains Mono', monospace; white-space: pre-wrap;
}
#image-preview { display: flex; gap: 4px; flex-wrap: wrap; padding: 4px 12px; background: #252526; }
#image-preview:empty { display: none; }
#image-preview img { max-height: 60px; border-radius: 4px; border: 1px solid #444; }
#image-preview .img-wrap { position: relative; display: inline-block; }
#image-preview .img-remove {
    position: absolute; top: -4px; right: -4px; background: #c00; color: #fff;
    border: none; border-radius: 50%; width: 16px; height: 16px; font-size: 10px;
    cursor: pointer; display: flex; align-items: center; justify-content: center;
}
/* 입력 컨테이너 (하단 고정, 통합 디자인) */
#input-container {
    background: #1e1e1e;
    border-top: 1px solid #3c3c3c;
    padding: 8px;
}
#input-wrapper {
    display: flex;
    flex-direction: column;
    gap: 6px;
    background: #2d2d2d;
    border: 1px solid #3c3c3c;
    border-radius: 6px;
    padding: 8px 10px;
    transition: border-color 0.15s, box-shadow 0.15s;
}
#input-wrapper:focus-within { 
    border-color: #007acc; 
    box-shadow: 0 0 0 1px rgba(0,122,204,0.3);
}
/* 입력창 - contenteditable div로 변경하여 인라인 배지 지원 */
#input {
    width: 100%; background: transparent; color: #d4d4d4; border: none;
    font-size: 13px;
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
    min-height: 60px; max-height: 120px; line-height: 1.6; padding: 0;
    overflow-y: auto; outline: none; white-space: pre-wrap; word-wrap: break-word;
}
#input:empty::before {
    content: attr(data-placeholder);
    color: #6e6e6e;
    pointer-events: none;
}
#input.disabled { opacity: 0.4; pointer-events: none; }
/* 인라인 배지 스타일 */
.inline-badge {
    display: inline-flex; align-items: center; gap: 2px;
    padding: 1px 6px; border-radius: 4px; font-size: 12px;
    vertical-align: baseline; margin: 0 2px; white-space: nowrap;
    user-select: all; cursor: default;
}
.inline-badge.file {
    background: rgba(38,79,120,0.4); color: #7cb7e8;
    border: 1px solid rgba(58,110,165,0.5);
}
.inline-badge.agent {
    background: rgba(220,220,170,0.2); color: #dcdcaa;
    border: 1px solid rgba(220,220,170,0.4);
}
/* 하단 툴바 */
#input-toolbar {
    display: flex; align-items: center; gap: 6px;
}
/* 첨부 버튼 */
#attach-btn {
    width: 24px; height: 24px; min-width: 24px;
    background: transparent; border: none; color: #888; cursor: pointer;
    display: flex; align-items: center; justify-content: center;
    border-radius: 4px; transition: color 0.15s, background 0.15s;
    flex-shrink: 0;
}
#attach-btn:hover { color: #d4d4d4; background: rgba(255,255,255,0.08); }
#attach-btn svg { width: 14px; height: 14px; }
/* 모델 선택 버튼 */
.model-wrap { position: relative; flex-shrink: 0; margin-left: auto; }
#model-btn {
    background: transparent; color: #888; border: none;
    border-radius: 4px; padding: 4px 8px; font-size: 11px; cursor: pointer;
    transition: color 0.15s, background 0.15s;
}
#model-btn:hover { color: #d4d4d4; background: rgba(255,255,255,0.08); }
/* 전송 버튼 */
#send-btn {
    width: 24px; height: 24px; min-width: 24px;
    background: #007acc; color: #fff; border: none; border-radius: 4px;
    cursor: pointer; display: flex; align-items: center; justify-content: center;
    transition: background 0.15s, opacity 0.15s; flex-shrink: 0;
}
#send-btn:hover { background: #005a9e; }
#send-btn:disabled { opacity: 0.3; cursor: not-allowed; }
#send-btn svg { width: 12px; height: 12px; }
#model-btn {
    background: transparent; color: #888; border: none;
    border-radius: 4px; padding: 4px 8px; font-size: 11px; cursor: pointer;
    transition: color 0.15s, background 0.15s; white-space: nowrap;
}
#model-btn:hover { color: #d4d4d4; background: rgba(255,255,255,0.08); }
#model-menu {
    display: none; position: absolute; bottom: 100%; right: 0; margin-bottom: 4px;
    background: #2d2d2d; border: 1px solid #555; border-radius: 6px;
    min-width: 160px; box-shadow: 0 4px 12px rgba(0,0,0,0.5); z-index: 999;
}
#model-menu .m-item {
    padding: 6px 12px; font-size: 12px; color: #ccc; cursor: pointer;
}
#model-menu .m-item:hover { background: #094771; color: #fff; }
#model-menu .m-item.active { color: #4fc1ff; }
#autocomplete {
    display: none; position: fixed; background: #2d2d2d; border: 1px solid #555;
    border-radius: 6px; max-height: 240px; overflow-y: auto; z-index: 999;
    min-width: 280px; max-width: 400px; box-shadow: 0 4px 16px rgba(0,0,0,0.4);
}
#autocomplete .ac-item {
    display: flex; align-items: flex-start; gap: 8px;
    padding: 6px 10px; font-size: 12px; color: #ccc; cursor: pointer;
}
#autocomplete .ac-item:hover, #autocomplete .ac-item.selected { background: #094771; color: #fff; }
#autocomplete .ac-icon { width: 16px; flex-shrink: 0; text-align: center; }
#autocomplete .ac-content { flex: 1; min-width: 0; }
#autocomplete .ac-name { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
#autocomplete .ac-secondary { font-size: 10px; color: #888; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
#autocomplete .ac-item.selected .ac-secondary { color: #aaa; }
#autocomplete .ac-match { color: #e8a838; font-weight: 600; }
/* 컨텍스트 태그 영역 - 사용 안함 */
#context-tags { display: none; }
.typing-indicator { display: inline-flex; gap: 4px; padding: 4px 0; }
.typing-indicator span {
    width: 6px; height: 6px; background: #888; border-radius: 50%;
    animation: bounce 1.4s infinite ease-in-out;
}
.typing-indicator span:nth-child(1) { animation-delay: 0s; }
.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }
@keyframes bounce {
    0%,80%,100% { transform: scale(0.6); opacity: 0.4; }
    40% { transform: scale(1); opacity: 1; }
}
.typing-indicator-standalone {
    display: inline-flex; gap: 4px; padding: 10px 14px;
    align-self: flex-start;
}
.typing-indicator-standalone span {
    width: 6px; height: 6px; background: #888; border-radius: 50%;
    animation: bounce 1.4s infinite ease-in-out;
}
.typing-indicator-standalone span:nth-child(1) { animation-delay: 0s; }
.typing-indicator-standalone span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator-standalone span:nth-child(3) { animation-delay: 0.4s; }
</style>
</head>
<body>
<div id="messages"></div>
<div id="image-preview"></div>
<div id="input-container">
    <div id="input-wrapper">
        <div id="input" contenteditable="true" data-placeholder=""></div>
        <div id="input-toolbar">
            <button id="attach-btn" title="이미지 첨부 (Ctrl+V)">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M21.44 11.05l-9.19 9.19a6 6 0 01-8.49-8.49l9.19-9.19a4 4 0 015.66 5.66l-9.2 9.19a2 2 0 01-2.83-2.83l8.49-8.48"/>
                </svg>
            </button>
            <div class="model-wrap">
                <button id="model-btn">Auto ▾</button>
                <div id="model-menu"></div>
            </div>
            <button id="send-btn" onclick="sendMessage()" title="전송 (Enter)">
                <svg viewBox="0 0 24 24" fill="currentColor"><path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/></svg>
            </button>
        </div>
    </div>
</div>
<div id="autocomplete"></div>
<input type="file" id="file-input" accept="image/*" multiple style="display:none">
<script>
const API = 'http://127.0.0.1:$port';
const SESSION = '$sessionId';
const messagesDiv = document.getElementById('messages');
const input = document.getElementById('input');
const sendBtn = document.getElementById('send-btn');
const modelBtn = document.getElementById('model-btn');
const modelMenu = document.getElementById('model-menu');
const autocompleteDiv = document.getElementById('autocomplete');
const imagePreview = document.getElementById('image-preview');
const fileInput = document.getElementById('file-input');
const attachBtn = document.getElementById('attach-btn');
let isStreaming = false;
let currentAssistantDiv = null;
let currentContent = '';
let currentAssistantWrap = null;
let currentSysToggle = null;
let sysLogCount = 0;
let openFiles = [];
let excludedPaths = new Set();
let attachedImages = [];
let acSelectedIndex = -1;
let currentModel = 'Auto';
let projectFiles = [];  // 프로젝트 파일 목록 (캐시)
let agents = [];        // 에이전트 목록 (캐시)
let i18n = { placeholder: '', openFile: '', systemLog: '' }; // 다국어 메시지

// i18n 로드
fetch(API + '/api/i18n').then(r => r.json()).then(data => {
    i18n = data;
    input.dataset.placeholder = i18n.placeholder || 'Enter message...';
}).catch(() => {
    input.dataset.placeholder = 'Enter message...';
});

// 시스템 로그 분류는 백엔드(KiroCliProcess)에서 [SYS] 접두사로 처리
// 프론트엔드에서는 [SYS] 접두사만 확인

// 완전히 제거할 패턴 (표시하지 않음)
const SKIP_PATTERNS = [
    /^\s*$/
];

function shouldSkipLine(line) {
    const trimmed = line.trim();
    return SKIP_PATTERNS.some(p => p.test(trimmed));
}

// --- model selector (opens upward) ---
const MODELS = [
    { value: 'auto', label: 'Auto' },
    { value: 'claude-opus-4.6', label: 'Claude Opus 4.6' },
    { value: 'claude-sonnet-4.6', label: 'Claude Sonnet 4.6' },
    { value: 'claude-opus-4.5', label: 'Claude Opus 4.5' },
    { value: 'claude-sonnet-4.5', label: 'Claude Sonnet 4.5' },
    { value: 'claude-sonnet-4', label: 'Claude Sonnet 4' },
    { value: 'claude-haiku-4.5', label: 'Claude Haiku 4.5' },
    { value: 'deepseek-3.2', label: 'DeepSeek 3.2' },
    { value: 'minimax-m2.1', label: 'MiniMax M2.1' },
    { value: 'minimax-m2.5', label: 'MiniMax M2.5' },
    { value: 'qwen3-coder-next', label: 'Qwen3 Coder' },
];
function renderModelMenu() {
    modelMenu.innerHTML = '';
    MODELS.forEach(m => {
        const item = document.createElement('div');
        item.className = 'm-item' + (m.value === currentModel ? ' active' : '');
        item.textContent = m.label;
        item.onclick = (e) => {
            e.stopPropagation();
            currentModel = m.value;
            modelBtn.textContent = m.label + ' ▾';
            modelMenu.style.display = 'none';
            fetch(API + '/api/set-model', { method: 'POST', body: SESSION + '\n' + m.value }).catch(() => {});
            renderModelMenu();
        };
        modelMenu.appendChild(item);
    });
}
renderModelMenu();
modelBtn.onclick = (e) => {
    e.stopPropagation();
    modelMenu.style.display = modelMenu.style.display === 'block' ? 'none' : 'block';
};
document.addEventListener('click', () => { modelMenu.style.display = 'none'; });

// contenteditable input 이벤트
let isComposing = false;
input.addEventListener('compositionstart', () => { isComposing = true; });
input.addEventListener('compositionend', () => { isComposing = false; handleAutocomplete(); });
// 붙여넣기 시 순수 텍스트만 삽입 (HTML 말풍선 방지)
input.addEventListener('paste', (e) => {
    e.preventDefault();
    const text = (e.clipboardData || window.clipboardData).getData('text/plain');
    document.execCommand('insertText', false, text);
});
input.addEventListener('input', () => {
    if (!isComposing) handleAutocomplete();
});
input.addEventListener('keydown', (e) => {
    if (isComposing) return;
    handleKey(e);
});

function handleKey(e) {
    if (autocompleteDiv.style.display === 'block') {
        const items = autocompleteDiv.querySelectorAll('.ac-item');
        if (e.key === 'ArrowDown') { e.preventDefault(); acSelectedIndex = Math.min(acSelectedIndex+1, items.length-1); updateAcSelection(items); return; }
        if (e.key === 'ArrowUp') { e.preventDefault(); acSelectedIndex = Math.max(acSelectedIndex-1, 0); updateAcSelection(items); return; }
        if ((e.key === 'Enter' || e.key === 'Tab') && acSelectedIndex >= 0 && items[acSelectedIndex]) {
            e.preventDefault();
            const item = items[acSelectedIndex];
            if (acType === 'command') {
                insertCommandAutocomplete(item.dataset.cmd);
            } else if (acType === 'file') {
                insertFileContext(item.dataset.path, item.dataset.name);
            } else if (acType === 'agent') {
                insertAgentContext(item.dataset.name);
            }
            return;
        }
        if (e.key === 'Escape') { hideAutocomplete(); return; }
    }
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage(); }
}

// contenteditable에서 텍스트 추출 (배지는 #name, @name 형태로 변환)
function getInputText() {
    let text = '';
    input.childNodes.forEach(node => {
        if (node.nodeType === Node.TEXT_NODE) {
            text += node.textContent;
        } else if (node.nodeType === Node.ELEMENT_NODE) {
            if (node.classList.contains('inline-badge')) {
                const type = node.dataset.type;
                const name = node.dataset.name;
                text += (type === 'file' ? '#' : '@') + name;
            } else {
                text += node.textContent;
            }
        }
    });
    return text;
}

function setInput(text) { 
    input.textContent = text; 
    input.dispatchEvent(new Event('input')); 
    input.focus(); 
    // 커서를 끝으로 이동
    const range = document.createRange();
    const sel = window.getSelection();
    range.selectNodeContents(input);
    range.collapse(false);
    sel.removeAllRanges();
    sel.addRange(range);
}

function sendMessage() {
    const text = getInputText().trim();
    if (!text || isStreaming) return;
    hideAutocomplete();
    
    // 메시지 내에 #file, @agent가 인라인으로 포함되어 있음
    let fullMessage = text;
    
    // 현재 활성화된 파일 항상 컨텍스트에 추가
    const activeFile = openFiles.find(f => f.active);
    if (activeFile) {
        fullMessage = '[현재 파일: ' + activeFile.relativePath + ']\n' + fullMessage;
    }
    
    if (attachedImages.length > 0) {
        const paths = attachedImages.map(img => img.savedPath || img.name).join(', ');
        fullMessage = '[첨부 이미지: ' + paths + ']\n' + fullMessage;
    }
    
    addUserMessage(text);
    input.innerHTML = '';
    attachedImages = []; imagePreview.innerHTML = '';
    setEnabled(false);
    fetch(API + '/api/send', { method: 'POST', body: SESSION + '\n' + fullMessage })
        .catch(err => { addErrorMessage('전송 실패: ' + err.message); setEnabled(true); });
}

function addUserMessage(text) {
    const wrap = document.createElement('div');
    wrap.className = 'msg-wrap user-wrap';
    const div = document.createElement('div');
    div.className = 'msg user'; div.textContent = text;
    const copyBtn = document.createElement('button');
    copyBtn.className = 'copy-btn'; copyBtn.textContent = '📋'; copyBtn.title = '복사';
    copyBtn.onclick = () => { navigator.clipboard.writeText(text); copyBtn.textContent = '✓'; setTimeout(() => copyBtn.textContent = '📋', 1000); };
    wrap.appendChild(div); wrap.appendChild(copyBtn);
    messagesDiv.appendChild(wrap); scrollToBottom();
}

let typingIndicator = null;

function startAssistantMessage() {
    currentContent = ''; sysLogCount = 0; currentSysToggle = null;
    currentAssistantWrap = null;
    currentAssistantDiv = null;
    // 독립 typing indicator 표시
    typingIndicator = document.createElement('div');
    typingIndicator.className = 'typing-indicator-standalone';
    typingIndicator.innerHTML = '<span></span><span></span><span></span>';
    messagesDiv.appendChild(typingIndicator);
    scrollToBottom();
}

function ensureAssistantBubble() {
    if (currentAssistantDiv) return;
    // typing indicator 제거
    if (typingIndicator) { typingIndicator.remove(); typingIndicator = null; }
    currentAssistantWrap = document.createElement('div');
    currentAssistantWrap.className = 'msg-wrap';
    currentAssistantDiv = document.createElement('div');
    currentAssistantDiv.className = 'msg assistant streaming';
    const copyBtn = document.createElement('button');
    copyBtn.className = 'copy-btn'; copyBtn.textContent = '📋'; copyBtn.title = '복사';
    copyBtn.onclick = () => { navigator.clipboard.writeText(currentContent.replace(/▸\s*Time:\s*[\d.]+s/g, '').trim()); copyBtn.textContent = '✓'; setTimeout(() => copyBtn.textContent = '📋', 1000); };
    currentAssistantWrap.appendChild(currentAssistantDiv);
    currentAssistantWrap.appendChild(copyBtn);
    messagesDiv.appendChild(currentAssistantWrap);
}

function renderContent(raw) {
    // 시스템 메시지 패턴 제거
    let display = raw
        .replace(/▸\s*Time:\s*[\d.]+s/g, '')
        .replace(/Completed in [\d.]+s\.?/gi, '')
        .replace(/^-{3,}$/gm, '')
        .replace(/\x1B\[[0-9;]*[a-zA-Z]/g, '')
        .replace(/\[0m/g, '')
        .replace(/\[38;5;\d+m/g, '')
        .replace(/\[\d+m/g, '')
        .replace(/\*\*([^*]+)\*\*/g, '$1')
        .trim();
    
    if (!display) return '';
    
    // kiro-cli --no-interactive는 ```를 출력하지 않음
    // 언어 힌트(kotlin, java 등) + 코드 패턴을 감지하여 ```로 감싸기
    display = autoWrapCodeBlocks(display);
    
    try { 
        if (typeof marked !== 'undefined' && marked.parse) {
            return marked.parse(display);
        }
        return simpleMarkdown(display);
    }
    catch(e) { 
        console.error('Markdown parse error:', e);
        return simpleMarkdown(display);
    }
}

// kiro-cli --no-interactive 출력 패턴:
// 언어이름 (예: "kotlin") 한 줄 → 코드 → 빈 줄 2개로 종료
const CODE_LANG_HINTS = /^(kotlin|java|python|javascript|typescript|bash|sh|shell|sql|xml|json|yaml|yml|html|css|swift|rust|go|c|cpp|ruby|php|scala|groovy|dart|text|plaintext|diff|makefile|dockerfile|toml|ini|properties|gradle)\s*$/i;

function autoWrapCodeBlocks(text) {
    if (text.includes('```')) return text;
    
    const lines = text.split('\n');
    const result = [];
    let i = 0;
    
    while (i < lines.length) {
        const trimmed = lines[i].trim();
        
        // 언어 힌트 감지
        if (CODE_LANG_HINTS.test(trimmed)) {
            const lang = trimmed.toLowerCase();
            const codeLines = [];
            i++;
            
            // 코드 수집: 빈 줄 2개 연속 또는 자연어 문장이 나올 때까지
            let emptyCount = 0;
            while (i < lines.length) {
                const line = lines[i];
                const lt = line.trim();
                
                if (lt === '') {
                    emptyCount++;
                    if (emptyCount >= 2) { i++; break; }
                    codeLines.push(line);
                    i++;
                    continue;
                }
                
                emptyCount = 0;
                
                // 자연어 문장이면 코드 블럭 종료
                if (isNaturalLanguage(lt)) {
                    break;
                }
                
                codeLines.push(line);
                i++;
            }
            
            // 끝의 빈 줄 제거
            while (codeLines.length > 0 && codeLines[codeLines.length - 1].trim() === '') {
                codeLines.pop();
            }
            
            if (codeLines.length > 0) {
                result.push('```' + lang);
                result.push(...codeLines);
                result.push('```');
            }
        } else {
            result.push(lines[i]);
            i++;
        }
    }
    
    return result.join('\n');
}

function isNaturalLanguage(line) {
    if (!line || line.length < 3) return false;
    if (/[\uAC00-\uD7AF\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FFF]/.test(line)) return true;
    if (/^[A-Z][a-z]+\s+[a-z]+\s/.test(line)) return true;
    if (/^(The|This|It|Here|Note|See|For|If|When|After|Before|You|We|I)\s/i.test(line)) return true;
    if (/^\d+\.\s+[A-Za-z\uAC00-\uD7AF]/.test(line)) return true;
    if (/^[-*]\s+[A-Za-z\uAC00-\uD7AF]/.test(line)) return true;
    if (/^>\s/.test(line)) return true;
    return false;
}

// 간단한 마크다운 파서 (fallback)
function simpleMarkdown(text) {
    let html = escapeHtml(text);
    
    // 코드 블록
    html = html.replace(/```(\w*)\n([\s\S]*?)```/g, '<pre><code>$2</code></pre>');
    
    // 인라인 코드
    html = html.replace(/`([^`]+)`/g, '<code>$1</code>');
    
    // 헤더
    html = html.replace(/^### (.+)$/gm, '<h3>$1</h3>');
    html = html.replace(/^## (.+)$/gm, '<h2>$1</h2>');
    html = html.replace(/^# (.+)$/gm, '<h1>$1</h1>');
    
    // 굵게/기울임
    html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
    html = html.replace(/\*(.+?)\*/g, '<em>$1</em>');
    
    // 리스트
    html = html.replace(/^- (.+)$/gm, '<li>$1</li>');
    html = html.replace(/^(\d+)\. (.+)$/gm, '<li>$2</li>');
    
    // 테이블 (간단한 처리)
    const lines = html.split('\n');
    let inTable = false;
    let tableHtml = '';
    const result = [];
    
    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        if (line.includes('|') && line.trim().startsWith('|')) {
            if (!inTable) {
                inTable = true;
                tableHtml = '<table>';
            }
            // 구분선 무시
            if (/^\|[\s\-:|]+\|$/.test(line.trim())) continue;
            
            const cells = line.split('|').filter(c => c.trim());
            const isHeader = i === 0 || (i > 0 && /^\|[\s\-:|]+\|$/.test(lines[i-1]?.trim() || ''));
            const tag = isHeader ? 'th' : 'td';
            tableHtml += '<tr>' + cells.map(c => '<' + tag + '>' + c.trim() + '</' + tag + '>').join('') + '</tr>';
        } else {
            if (inTable) {
                tableHtml += '</table>';
                result.push(tableHtml);
                tableHtml = '';
                inTable = false;
            }
            result.push(line);
        }
    }
    if (inTable) {
        tableHtml += '</table>';
        result.push(tableHtml);
    }
    
    html = result.join('\n');
    
    // 줄바꿈
    html = html.replace(/\n/g, '<br>');
    
    // 연속 br 정리
    html = html.replace(/(<br>){3,}/g, '<br><br>');
    
    return html;
}

function appendChunk(chunk) {
    const lines = chunk.split('\n');
    for (const line of lines) {
        // 완전히 제거할 라인
        if (shouldSkipLine(line)) continue;
        
        // 백엔드에서 [SYS] 접두사로 시스템 로그 표시
        const isSys = line.startsWith('[SYS]');
        const displayLine = isSys ? line.substring(5) : line;
        
        if (isSys) {
            if (typingIndicator) { typingIndicator.remove(); typingIndicator = null; }
            flushContent();
            sysLogCount++;
            if (!currentSysToggle) {
                currentSysToggle = document.createElement('details');
                currentSysToggle.className = 'sys-toggle';
                const summary = document.createElement('summary');
                summary.textContent = (i18n.systemLog || 'System log') + ' (' + sysLogCount + ')';
                currentSysToggle.appendChild(summary);
                const content = document.createElement('div');
                content.className = 'sys-log-content';
                currentSysToggle.appendChild(content);
                messagesDiv.appendChild(currentSysToggle);
            }
            currentSysToggle.querySelector('summary').textContent = (i18n.systemLog || 'System log') + ' (' + sysLogCount + ')';
            const logContent = currentSysToggle.querySelector('.sys-log-content');
            logContent.textContent += displayLine + '\n';
        } else {
            currentContent += displayLine + '\n';
        }
    }
    flushContent();
    scrollToBottom();
}

function flushContent() {
    if (currentContent.trim()) {
        ensureAssistantBubble();
        currentAssistantDiv.innerHTML = renderContent(currentContent);
        addCodeCopyButtons(currentAssistantDiv);
        scrollToBottom();
    }
}

function addCodeCopyButtons(container) {
    container.querySelectorAll('pre').forEach(pre => {
        if (pre.querySelector('.code-copy')) return; // 이미 있으면 스킵
        const btn = document.createElement('button');
        btn.className = 'code-copy';
        btn.textContent = '📋';
        btn.title = 'Copy code';
        btn.onclick = (e) => {
            e.stopPropagation();
            const code = pre.querySelector('code') || pre;
            navigator.clipboard.writeText(code.textContent);
            btn.textContent = '✓';
            setTimeout(() => btn.textContent = '📋', 1000);
        };
        pre.appendChild(btn);
    });
}

function finishAssistantMessage() {
    if (typingIndicator) { typingIndicator.remove(); typingIndicator = null; }
    if (currentAssistantDiv) {
        currentAssistantDiv.classList.remove('streaming');
        if (currentContent.trim()) {
            currentAssistantDiv.innerHTML = renderContent(currentContent);
            addCodeCopyButtons(currentAssistantDiv);
        } else {
            currentAssistantWrap.remove();
        }
    }
    currentAssistantDiv = null;
    currentAssistantWrap = null;
    setEnabled(true);
}

function addErrorMessage(text) {
    if (typingIndicator) { typingIndicator.remove(); typingIndicator = null; }
    ensureAssistantBubble();
    if (currentAssistantDiv) {
        currentAssistantDiv.innerHTML = '<span style="color:#f44">⚠ ' + escapeHtml(text) + '</span>';
        currentAssistantDiv.classList.remove('streaming'); currentAssistantDiv = null;
    }
}

function setEnabled(en) { 
    isStreaming = !en; 
    if (!en) input.classList.add('disabled'); 
    else input.classList.remove('disabled');
    input.contentEditable = en ? 'true' : 'false';
    
    // 전송/중단 버튼 전환
    if (en) {
        sendBtn.innerHTML = '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/></svg>';
        sendBtn.style.background = '#007acc';
        sendBtn.onclick = () => sendMessage();
        sendBtn.title = '전송 (Enter)';
    } else {
        sendBtn.innerHTML = '<svg viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="6" width="12" height="12" rx="2"/></svg>';
        sendBtn.style.background = '#6e6e6e';
        sendBtn.onclick = () => stopGeneration();
        sendBtn.title = '중단';
    }
    sendBtn.disabled = false;
    if (en) input.focus(); 
}

function stopGeneration() {
    fetch(API + '/api/stop', { method: 'POST', body: SESSION })
        .catch(() => {});
    finishAssistantMessage();
}
function scrollToBottom() { messagesDiv.scrollTop = messagesDiv.scrollHeight; }
function escapeHtml(s) { return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }

// --- 슬래시 커맨드 목록 ---
const SLASH_COMMANDS = [
    { cmd: '/quit', desc: '앱 종료' },
    { cmd: '/clear', desc: '대화 기록 삭제' },
    { cmd: '/agent', desc: '에이전트 관리' },
    { cmd: '/chat', desc: '저장된 대화 관리' },
    { cmd: '/context', desc: '컨텍스트 파일 관리' },
    { cmd: '/code', desc: 'LSP 코드 인텔리전스' },
    { cmd: '/editor', desc: '에디터로 프롬프트 작성' },
    { cmd: '/reply', desc: '마지막 응답에 답장' },
    { cmd: '/compact', desc: '대화 요약하여 컨텍스트 확보' },
    { cmd: '/tools', desc: '도구 및 권한 보기' },
    { cmd: '/issue', desc: 'GitHub 이슈 생성' },
    { cmd: '/logdump', desc: '로그 파일 생성' },
    { cmd: '/changelog', desc: '변경 로그 보기' },
    { cmd: '/prompts', desc: '프롬프트 보기' },
    { cmd: '/hooks', desc: '컨텍스트 훅 보기' },
    { cmd: '/usage', desc: '사용량 및 크레딧 정보' },
    { cmd: '/mcp', desc: 'MCP 서버 목록' },
    { cmd: '/model', desc: '모델 선택' },
    { cmd: '/experiment', desc: '실험 기능 토글' },
    { cmd: '/plan', desc: 'Plan 에이전트로 전환' },
    { cmd: '/todos', desc: '할 일 목록 관리' },
    { cmd: '/paste', desc: '클립보드 이미지 붙여넣기' },
    { cmd: '/help', desc: '도움말' },
];

// 파일 확장자별 아이콘
const FILE_ICONS = {
    'kt': '📄', 'java': '☕', 'ts': '🔷', 'tsx': '🔷', 'js': '🟨', 'jsx': '🟨',
    'json': '📋', 'xml': '📰', 'md': '📝', 'yaml': '⚙️', 'yml': '⚙️',
    'gradle': '🐘', 'kts': '🐘', 'py': '🐍', 'rb': '💎', 'go': '🔵',
    'rs': '🦀', 'swift': '🍎', 'css': '🎨', 'html': '🌐', 'sql': '🗃️',
    'sh': '📜', 'bash': '📜', 'default': '📄'
};
function getFileIcon(filename) {
    const ext = filename.split('.').pop()?.toLowerCase() || '';
    return FILE_ICONS[ext] || FILE_ICONS['default'];
}

// --- 자동완성 타입 ---
let acType = ''; // 'command', 'file', 'agent'
let acDebounceTimer = null;

// contenteditable에서 커서 앞의 텍스트 가져오기
function getTextBeforeCursor() {
    const sel = window.getSelection();
    if (!sel.rangeCount) return '';
    
    const range = sel.getRangeAt(0);
    const preRange = range.cloneRange();
    preRange.selectNodeContents(input);
    preRange.setEnd(range.startContainer, range.startOffset);
    
    // 텍스트만 추출 (배지는 무시)
    let text = '';
    const walker = document.createTreeWalker(input, NodeFilter.SHOW_TEXT, null, false);
    let node;
    while ((node = walker.nextNode())) {
        if (preRange.intersectsNode(node)) {
            const nodeRange = document.createRange();
            nodeRange.selectNodeContents(node);
            if (preRange.compareBoundaryPoints(Range.END_TO_START, nodeRange) <= 0) {
                if (preRange.compareBoundaryPoints(Range.END_TO_END, nodeRange) >= 0) {
                    text += node.textContent;
                } else {
                    text += node.textContent.substring(0, preRange.endOffset - (node === range.startContainer ? 0 : 0));
                }
            }
        }
    }
    
    // 간단한 방식: 현재 텍스트 노드에서 커서 앞 텍스트
    if (range.startContainer.nodeType === Node.TEXT_NODE) {
        return range.startContainer.textContent.substring(0, range.startOffset);
    }
    return '';
}

function handleAutocomplete() {
    const before = getTextBeforeCursor();
    
    // 슬래시 커맨드 자동완성 (줄 시작에서만)
    const slashMatch = before.match(/^\/([^\s]*)$/) || before.match(/\n\/([^\s]*)$/);
    if (slashMatch) {
        const q = slashMatch[1].toLowerCase();
        const matches = SLASH_COMMANDS.filter(c => c.cmd.toLowerCase().includes('/' + q)).slice(0, 15);
        if (matches.length > 0) { showCommandAutocomplete(matches); return; }
    }
    
    // # 파일 검색 자동완성
    const hashMatch = before.match(/#([^\s]*)$/);
    if (hashMatch) {
        const q = hashMatch[1];
        clearTimeout(acDebounceTimer);
        acDebounceTimer = setTimeout(() => searchProjectFiles(q), 150);
        return;
    }
    
    // @ 에이전트 자동완성
    const atMatch = before.match(/@([^\s]*)$/);
    if (atMatch) {
        const q = atMatch[1].toLowerCase();
        if (agents.length === 0) {
            // 에이전트 목록 로드
            fetch(API + '/api/agents').then(r => r.json()).then(data => {
                agents = data;
                showAgentAutocomplete(q);
            }).catch(() => {});
        } else {
            showAgentAutocomplete(q);
        }
        return;
    }
    
    hideAutocomplete();
}

// 프로젝트 파일 검색
function searchProjectFiles(query) {
    const url = query ? API + '/api/project-files?q=' + encodeURIComponent(query) : API + '/api/project-files';
    fetch(url).then(r => r.json()).then(files => {
        if (files.length > 0) {
            showFileSearchAutocomplete(files, query);
        } else {
            hideAutocomplete();
        }
    }).catch(() => hideAutocomplete());
}

// 파일 검색 자동완성 표시
function showFileSearchAutocomplete(files, query) {
    acType = 'file';
    autocompleteDiv.innerHTML = ''; acSelectedIndex = 0;
    files.slice(0, 15).forEach((f, i) => {
        const item = document.createElement('div');
        item.className = 'ac-item' + (i === 0 ? ' selected' : '');
        item.dataset.path = f.path;
        item.dataset.name = f.name;
        item.innerHTML = '<span class="ac-icon">' + getFileIcon(f.name) + '</span>' +
            '<div class="ac-content">' +
            '<div class="ac-name">' + highlightMatch(f.name, query) + '</div>' +
            '<div class="ac-secondary">' + escapeHtml(f.dir) + '</div>' +
            '</div>';
        item.onclick = () => insertFileContext(f.path, f.name);
        autocompleteDiv.appendChild(item);
    });
    positionAutocomplete();
}

// 에이전트 자동완성 표시
function showAgentAutocomplete(query) {
    acType = 'agent';
    const matches = agents.filter(a => a.name.toLowerCase().includes(query)).slice(0, 10);
    if (matches.length === 0) { hideAutocomplete(); return; }
    
    autocompleteDiv.innerHTML = ''; acSelectedIndex = 0;
    matches.forEach((a, i) => {
        const item = document.createElement('div');
        item.className = 'ac-item' + (i === 0 ? ' selected' : '');
        item.dataset.name = a.name;
        const desc = a.description.length > 50 ? a.description.substring(0, 50) + '...' : a.description;
        item.innerHTML = '<span class="ac-icon">🤖</span>' +
            '<div class="ac-content">' +
            '<div class="ac-name">' + highlightMatch(a.name, query) + '</div>' +
            '<div class="ac-secondary">' + escapeHtml(desc) + '</div>' +
            '</div>';
        item.onclick = () => insertAgentContext(a.name);
        autocompleteDiv.appendChild(item);
    });
    positionAutocomplete();
}

// 매칭 텍스트 하이라이트
function highlightMatch(text, query) {
    if (!query) return escapeHtml(text);
    const idx = text.toLowerCase().indexOf(query.toLowerCase());
    if (idx === -1) return escapeHtml(text);
    return escapeHtml(text.substring(0, idx)) +
           '<span class="ac-match">' + escapeHtml(text.substring(idx, idx + query.length)) + '</span>' +
           escapeHtml(text.substring(idx + query.length));
}

function showCommandAutocomplete(matches) {
    acType = 'command';
    autocompleteDiv.innerHTML = ''; acSelectedIndex = 0;
    matches.forEach((c, i) => {
        const item = document.createElement('div');
        item.className = 'ac-item' + (i === 0 ? ' selected' : '');
        item.innerHTML = '<span class="ac-icon" style="color:#4fc1ff">/</span>' +
            '<div class="ac-content">' +
            '<div class="ac-name" style="color:#4fc1ff">' + escapeHtml(c.cmd) + '</div>' +
            '<div class="ac-secondary">' + escapeHtml(c.desc) + '</div>' +
            '</div>';
        item.dataset.cmd = c.cmd;
        item.onclick = () => insertCommandAutocomplete(c.cmd);
        autocompleteDiv.appendChild(item);
    });
    positionAutocomplete();
}

function positionAutocomplete() {
    const rect = input.getBoundingClientRect();
    autocompleteDiv.style.left = rect.left + 'px';
    autocompleteDiv.style.bottom = (window.innerHeight - rect.top + 4) + 'px';
    autocompleteDiv.style.display = 'block';
}

function insertCommandAutocomplete(cmd) {
    // 커서 위치에서 /query를 cmd로 교체
    deleteTextBeforeCursor(/\/[^\s]*$/);
    insertTextAtCursor(cmd + ' ');
    hideAutocomplete(); input.focus();
}

// 커서 앞의 패턴 삭제
function deleteTextBeforeCursor(pattern) {
    const sel = window.getSelection();
    if (!sel.rangeCount) return;
    
    const range = sel.getRangeAt(0);
    if (range.startContainer.nodeType !== Node.TEXT_NODE) return;
    
    const textNode = range.startContainer;
    const offset = range.startOffset;
    const text = textNode.textContent.substring(0, offset);
    const match = text.match(pattern);
    
    if (match) {
        const start = text.lastIndexOf(match[0]);
        textNode.textContent = textNode.textContent.substring(0, start) + textNode.textContent.substring(offset);
        range.setStart(textNode, start);
        range.collapse(true);
        sel.removeAllRanges();
        sel.addRange(range);
    }
}

// 커서 위치에 텍스트 삽입
function insertTextAtCursor(text) {
    const sel = window.getSelection();
    if (!sel.rangeCount) return;
    
    const range = sel.getRangeAt(0);
    range.deleteContents();
    const textNode = document.createTextNode(text);
    range.insertNode(textNode);
    range.setStartAfter(textNode);
    range.collapse(true);
    sel.removeAllRanges();
    sel.addRange(range);
}

// 커서 위치에 배지 삽입
function insertBadgeAtCursor(type, name, displayName) {
    const sel = window.getSelection();
    if (!sel.rangeCount) return;
    
    const range = sel.getRangeAt(0);
    range.deleteContents();
    
    // 배지 요소 생성
    const badge = document.createElement('span');
    badge.className = 'inline-badge ' + type;
    badge.contentEditable = 'false';
    badge.dataset.type = type;
    badge.dataset.name = name;
    badge.innerHTML = '<span style="opacity:0.7">' + (type === 'file' ? '#' : '@') + '</span>' + escapeHtml(displayName);
    
    range.insertNode(badge);
    
    // 배지 뒤에 공백 추가
    const space = document.createTextNode(' ');
    badge.parentNode.insertBefore(space, badge.nextSibling);
    
    // 커서를 공백 뒤로 이동
    range.setStartAfter(space);
    range.collapse(true);
    sel.removeAllRanges();
    sel.addRange(range);
}

// 파일 컨텍스트 삽입 (커서 위치에 배지로 삽입)
function insertFileContext(path, name) {
    deleteTextBeforeCursor(/#[^\s]*$/);
    insertBadgeAtCursor('file', path, name);
    hideAutocomplete(); input.focus();
}

// 에이전트 컨텍스트 삽입 (커서 위치에 배지로 삽입)
function insertAgentContext(name) {
    deleteTextBeforeCursor(/@[^\s]*$/);
    insertBadgeAtCursor('agent', name, name);
    hideAutocomplete(); input.focus();
}

function hideAutocomplete() { autocompleteDiv.style.display = 'none'; acSelectedIndex = -1; acType = ''; }
function updateAcSelection(items) {
    items.forEach((el, i) => el.classList.toggle('selected', i === acSelectedIndex));
    if (items[acSelectedIndex]) items[acSelectedIndex].scrollIntoView({ block: 'nearest' });
}

// --- image attach ---
attachBtn.onclick = () => fileInput.click();
fileInput.onchange = () => { for (const f of fileInput.files) addImage(f); fileInput.value = ''; };
document.addEventListener('paste', (e) => {
    const items = e.clipboardData?.items; if (!items) return;
    for (const item of items) {
        if (item.type.startsWith('image/')) { e.preventDefault(); addImage(item.getAsFile()); }
    }
});
function addImage(file) {
    const reader = new FileReader();
    reader.onload = (ev) => {
        const dataUrl = ev.target.result;
        // 서버에 저장 요청
        fetch(API + '/api/save-image', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ session: SESSION, name: file.name, data: dataUrl })
        }).then(r => r.text()).then(path => {
            attachedImages.push({ name: file.name, data: dataUrl, savedPath: path });
            renderImagePreviews();
        }).catch(() => {
            attachedImages.push({ name: file.name, data: dataUrl, savedPath: '' });
            renderImagePreviews();
        });
    };
    reader.readAsDataURL(file);
}
function renderImagePreviews() {
    imagePreview.innerHTML = '';
    attachedImages.forEach((img, i) => {
        const w = document.createElement('div'); w.className = 'img-wrap';
        w.innerHTML = '<img src="' + img.data + '" title="' + escapeHtml(img.name) + '"><button class="img-remove">&times;</button>';
        w.querySelector('.img-remove').onclick = () => { attachedImages.splice(i, 1); renderImagePreviews(); };
        imagePreview.appendChild(w);
    });
}

// --- SSE ---
function connectSSE() {
    const es = new EventSource(API + '/api/stream?session=' + SESSION);
    es.addEventListener('start', () => startAssistantMessage());
    es.addEventListener('chunk', (e) => appendChunk(e.data));
    es.addEventListener('done', () => finishAssistantMessage());
    es.addEventListener('error', (e) => { if (e.data) addErrorMessage(e.data); finishAssistantMessage(); });
    es.addEventListener('ping', () => {});
    es.onerror = () => setTimeout(connectSSE, 2000);
}

// marked.js 설정 (GFM 테이블, 줄바꿈 지원)
if (typeof marked !== 'undefined') { 
    const renderer = new marked.Renderer();
    // 볼드체 비활성화 - 일반 텍스트로 출력
    renderer.strong = (text) => text;
    // hr 비활성화
    renderer.hr = () => '';
    
    marked.setOptions({ 
        breaks: true, 
        gfm: true,
        headerIds: false,
        mangle: false,
        renderer: renderer
    }); 
} else {
    console.warn('marked.js not loaded, using fallback renderer');
}

// --- open files ---
function refreshOpenFiles() {
    fetch(API + '/api/open-files').then(r => r.json()).then(files => {
        const currentPaths = new Set(files.map(f => f.path));
        // 더 이상 열려있지 않은 파일은 excluded에서도 제거
        for (const p of excludedPaths) { if (!currentPaths.has(p)) excludedPaths.delete(p); }
        openFiles = files;
    }).catch(() => {});
}

refreshOpenFiles(); setInterval(refreshOpenFiles, 3000);
connectSSE(); input.focus();
</script>
</body>
</html>
    """.trimIndent()
}
