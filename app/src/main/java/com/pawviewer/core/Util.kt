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
}
