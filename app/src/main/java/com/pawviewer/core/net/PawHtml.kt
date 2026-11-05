package com.pawviewer.core.net

import org.jsoup.Jsoup

/**
 * pawchive 的媒体直链不是静态地址，都要从帖子网页里现取（带 e= 过期时间 + s= 签名）：
 *
 *  - 图片：`https://t1.pawchive.pw/f/<hash>/<name>?e=..&s=..`
 *  - 视频文件：`https://t1.pawchive.pw/d/<hash>/<name>?e=..&s=..`
 *  - 视频 HLS：`https://t1.pawchive.pw/v/<hash>/master.m3u8?e=..&s=..`
 *
 * 同一个视频的 `/d/` 与 `/v/` 用同一个 hash，可以据此配对。
 */
object PawHtml {

    private val mediaUrlRe = Regex(
        """https://t1\.pawchive\.pw/(f|d|v)/([A-Za-z0-9_\-]+)/([^"'\s<>\\?]+)(\?[^"'\s<>\\]*)?"""
    )

    private val imgSrcRe =
        Regex("""<img[^>]*?\ssrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    private val expiryRe = Regex("""[?&]e=(\d+)""")

    data class Signed(val url: String, val expiresAt: Long, val hash: String = "")

    /** 按文件名索引的直链（f/d），以及按 hash 索引的 HLS 清单（v）。 */
    data class SignedTable(
        val byName: Map<String, Signed>,
        val hlsByHash: Map<String, Signed>,
    ) {
        fun urlFor(name: String): String? = byName[name]?.url

        /** 优先用 mp4 直链（带完整签名，最稳），没有才退回 HLS 清单。 */
        fun videoUrlFor(name: String): String? {
            val direct = byName[name] ?: return hlsByHash.values.firstOrNull()?.url
            return direct.url
        }
    }

    fun extractSigned(html: String): SignedTable {
        val byName = LinkedHashMap<String, Signed>()
        val hls = LinkedHashMap<String, Signed>()
        for (match in mediaUrlRe.findAll(html)) {
            val kind = match.groupValues[1]
            val hash = match.groupValues[2]
            val name = match.groupValues[3]
            val raw = match.value.replace("&amp;", "&")
            if (name.isBlank()) continue
            val expiry = expiryRe.find(raw)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            if (kind == "v" || name.endsWith(".m3u8", true)) {
                hls.putIfAbsent(hash, Signed(raw, expiry, hash))
            } else {
                byName.putIfAbsent(name, Signed(raw, expiry, hash))
            }
        }
        return SignedTable(byName, hls)
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
