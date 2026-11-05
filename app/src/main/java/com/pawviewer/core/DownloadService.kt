package com.pawviewer.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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
import com.pawviewer.core.media.MediaResolver
import com.pawviewer.core.model.Post
import com.pawviewer.core.net.Http
import com.pawviewer.core.net.Lane
import com.pawviewer.core.net.PawApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/**
 * 单线程下载队列：严格按 1 请求/秒的顺序抓，服务在前台运行，
 * 下载的只是用户手动点过的那一个帖子里的图片。
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
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
            Store.updateDownload(this, row.id, state = DownloadState.RUNNING)
            val success = runCatching { downloadOne(row) }.isSuccess
            Store.updateDownload(
                this, row.id,
                state = if (success) DownloadState.DONE else DownloadState.FAILED
            )
        }
        notifyDone()
        stopForeground(true)
        stopSelf()
    }

    private suspend fun downloadOne(row: DownloadRow) {
        val post: Post = PawApi.post(row.service, row.uid, row.pid) ?: error("post not found")
        val images = MediaResolver.images(post)
        Store.updateDownload(this, row.id, total = images.size)

        val dir = File(row.dir).apply { mkdirs() }
        runCatching { File(dir, ".nomedia").createNewFile() }
        var done = 0
        for (item in images) {
            val url = MediaResolver.bestImageUrl(post, item) ?: continue
            val bytes = Http.fetchMedia(url, Lane.FULL)
            val extension = item.name.substringAfterLast('.', "jpg")
            val fileName = Util.safeFileName(item.name, "image_${done + 1}.$extension")
            File(dir, fileName).writeBytes(bytes)
            done++
            Store.updateDownload(this, row.id, done = done)
            updateProgress(row.title, done, images.size)
        }
        File(dir, "_meta.txt").writeText(
            buildString {
                appendLine("title: ${post.displayTitle}")
                appendLine("service: ${post.service}")
                appendLine("creator: ${post.user}")
                appendLine("post: ${post.id}")
                appendLine("url: ${post.webUrl}")
                appendLine("images: $done")
            }
        )
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
            .setColor(0xFFDB8004.toInt())
            .setOnlyAlertOnce(true)
            .setOngoing(true)

    private fun startAsForeground() {
        val notification = baseNotification()
            .setContentTitle(getString(R.string.download_channel))
            .setContentText(getString(R.string.loading))
            .setProgress(0, 0, true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateProgress(title: String, done: Int, total: Int) {
        val notification = baseNotification()
            .setContentTitle(title.take(40))
            .setContentText(getString(R.string.downs_progress, done, total))
            .setProgress(total.coerceAtLeast(1), done, false)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun notifyDone() {
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setColor(0xFFDB8004.toInt())
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

        fun enqueue(context: Context, post: Post) {
            val root = context.getExternalFilesDir("downloads") ?: context.filesDir
            // 让相册/媒体扫描器跳过整个下载目录
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
    }
}
