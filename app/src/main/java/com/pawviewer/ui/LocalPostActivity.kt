package com.pawviewer.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.pawviewer.R
import com.pawviewer.core.Util
import com.pawviewer.core.db.DownloadRow
import com.pawviewer.databinding.ActivityLocalPostBinding
import com.pawviewer.databinding.ItemAttachmentBinding
import java.io.File

/**
 * 已下载帖子的「本地详情页」。
 * 先看详情（封面 / 标题 / 文件清单），再从这里进阅读器或播放器，
 * 和在线帖子详情页的操作路径保持一致。
 */
class LocalPostActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLocalPostBinding
    private var media: List<File> = emptyList()
    private var title: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLocalPostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val dirPath = intent.getStringExtra(EXTRA_DIR).orEmpty()
        title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val dir = File(dirPath)
        media = dir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") && isMedia(it.name) }
            ?.sortedBy { it.name }
            ?: emptyList()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val meta = readMeta(dir)
        if (title.isBlank()) title = meta["title"] ?: dir.name

        binding.toolbar.title = title
        binding.toolbar.setNavigationOnClickListener { finish() }

        bindHeader(meta)
        bindActions()
        bindFiles()

        binding.content.state.hideState()
    }

    private fun bindHeader(meta: Map<String, String>) {
        binding.content.title.text = title
        val service = meta["service"].orEmpty()
        val creator = meta["creator"].orEmpty()
        binding.content.meta.text = buildString {
            if (service.isNotBlank()) append(Util.serviceName(service))
            if (creator.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(creator)
            }
            if (isNotEmpty()) append(" · ")
            append("本地")
        }
        binding.content.stats.text = getString(R.string.attachments_count, media.size)

        val cover = media.firstOrNull { isImage(it.name) }
        if (cover != null) {
            runCatching { BitmapFactory.decodeFile(cover.absolutePath) }.getOrNull()?.let {
                binding.content.cover.setImageBitmap(it)
            }
        } else {
            binding.content.cover.setImageResource(R.drawable.ic_image_off)
        }

        val webUrl = meta["url"]
        binding.content.content.text = buildString {
            append(getString(R.string.local_post_hint))
            if (!webUrl.isNullOrBlank()) {
                append("\n\n")
                append(webUrl)
            }
        }
    }

    private fun bindActions() {
        val images = media.filter { isImage(it.name) }
        val videos = media.filter { isVideo(it.name) }

        if (images.isEmpty()) {
            binding.content.readButton.visibility = View.GONE
        } else {
            binding.content.readButton.visibility = View.VISIBLE
            binding.content.readButton.text = "${getString(R.string.read)} (${images.size})"
            binding.content.readButton.setOnClickListener {
                LocalGalleryActivity.start(this, File(intent.getStringExtra(EXTRA_DIR).orEmpty()).absolutePath, title)
            }
        }

        if (videos.isEmpty()) {
            binding.content.downloadButton.visibility = View.GONE
        } else {
            binding.content.downloadButton.visibility = View.VISIBLE
            binding.content.downloadButton.text = getString(R.string.play)
            binding.content.downloadButton.setOnClickListener {
                if (videos.size == 1) {
                    PlayerActivity.startLocal(this, videos.first(), videos.first().name)
                } else {
                    LocalGalleryActivity.start(
                        this,
                        File(intent.getStringExtra(EXTRA_DIR).orEmpty()).absolutePath,
                        title,
                    )
                }
            }
        }
    }

    private fun bindFiles() {
        binding.content.attachmentContainer.removeAllViews()
        if (media.isEmpty()) {
            binding.content.attachmentTitle.visibility = View.GONE
            return
        }
        binding.content.attachmentTitle.visibility = View.VISIBLE
        binding.content.attachmentTitle.text = getString(R.string.attachments_count, media.size)

        media.forEach { file ->
            val row = ItemAttachmentBinding.inflate(layoutInflater, binding.content.attachmentContainer, false)
            row.name.text = file.name
            row.ext.text = file.name.substringAfterLast('.', "").uppercase().take(4).ifBlank { "?" }
            row.kind.text = when {
                isImage(file.name) -> getString(R.string.kind_image)
                isVideo(file.name) -> getString(R.string.kind_video)
                else -> getString(R.string.kind_file)
            }
            row.root.setOnClickListener {
                when {
                    isVideo(file.name) -> PlayerActivity.startLocal(this, file, file.name)
                    isImage(file.name) -> LocalGalleryActivity.start(
                        this,
                        File(intent.getStringExtra(EXTRA_DIR).orEmpty()).absolutePath,
                        title,
                    )
                }
            }
            binding.content.attachmentContainer.addView(row.root)
        }
    }

    private fun readMeta(dir: File): Map<String, String> {
        val file = File(dir, "_meta.txt")
        if (!file.exists()) return emptyMap()
        return runCatching {
            file.readLines()
                .mapNotNull { line ->
                    val i = line.indexOf(':')
                    if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
                }
                .toMap()
        }.getOrDefault(emptyMap())
    }

    private fun isImage(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in
            setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif", "jfif")

    private fun isVideo(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in setOf("mp4", "webm", "mov", "m4v", "mkv")

    private fun isMedia(name: String): Boolean = isImage(name) || isVideo(name)

    companion object {
        private const val EXTRA_DIR = "dir"
        private const val EXTRA_TITLE = "title"

        fun start(context: Context, dir: String, title: String) {
            context.startActivity(
                Intent(context, LocalPostActivity::class.java)
                    .putExtra(EXTRA_DIR, dir)
                    .putExtra(EXTRA_TITLE, title)
            )
        }

        fun start(context: Context, row: DownloadRow) = start(context, row.dir, row.title)
    }
}
