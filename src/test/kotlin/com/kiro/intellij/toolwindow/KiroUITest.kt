package com.kiro.intellij.toolwindow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * KiroUI 유틸리티 함수 테스트.
 * JBColor, AllIcons 등 IDE 의존 부분은 제외하고,
 * 순수 로직 함수들을 검증한다.
 */
class KiroUITest {

    // ========== Spacing 로직 테스트 ==========

    @Test
    fun `spacing values should be positive`() {
        // JBUI.scale()은 IDE 밖에서도 동작 (기본 scale = 1)
        assertTrue(KiroUI.Spacing.small > 0)
        assertTrue(KiroUI.Spacing.medium > 0)
        assertTrue(KiroUI.Spacing.large > 0)
        assertTrue(KiroUI.Spacing.xlarge > 0)
        assertTrue(KiroUI.Spacing.xxlarge > 0)
    }

    @Test
    fun `spacing values should be in ascending order`() {
        assertTrue(KiroUI.Spacing.small <= KiroUI.Spacing.medium)
        assertTrue(KiroUI.Spacing.medium <= KiroUI.Spacing.large)
        assertTrue(KiroUI.Spacing.large <= KiroUI.Spacing.xlarge)
        assertTrue(KiroUI.Spacing.xlarge <= KiroUI.Spacing.xxlarge)
    }

    // ========== Dimension 유틸리티 테스트 ==========

    @Test
    fun `scaledDimension should return positive dimensions`() {
        val dim = KiroUI.scaledDimension(100, 50)
        assertTrue(dim.width > 0)
        assertTrue(dim.height > 0)
    }

    @Test
    fun `maxHeightDimension should have MAX_VALUE width`() {
        val dim = KiroUI.maxHeightDimension(100)
        assertEquals(Int.MAX_VALUE, dim.width)
        assertTrue(dim.height > 0)
    }

    // ========== Icons 구조 테스트 ==========

    @Test
    fun `all navigation icons should be non-null`() {
        assertNotNull(KiroUI.Icons.auth)
        assertNotNull(KiroUI.Icons.settings)
        assertNotNull(KiroUI.Icons.mcp)
        assertNotNull(KiroUI.Icons.skills)
        assertNotNull(KiroUI.Icons.agent)
    }

    @Test
    fun `status icons should be non-null`() {
        assertNotNull(KiroUI.Icons.statusOk)
        assertNotNull(KiroUI.Icons.statusError)
        assertNotNull(KiroUI.Icons.statusRunning)
    }

    @Test
    fun `action icons should be non-null`() {
        assertNotNull(KiroUI.Icons.refresh)
        assertNotNull(KiroUI.Icons.add)
        assertNotNull(KiroUI.Icons.remove)
        assertNotNull(KiroUI.Icons.edit)
    }

    @Test
    fun `misc icons should be non-null`() {
        assertNotNull(KiroUI.Icons.info)
        assertNotNull(KiroUI.Icons.folder)
        assertNotNull(KiroUI.Icons.web)
        assertNotNull(KiroUI.Icons.user)
    }

    // ========== getStatusIcon 테스트 ==========

    @Test
    fun `getStatusIcon active should return statusOk`() {
        val icon = KiroUI.getStatusIcon(true)
        assertEquals(KiroUI.Icons.statusOk, icon)
    }

    @Test
    fun `getStatusIcon inactive should return statusError`() {
        val icon = KiroUI.getStatusIcon(false)
        assertEquals(KiroUI.Icons.statusError, icon)
    }

    // ========== createCard 테스트 ==========

    @Test
    fun `createCard should return non-null panel`() {
        val card = KiroUI.createCard()
        assertNotNull(card)
    }

    @Test
    fun `createCard should have border`() {
        val card = KiroUI.createCard()
        assertNotNull(card.border)
    }

    // ========== createCardHeader 테스트 ==========

    @Test
    fun `createCardHeader should return non-null panel`() {
        val header = KiroUI.createCardHeader("Test Title")
        assertNotNull(header)
    }

    @Test
    fun `createCardHeader with icon should not throw`() {
        assertDoesNotThrow {
            KiroUI.createCardHeader("Title", KiroUI.Icons.settings)
        }
    }

    // ========== createStatusLabel 테스트 ==========

    @Test
    fun `createStatusLabel active should show active text`() {
        val label = KiroUI.createStatusLabel(true, "Running", "Stopped")
        assertEquals("Running", label.text)
    }

    @Test
    fun `createStatusLabel inactive should show inactive text`() {
        val label = KiroUI.createStatusLabel(false, "Running", "Stopped")
        assertEquals("Stopped", label.text)
    }
}
