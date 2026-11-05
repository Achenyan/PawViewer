package com.pawviewer.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.Service
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.pawviewer.R
import com.pawviewer.core.db.DownloadRow
import com.pawviewer.core.db.DownloadState
import com.pawviewer.core.db.Store
import com.pawviewer.core.media.MediaKind
import com.pawviewer.core.media.MediaResolver
import com.pawviewer.core.model.Post
import com.pawviewer.core.net.DownloadCancelled
import com.pawviewer.core.net.Http
import com.pawviewer.core.net.Lane
import com.pawviewer.core.net.PawApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections

/**
 * 下载队列：严格串行（一个任务一个任务来，任务内文件按顺序）。
 *
 * 断点续传规则：
 *  - 图片：先写 `xxx.part`，下完再改名；中途取消/失败就删掉半张，下次重下。
 *  - 视频：直接写 `xxx.part` 并用 Range 续传，取消/失败都保留，下次接着下。
 *  - 已经存在的成品文件直接跳过，所以「取消后继续」天然从断点开始。
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // 上次进程被杀时留下的 RUNNING 任务，重启后当作待下载
        runCatching { Store.resetRunning(this) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (job?.isActive != true) {
            job = scope.launch {
                try {
                    processQueue()
                } finally {
                    job = null
                }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun processQueue() {
        while (true) {
            val row = Store.nextWaiting(this) ?: break
            if (cancelIds.contains(row.id)) {
                cancelIds.remove(row.id)
                continue
            }
            Store.updateDownload(this, row.id, state = DownloadState.RUNNING)
            val next = try {
                downloadOne(row)
                DownloadState.DONE
            } catch (e: DownloadCancelled) {
                DownloadState.CANCELED
            } catch (e: Exception) {
                DownloadState.FAILED
            }
            Store.updateDownload(this, row.id, state = next)
            cancelIds.remove(row.id)
        }
        notifyDone()
        stopForeground(true)
        stopSelf()
    }

    /** 返回下载失败的条目数（0 表示全部成功）。 */
    private suspend fun downloadOne(row: DownloadRow) {
        val post: Post = PawApi.post(row.service, row.uid, row.pid) ?: error("post not found")
        val media = MediaResolver.items(post).filter {
            it.isImage || it.kind == MediaKind.VIDEO
        }
        Store.updateDownload(this, row.id, total = media.size)

        val dir = File(row.dir).apply { mkdirs() }
        runCatching { File(dir, ".nomedia").createNewFile() }
        writeMeta(dir, post)

        var done = 0
        var failed = 0
        for (item in media) {
            if (cancelIds.contains(row.id)) throw DownloadCancelled()

            val isVideo = item.kind == MediaKind.VIDEO
            val extension = item.name.substringAfterLast('.', "bin")
            val fileName = Util.safeFileName(item.name, "media_${done + 1}.$extension")
            val finalFile = File(dir, fileName)
            val partFile = File(dir, "$fileName.part")

            // 成品已存在 → 直接跳过（断点续传的关键）
            if (finalFile.exists() && finalFile.length() > 0) {
                done++
                Store.updateDownload(this, row.id, done = done)
                continue
            }

            val url = runCatching { MediaResolver.fullUrl(post, item) }.getOrNull()
                ?: MediaResolver.thumbUrl(item.path)
            if (url.isNullOrBlank()) {
                failed++
                continue
            }

            try {
                Http.downloadToFile(
                    url = url,
                    target = partFile,
                    resume = isVideo,
                    onProgress = { written, total ->
                        updateProgress(row.title, done, media.size, written, total)
                        !cancelIds.contains(row.id)
                    },
                )
                if (finalFile.exists()) finalFile.delete()
                partFile.renameTo(finalFile)
                done++
                Store.updateDownload(this, row.id, done = done)
                updateProgress(row.title, done, media.size, 0, 0)
            } catch (e: DownloadCancelled) {
                // 图片不留半张；视频保留 .part 供续传
                if (!isVideo) runCatching { partFile.delete() }
                throw e
            } catch (e: Exception) {
                if (!isVideo) runCatching { partFile.delete() }
                failed++
            }
        }
        if (failed > 0) throw IllegalStateException("$failed item(s) failed")
    }

    private fun writeMeta(dir: File, post: Post) {
        runCatching {
            File(dir, "_meta.txt").writeText(
                buildString {
                    appendLine("title: ${post.displayTitle}")
                    appendLine("service: ${post.service}")
                    appendLine("creator: ${post.user}")
                    appendLine("post: ${post.id}")
                    appendLine("url: ${post.webUrl}")
                }
            )
        }
    }

    // ---------------------------------------------------------- notification

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL, getString(R.string.download_channel), NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        manager.createNotificationChannel(channel)
    }

    private fun baseNotification(): NotificationCompat.Builder =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setColor(0xFF63A4A8.toInt())
            .setOnlyAlertOnce(true)
            .setOngoing(true)

    private fun startAsForeground() {
        val notification = baseNotification()
            .setContentTitle(getString(R.string.download_channel))
            .setContentText(getString(R.string.loading))
            .setProgress(0, 0, true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateProgress(title: String, done: Int, total: Int, written: Long, bytes: Long) {
        val builder = baseNotification()
            .setContentTitle(title.take(40))
        if (bytes > 0) {
            val pct = (written * 100 / bytes).toInt().coerceIn(0, 100)
            builder.setContentText("${done + 1}/$total · $pct%")
                .setProgress(100, pct, false)
        } else {
            builder.setContentText(getString(R.string.downs_progress, done, total))
                .setProgress(total.coerceAtLeast(1), done, false)
        }
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, builder.build())
    }

    private fun notifyDone() {
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setColor(0xFF63A4A8.toInt())
            .setContentTitle(getString(R.string.download_finished))
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID + 1, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "paw_downloads"
        private const val NOTIFICATION_ID = 1001

        /** 正在被取消的任务 id（服务与界面同进程，直接共享）。 */
        private val cancelIds: MutableSet<Long> = Collections.synchronizedSet(HashSet<Long>())

        fun enqueue(context: Context, post: Post) {
            val root = context.getExternalFilesDir("downloads") ?: context.filesDir
            runCatching { File(root, ".nomedia").createNewFile() }
            val dir = File(root, "${post.service}_${post.user}/${post.id}")
            val id = Store.addDownload(context, post, dir.absolutePath)
            if (id <= 0) return
            kick(context)
        }

        fun kick(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        /** 取消 = 暂停：保留已完成文件和进度，之后可以继续。 */
        fun cancel(context: Context, id: Long) {
            cancelIds.add(id)
            Store.updateDownload(context, id, state = DownloadState.CANCELED)
        }

        /** 继续 / 重试。 */
        fun resume(context: Context, id: Long) {
            cancelIds.remove(id)
            Store.updateDownload(context, id, state = DownloadState.WAITING)
            kick(context)
        }
    }
}
