package com.pawviewer.core

import android.content.Context
import android.widget.Toast
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Util {

    private val outFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun serviceName(service: String?): String = when (service?.lowercase()) {
        "patreon" -> "Patreon"
        "fanbox" -> "Fanbox"
        "discord" -> "Discord"
        "gumroad" -> "Gumroad"
        "subscribestar" -> "SubscribeStar"
        "fantia" -> "Fantia"
        "boosty" -> "Boosty"
        "dlsite" -> "DLsite"
        else -> service?.replaceFirstChar { it.uppercase() } ?: "未知"
    }

    fun formatDate(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        // 既可能是 ISO 字符串，也可能是 epoch 秒
        raw.toLongOrNull()?.let { sec ->
            return Instant.ofEpochSecond(sec).atZone(ZoneId.systemDefault()).format(outFmt)
        }
        return runCatching {
            LocalDateTime.parse(raw).format(outFmt)
        }.getOrElse {
            runCatching {
                Instant.parse(raw).atZone(ZoneId.systemDefault()).format(outFmt)
            }.getOrDefault(raw.take(16).replace('T', ' '))
        }
    }

    fun humanSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var i = 0
        while (value >= 1024 && i < units.lastIndex) {
            value /= 1024
            i++
        }
        return if (i == 0) "${bytes} B" else String.format("%.1f %s", value, units[i])
    }

    fun sha1(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** 站点给的文件名不可信，落盘前先洗一遍。 */
    fun safeFileName(name: String?, fallback: String): String {
        val cleaned = name.orEmpty()
            .map { ch ->
                if (ch in "\\/:*?\"<>|" || ch.isISOControl()) '_' else ch
            }
            .joinToString("")
            .trim()
            .trim('.')
        return cleaned.ifBlank { fallback }
    }

    fun toast(context: Context, message: CharSequence?) {
        if (message.isNullOrBlank()) return
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    /**
     * 统一的网络错误文案：网络类失败统一提示「请尝试开启代理解决此问题」；
     * 4xx（未登录/无权限/不存在）保留服务端说法，避免误导。
     */
    fun netMessage(context: Context, error: Throwable?): String {
        val code = (error as? com.pawviewer.core.net.HttpException)?.code ?: 0
        if (code in 400..499 && code != 408 && code != 429) {
            return error?.message ?: context.getString(com.pawviewer.R.string.error_network)
        }
        return context.getString(com.pawviewer.R.string.error_network_hint)
    }
    /** Levenshtein 编辑距离。 */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    /**
     * 模糊匹配打分：0 = 直接包含；1 = 差一个字母（可容忍一处输错）；2 = 差两个字母（长关键词）；
     * null = 不匹配。用于「aiex 也能搜到 alex，但排在后面」。
     */
    fun fuzzyScore(title: String, query: String): Int? {
        val t = title.lowercase()
        val q = query.trim().lowercase()
        if (q.isEmpty()) return 0
        if (t.contains(q)) return 0
        if (q.length < 3) return null

        var best = Int.MAX_VALUE
        val words = t.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        for (w in words) {
            best = minOf(best, levenshtein(w, q))
            if (w.length > q.length) best = minOf(best, levenshtein(w.take(q.length), q))
        }
        if (t.length >= q.length) {
            var i = 0
            while (i <= t.length - q.length) {
                best = minOf(best, levenshtein(t.substring(i, i + q.length), q))
                i++
            }
        }
        return when {
            best <= 1 -> 1
            best <= 2 && q.length >= 5 -> 2
            else -> null
        }
    }

    /** 把接口里的时间字段解析成毫秒时间戳。 */
    fun epochOf(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        raw.toLongOrNull()?.let { sec ->
            return if (sec > 100_000_000_000L) sec else sec * 1000L
        }
        runCatching {
            return java.time.LocalDateTime.parse(raw)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        runCatching {
            return java.time.Instant.parse(raw).toEpochMilli()
        }
        return null
    }
    private val VIDEO_EXT = setOf("mp4", "webm", "mov", "m4v", "mkv")

    fun isVideoName(name: String?): Boolean {
        val ext = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return ext.isNotEmpty() && VIDEO_EXT.contains(ext)
    }

    /** 帖子是否含视频（有视频就算视频类，否则算图包）。 */
    fun isVideoPost(post: com.pawviewer.core.model.Post): Boolean {
        if (isVideoName(post.file?.name)) return true
        return post.attachmentsOrEmpty.any { isVideoName(it.name) }
    }
    private val AI_RE = Regex("(?<![A-Za-z])AI(?![A-Za-z])")

    /** 标题或正文里是否带独立的大写 AI 标记。 */
    fun hasAiTag(post: com.pawviewer.core.model.Post): Boolean {
        if (!post.title.isNullOrBlank() && AI_RE.containsMatchIn(post.title!!)) return true
        val content = post.content.orEmpty()
        return content.isNotEmpty() && AI_RE.containsMatchIn(content)
    }
}
