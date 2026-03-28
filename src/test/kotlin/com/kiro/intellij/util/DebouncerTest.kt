package com.kiro.intellij.util

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DebouncerTest {

    private var debouncer: Debouncer? = null

    @AfterEach
    fun tearDown() {
        debouncer?.shutdown()
    }

    @Test
    fun `debounce should execute action after delay`() {
        debouncer = Debouncer(100)
        val latch = CountDownLatch(1)
        val counter = AtomicInteger(0)

        debouncer!!.debounce {
            counter.incrementAndGet()
            latch.countDown()
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertEquals(1, counter.get())
    }

    @Test
    fun `rapid calls should only execute last action`() {
        debouncer = Debouncer(200)
        val latch = CountDownLatch(1)
        val counter = AtomicInteger(0)

        repeat(10) { i ->
            debouncer!!.debounce {
                counter.incrementAndGet()
                if (i == 9) latch.countDown()
            }
            Thread.sleep(20)
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        // Only the last call should execute (or at most a few if timing is tight)
        assertTrue(counter.get() <= 2, "Expected at most 2 executions but got ${counter.get()}")
    }

    @Test
    fun `shutdown should stop pending actions`() {
        debouncer = Debouncer(500)
        val counter = AtomicInteger(0)

        debouncer!!.debounce { counter.incrementAndGet() }
        debouncer!!.shutdown()

        Thread.sleep(700)
        assertEquals(0, counter.get())
    }
}

class ExpiringCacheTest {

    @Test
    fun `put and get should work within TTL`() {
        val cache = ExpiringCache<String, Int>(5000)
        cache.put("a", 42)
        assertEquals(42, cache.get("a"))
    }

    @Test
    fun `get should return null for missing key`() {
        val cache = ExpiringCache<String, Int>(5000)
        assertNull(cache.get("missing"))
    }

    @Test
    fun `get should return null after TTL expires`() {
        val cache = ExpiringCache<String, Int>(50)
        cache.put("a", 42)
        Thread.sleep(100)
        assertNull(cache.get("a"))
    }

    @Test
    fun `invalidate should remove specific key`() {
        val cache = ExpiringCache<String, Int>(5000)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.invalidate("a")
        assertNull(cache.get("a"))
        assertEquals(2, cache.get("b"))
    }

    @Test
    fun `invalidateAll should clear everything`() {
        val cache = ExpiringCache<String, Int>(5000)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.invalidateAll()
        assertNull(cache.get("a"))
        assertNull(cache.get("b"))
    }

    @Test
    fun `size should exclude expired entries`() {
        val cache = ExpiringCache<String, Int>(50)
        cache.put("a", 1)
        cache.put("b", 2)
        assertEquals(2, cache.size())
        Thread.sleep(100)
        assertEquals(0, cache.size())
    }

    @Test
    fun `put should overwrite existing value`() {
        val cache = ExpiringCache<String, Int>(5000)
        cache.put("a", 1)
        cache.put("a", 99)
        assertEquals(99, cache.get("a"))
    }

    @Test
    fun `concurrent access should not throw`() {
        val cache = ExpiringCache<Int, Int>(1000)
        val threads = (0 until 10).map { t ->
            Thread {
                repeat(100) { i ->
                    cache.put(t * 100 + i, i)
                    cache.get(t * 100 + i)
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertTrue(cache.size() > 0)
    }
}
