package com.kiro.intellij.chat

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * 채팅 세션 멀티 탭 컨테이너.
 * 상단에 탭 바 (+ 추가, × 닫기), 하단에 현재 ChatPanel 표시.
 */
class ChatTabPanel(
    private val project: Project,
    private val parentDisposable: Disposable,
    private val backendServer: ChatBackendServer
) : Disposable {

    private val mainPanel = JPanel(BorderLayout())
    private val tabBar = TabBar()
    private val contentArea = JPanel(BorderLayout())
    private val tabs = mutableListOf<TabEntry>()
    private var activeIndex = -1
    private var tabCounter = 0

    val component: JComponent get() = mainPanel

    init {
        Disposer.register(parentDisposable, this)
        mainPanel.add(tabBar, BorderLayout.NORTH)
        mainPanel.add(contentArea, BorderLayout.CENTER)
        addNewTab()
    }

    fun getActiveChatPanel(): ChatPanel? {
        return tabs.getOrNull(activeIndex)?.panel
    }

    fun addNewTab() {
        tabCounter++
        val name = if (tabCounter == 1) "Chat" else "Chat $tabCounter"
        val panel = ChatPanel(project, this, backendServer, name)
        val entry = TabEntry(name, panel)
        tabs.add(entry)
        selectTab(tabs.size - 1)
        tabBar.repaint()
    }

    private fun closeTab(index: Int) {
        if (tabs.size <= 1 || index < 0 || index >= tabs.size) return
        val entry = tabs.removeAt(index)
        Disposer.dispose(entry.panel)
        if (activeIndex >= tabs.size) activeIndex = tabs.size - 1
        if (activeIndex == index || activeIndex >= tabs.size) {
            activeIndex = (index - 1).coerceAtLeast(0)
        }
        selectTab(activeIndex)
        tabBar.repaint()
    }

    private fun selectTab(index: Int) {
        if (index < 0 || index >= tabs.size) return
        activeIndex = index
        contentArea.removeAll()
        contentArea.add(tabs[index].panel.component, BorderLayout.CENTER)
        contentArea.revalidate()
        contentArea.repaint()
        tabBar.repaint()
    }

    override fun dispose() {
        tabs.forEach { Disposer.dispose(it.panel) }
        tabs.clear()
    }

    private data class TabEntry(val name: String, val panel: ChatPanel)


    // --- 커스텀 탭 바 ---
    private inner class TabBar : JPanel() {
        private val tabHeight = 32
        private val tabPadding = 12
        private val closeSize = 14
        private val plusSize = 24
        private val gap = 2

        init {
            preferredSize = Dimension(0, tabHeight)
            isOpaque = true
            background = JBColor(Color(0xF0, 0xF0, 0xF0), Color(0x2B, 0x2B, 0x2B))

            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    handleClick(e.x, e.y)
                }
            })
        }

        private fun handleClick(mx: Int, my: Int) {
            var x = gap
            val fm = getFontMetrics(font)

            for (i in tabs.indices) {
                val textWidth = fm.stringWidth(tabs[i].name)
                val showClose = tabs.size > 1
                val tabWidth = tabPadding + textWidth + (if (showClose) closeSize + 8 else 0) + tabPadding
                val tabRect = Rectangle(x, 0, tabWidth, tabHeight)

                if (tabRect.contains(mx, my)) {
                    // 닫기 버튼 영역 체크
                    if (showClose) {
                        val closeX = x + tabWidth - tabPadding - closeSize
                        val closeY = (tabHeight - closeSize) / 2
                        val closeRect = Rectangle(closeX, closeY, closeSize, closeSize)
                        if (closeRect.contains(mx, my)) {
                            closeTab(i)
                            return
                        }
                    }
                    selectTab(i)
                    return
                }
                x += tabWidth + gap
            }

            // + 버튼 영역
            val plusRect = Rectangle(x + 4, (tabHeight - plusSize) / 2, plusSize, plusSize)
            if (plusRect.contains(mx, my)) {
                addNewTab()
            }
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val fm = g2.fontMetrics

            var x = gap

            for (i in tabs.indices) {
                val tab = tabs[i]
                val textWidth = fm.stringWidth(tab.name)
                val showClose = tabs.size > 1
                val tabWidth = tabPadding + textWidth + (if (showClose) closeSize + 8 else 0) + tabPadding
                val isActive = i == activeIndex

                // 탭 배경
                if (isActive) {
                    g2.color = JBColor(Color.WHITE, Color(0x3C, 0x3C, 0x3C))
                } else {
                    g2.color = JBColor(Color(0xE8, 0xE8, 0xE8), Color(0x2B, 0x2B, 0x2B))
                }
                g2.fillRoundRect(x, 2, tabWidth, tabHeight - 2, 8, 8)

                // 탭 텍스트
                g2.color = if (isActive) {
                    JBColor(Color(0x1E, 0x1E, 0x1E), Color(0xE8, 0xE8, 0xE8))
                } else {
                    JBColor(Color(0x80, 0x80, 0x80), Color(0x80, 0x80, 0x80))
                }
                g2.drawString(tab.name, x + tabPadding, (tabHeight + fm.ascent - fm.descent) / 2)

                // × 닫기 아이콘
                if (showClose) {
                    val closeX = x + tabWidth - tabPadding - closeSize
                    val closeY = (tabHeight - closeSize) / 2
                    g2.color = JBColor(Color(0xA0, 0xA0, 0xA0), Color(0x70, 0x70, 0x70))
                    g2.stroke = BasicStroke(1.5f)
                    val m = 3 // margin inside close box
                    g2.drawLine(closeX + m, closeY + m, closeX + closeSize - m, closeY + closeSize - m)
                    g2.drawLine(closeX + closeSize - m, closeY + m, closeX + m, closeY + closeSize - m)
                }

                x += tabWidth + gap
            }

            // + 버튼
            val plusX = x + 4
            val plusY = (tabHeight - plusSize) / 2
            g2.color = JBColor(Color(0xD0, 0xD0, 0xD0), Color(0x3C, 0x3C, 0x3C))
            g2.fillRoundRect(plusX, plusY, plusSize, plusSize, 6, 6)
            g2.color = JBColor(Color(0x60, 0x60, 0x60), Color(0x90, 0x90, 0x90))
            g2.stroke = BasicStroke(1.5f)
            val cx = plusX + plusSize / 2
            val cy = plusY + plusSize / 2
            val arm = 5
            g2.drawLine(cx - arm, cy, cx + arm, cy)
            g2.drawLine(cx, cy - arm, cx, cy + arm)

            g2.dispose()
        }
    }
}
