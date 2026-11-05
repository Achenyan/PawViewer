package com.pawviewer.core.img

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.widget.ImageView
import com.pawviewer.core.Util
import com.pawviewer.core.net.Http
import com.pawviewer.core.net.Lane
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 自带的图片加载器（不引第三方库）：
 * 内存 LruCache + 磁盘缓存 + 按目标尺寸降采样解码 + 请求去重。
 * 磁盘缓存以「逻辑 key」命名，所以签名直链过期后仍然命中本地缓存。
 */
object ImageLoader {

    private lateinit var appContext: Context
    private lateinit var diskDir: File
    private lateinit var memory: LruCache<String, Bitmap>

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        appContext = context.applicationContext
        diskDir = File(appContext.cacheDir, "images").apply { mkdirs() }
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024L / 6L).toInt().coerceAtLeast(8 * 1024)
        memory = object : LruCache<String, Bitmap>(maxKb) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }
    }

    fun load(
        url: String?,
        cacheKey: String,
        view: ImageView,
        reqWidth: Int,
        reqHeight: Int,
        lane: Lane = Lane.THUMB,
        placeholder: Drawable? = null,
        onFail: (() -> Unit)? = null,
    ) {
        view.tag = cacheKey
        val cached = memory.get(cacheKey)
        if (cached != null) {
            view.setImageBitmap(cached)
            return
        }
        view.setImageDrawable(placeholder)
        scope.launch {
            val bitmap = loadBitmap(url, cacheKey, reqWidth, reqHeight, lane)
            withContext(Dispatchers.Main) {
                if (view.tag != cacheKey) return@withContext
                if (bitmap != null) view.setImageBitmap(bitmap) else onFail?.invoke()
            }
        }
    }

    suspend fun loadBitmap(
        url: String?,
        cacheKey: String,
        reqWidth: Int,
        reqHeight: Int,
        lane: Lane = Lane.THUMB,
        forceNetwork: Boolean = false,
    ): Bitmap? = withContext(Dispatchers.IO) {
        memory.get(cacheKey)?.let { return@withContext it }

        val diskFile = File(diskDir, Util.sha1(cacheKey))
        val bytes: ByteArray? = when {
            !forceNetwork && diskFile.exists() && diskFile.length() > 0 -> {
                runCatching { diskFile.readBytes() }.getOrNull()
            }
            url == null -> null
            else -> runCatching { Http.fetchMedia(url, lane) }.getOrNull()?.also { data ->
                runCatching { diskFile.writeBytes(data) }
            }
        }

        val bitmap = bytes?.let { decode(it, reqWidth, reqHeight) }
        if (bitmap != null) memory.put(cacheKey, bitmap)
        bitmap
    }

    private fun decode(bytes: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        val targetW = reqWidth.coerceAtLeast(1)
        val targetH = reqHeight.coerceAtLeast(1)
        while (bounds.outWidth / (sample * 2) >= targetW && bounds.outHeight / (sample * 2) >= targetH) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }.getOrNull()
    }

    fun prefetch(url: String?, cacheKey: String, lane: Lane = Lane.FULL) {
        if (url == null) return
        if (memory.get(cacheKey) != null) return
        scope.launch { loadBitmap(url, cacheKey, 1080, 1920, lane) }
    }

    fun clearDisk(): Long {
        memory.evictAll()
        var size = 0L
        diskDir.listFiles()?.forEach { file ->
            size += file.length()
            file.delete()
        }
        return size
    }

    fun diskUsage(): Long = diskDir.listFiles()?.sumOf { it.length() } ?: 0L
}
