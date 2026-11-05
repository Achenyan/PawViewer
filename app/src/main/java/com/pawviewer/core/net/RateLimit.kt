package com.pawviewer.core.net

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlin.math.min

/**
 * 令牌桶。站点明确要求「不超过 1 请求/秒、最多 5 个并发下载」，
 * 这里把限速做成全局基础设施，而不是散落在各处的 sleep。
 */
class RateGate(private val permitsPerSecond: Double, private val burst: Double = 1.0) {

    private var tokens: Double = burst
    private var lastNanos: Long = System.nanoTime()

    private fun reserveWaitNanos(): Long = synchronized(this) {
        val now = System.nanoTime()
        val elapsed = (now - lastNanos) / 1_000_000_000.0
        lastNanos = now
        tokens = min(burst, tokens + elapsed * permitsPerSecond)
        if (tokens >= 1.0) {
            tokens -= 1.0
            0L
        } else {
            ((1.0 - tokens) / permitsPerSecond * 1_000_000_000.0).toLong()
        }
    }

    suspend fun acquire() {
        while (true) {
            val wait = reserveWaitNanos()
            if (wait <= 0L) return
            delay(wait / 1_000_000L + 2L)
        }
    }
}

object Lanes {

    /** 接口与页面 HTML：严格 1 次/秒。 */
    @Volatile
    var api = RateGate(1.0, 1.0)

    /** 缩略图：小文件，允许稍快，但严格模式下同样降到 1 次/秒。 */
    @Volatile
    var thumbGate = RateGate(2.0, 2.0)

    /** 原图 / 附件：严格 1 次/秒。 */
    @Volatile
    var fullGate = RateGate(1.0, 1.0)

    @Volatile
    var thumbConcurrency = Semaphore(4)

    @Volatile
    var fullConcurrency = Semaphore(3)

    fun applyStrict(strict: Boolean, concurrency: Int) {
        api = RateGate(1.0, 1.0)
        if (strict) {
            thumbGate = RateGate(1.0, 1.0)
            fullGate = RateGate(1.0, 1.0)
            thumbConcurrency = Semaphore(2)
            fullConcurrency = Semaphore(2)
        } else {
            thumbGate = RateGate(3.0, 3.0)
            fullGate = RateGate(1.0, 1.0)
            thumbConcurrency = Semaphore(4)
            fullConcurrency = Semaphore(concurrency.coerceIn(1, 5))
        }
    }
}
