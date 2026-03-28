package com.kiro.intellij.util

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 스레드 안전한 디바운서.
 * 같은 키의 호출이 반복되면 마지막 호출만 실행한다.
 */
class Debouncer(private val delayMs: Long) {

    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "kiro-debouncer").apply { isDaemon = true }
    }
    private val pending = AtomicReference<ScheduledFuture<*>>(null)

    fun debounce(action: () -> Unit) {
        pending.getAndSet(
            scheduler.schedule(action, delayMs, TimeUnit.MILLISECONDS)
        )?.cancel(false)
    }

    fun shutdown() {
        pending.get()?.cancel(false)
        scheduler.shutdown()
    }
}

/**
 * 만료 캐시. TTL 이내에 같은 키로 조회하면 캐시된 값을 반환한다.
 */
class ExpiringCache<K, V>(private val ttlMs: Long) {

    private data class Entry<V>(val value: V, val expiresAt: Long)

    private val cache = java.util.concurrent.ConcurrentHashMap<K, Entry<V>>()

    fun get(key: K): V? {
        val entry = cache[key] ?: return null
        return if (System.currentTimeMillis() < entry.expiresAt) entry.value else {
            cache.remove(key)
            null
        }
    }

    fun put(key: K, value: V) {
        cache[key] = Entry(value, System.currentTimeMillis() + ttlMs)
    }

    fun invalidate(key: K) {
        cache.remove(key)
    }

    fun invalidateAll() {
        cache.clear()
    }

    fun size(): Int {
        evictExpired()
        return cache.size
    }

    private fun evictExpired() {
        val now = System.currentTimeMillis()
        cache.entries.removeIf { it.value.expiresAt <= now }
    }
}
