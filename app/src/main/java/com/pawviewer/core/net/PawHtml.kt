package com.pawviewer.core.net

import org.jsoup.Jsoup

/**
 * pawchive 的原图直链并不是静态地址：
 * 帖子网页里是 `https://t1.pawchive.pw/f/<id>/<name>?e=<过期时间>&s=<签名>`，
 * 而 API 只给相对 path。所以「阅读器」必须先抓一次帖子网页，把签名表解出来。
 */
object PawHtml {

    private val signedUrlRe =
        Regex("""https://t1\.pawchive\.pw/f/[A-Za-z0-9_\-]+/[^"'\s<>\\]+""")

    private val imgSrcRe =
        Regex("""<img[^>]*?\ssrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    private val expiryRe = Regex("""[?&]e=(\d+)""")

    data class Signed(val url: String, val expiresAt: Long)

    fun extractSigned(html: String): Map<String, Signed> {
        val out = LinkedHashMap<String, Signed>()
        for (match in signedUrlRe.findAll(html)) {
            val raw = match.value.replace("&amp;", "&")
            val name = raw.substringBefore('?').substringAfterLast('/')
            if (name.isBlank()) continue
            val expiry = expiryRe.find(raw)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            out.putIfAbsent(name, Signed(raw, expiry))
        }
        return out
    }

    /** 正文里的图片，统一折算成 `/xx/yy/<hash>.<ext>` 形式的相对路径。 */
    fun contentImagePaths(html: String): List<String> {
        if (html.isBlank()) return emptyList()
        val out = LinkedHashSet<String>()
        for (match in imgSrcRe.findAll(html)) {
            val src = match.groupValues[1].replace("&amp;", "&")
            val path = toDataPath(src) ?: continue
            out.add(path)
        }
        return out.toList()
    }

    fun toDataPath(src: String): String? {
        val marker = "/data/"
        val index = src.indexOf(marker)
        if (index < 0) return null
        var path = src.substring(index + "/data".length)
        path = path.substringBefore('?').substringBefore('#')
        if (!path.startsWith("/")) return null
        if (path.length < 6) return null
        return path
    }

    fun plainText(html: String): String {
        if (html.isBlank()) return ""
        return runCatching { Jsoup.parse(html).text() }.getOrDefault("")
    }

    fun embedUrl(html: String): String? {
        if (html.isBlank()) return null
        val doc = runCatching { Jsoup.parse(html) }.getOrNull() ?: return null
        return doc.selectFirst("a[href]")?.attr("href")?.takeIf { it.startsWith("http") }
    }
}
