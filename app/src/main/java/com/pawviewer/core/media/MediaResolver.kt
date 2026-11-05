package com.pawviewer.core.media

import com.pawviewer.core.model.Post
import com.pawviewer.core.net.PawApi
import com.pawviewer.core.net.PawHtml
import java.util.concurrent.ConcurrentHashMap

enum class MediaKind { IMAGE, VIDEO, ARCHIVE, DOC, OTHER }

data class MediaItem(
    val key: String,
    val name: String,
    val kind: MediaKind,
    val path: String?,
    val thumbUrl: String?,
    val directUrl: String?,
    val signedName: String?,
) {
    val isImage: Boolean get() = kind == MediaKind.IMAGE
}

object MediaResolver {

    private const val FILE_HOST = "https://file.pawchive.pw/data"
    private const val THUMB_HOST = "https://img.pawchive.pw/thumbnail/data"

    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif", "jfif")
    private val VIDEO_EXT = setOf("mp4", "webm", "mov", "m4v", "mkv")
    private val ARCHIVE_EXT = setOf("zip", "7z", "rar", "tar", "gz")

    private data class Entry(val expiresAt: Long, val table: PawHtml.SignedTable)

    private val cache = ConcurrentHashMap<String, Entry>()

    fun kindOf(name: String?): MediaKind {
        val ext = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return when {
            IMAGE_EXT.contains(ext) -> MediaKind.IMAGE
            VIDEO_EXT.contains(ext) -> MediaKind.VIDEO
            ARCHIVE_EXT.contains(ext) -> MediaKind.ARCHIVE
            ext.isEmpty() -> MediaKind.OTHER
            else -> MediaKind.DOC
        }
    }

    fun thumbUrl(path: String?): String? = path?.let { THUMB_HOST + it }

    fun directUrl(path: String?): String? = path?.let { FILE_HOST + it }

    /** 一个帖子里所有可看的媒体，顺序：主文件 → 附件 → 正文内嵌图。 */
    fun items(post: Post): List<MediaItem> {
        val out = LinkedHashMap<String, MediaItem>()

        val file = post.file
        if (!file?.path.isNullOrBlank()) {
            val path = file!!.path!!
            val name = file.name?.takeIf { it.isNotBlank() } ?: path.substringAfterLast('/')
            out[path] = MediaItem(
                key = post.key + "#" + path,
                name = name,
                kind = kindOf(name),
                path = path,
                thumbUrl = thumbUrl(path),
                directUrl = if (file.previewOnly == true) null else directUrl(path),
                signedName = name,
            )
        }

        post.attachmentsOrEmpty.forEach { attachment ->
            val path = attachment.path
            val name = attachment.name?.takeIf { it.isNotBlank() }
                ?: path?.substringAfterLast('/')
                ?: return@forEach
            val key = path ?: "signed:$name"
            if (out.containsKey(key)) return@forEach
            out[key] = MediaItem(
                key = post.key + "#" + key,
                name = name,
                kind = kindOf(name),
                path = path,
                thumbUrl = thumbUrl(path),
                directUrl = null,
                signedName = name,
            )
        }

        PawHtml.contentImagePaths(post.content.orEmpty()).forEach { path ->
            if (out.containsKey(path)) return@forEach
            val name = path.substringAfterLast('/')
            out[path] = MediaItem(
                key = post.key + "#" + path,
                name = name,
                kind = MediaKind.IMAGE,
                path = path,
                thumbUrl = thumbUrl(path),
                directUrl = null,
                signedName = name,
            )
        }

        return out.values.toList()
    }

    fun images(post: Post): List<MediaItem> = items(post).filter { it.isImage }

    /** 帖子网页里的签名表，带过期时间，过期或 403 时强制刷新。 */
    suspend fun signedTable(post: Post, force: Boolean = false): PawHtml.SignedTable {
        val now = System.currentTimeMillis()
        val cached = cache[post.key]
        if (!force && cached != null && cached.expiresAt > now) return cached.table

        val html = PawApi.postPageHtml(post.service.orEmpty(), post.id.orEmpty())
        val table = PawHtml.extractSigned(html)
        val all = table.byName.values + table.hlsByHash.values
        val minExpirySec = all.mapNotNull { it.expiresAt.takeIf { e -> e > 0 } }.minOrNull()
        val expiresAt = if (minExpirySec != null) minExpirySec * 1000L - 120_000L
        else now + 30 * 60_000L
        cache[post.key] = Entry(expiresAt, table)
        return table
    }

    suspend fun fullUrl(post: Post, item: MediaItem, force: Boolean = false): String? {
        item.directUrl?.let { return it }
        // 有 path 的附件优先走 file.pawchive.pw（Cloudflare，支持 HTTP/3），
        // 只有视频这种没有 path 的才回落到 t1 的签名直链。
        if (item.kind == MediaKind.IMAGE && !item.path.isNullOrBlank()) {
            return directUrl(item.path)
        }
        val name = item.signedName ?: item.path?.substringAfterLast('/') ?: return null
        val table = runCatching { signedTable(post, force) }.getOrNull() ?: return null
        return if (item.kind == MediaKind.VIDEO) table.videoUrlFor(name) else table.urlFor(name)
    }

    /** 阅读器用：优先原图，拿不到就退回缩略图，保证总有东西可看。 */
    suspend fun bestImageUrl(post: Post, item: MediaItem, force: Boolean = false): String? =
        fullUrl(post, item, force) ?: item.thumbUrl

    fun invalidate(postKey: String) {
        cache.remove(postKey)
    }
}
