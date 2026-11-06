package com.pawviewer.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.pawviewer.R
import com.pawviewer.core.Prefs
import com.pawviewer.core.Util
import com.pawviewer.databinding.ActivityLocalGalleryBinding
import com.pawviewer.ui.widget.ZoomImageView
import java.io.File

/** 打开本地已下载的帖子目录：图片翻页看，视频点一下用内置播放器播。 */
class LocalGalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLocalGalleryBinding
    private var files: List<File> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLocalGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val dir = intent.getStringExtra(EXTRA_DIR).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val dirFile = File(dir)
        files = dirFile.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") && isMedia(it.name) }
            ?.sortedBy { it.name }
            ?: emptyList()

        if (files.isEmpty()) {
            Util.toast(this, getString(R.string.downloads_empty_files))
            finish()
            return
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.topBar.setPadding(
                Util.dp(this, 4),
                bars.top + Util.dp(this, 4),
                Util.dp(this, 4),
                Util.dp(this, 4),
            )
            insets
        }

        binding.title.text = title.ifBlank { dirFile.name }
        binding.btnBack.setOnClickListener { finish() }

        val adapter = LocalPageAdapter(files) { file ->
            PlayerActivity.startLocal(this, file, file.name)
        }
        binding.pager.adapter = adapter
        binding.pager.offscreenPageLimit = 1
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.pageText.text = getString(R.string.reader_page_of, position + 1, files.size)
            }
        })
        binding.pageText.text = getString(R.string.reader_page_of, 1, files.size)
    }

    private fun isMedia(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif", "jfif", "mp4", "webm", "mov", "m4v", "mkv")
    }

    /**
     * 音量键 / 翻页键翻页，行为与在线阅读器一致。
     * 关掉「音量键翻页」开关后不再拦截，音量键恢复成调音量。
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!Prefs.volumeKeys) return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                turnPage(1)
                return true
            }

            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_LEFT -> {
                turnPage(-1)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (Prefs.volumeKeys &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)
        ) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun turnPage(delta: Int) {
        if (files.isEmpty() || !::binding.isInitialized) return
        val current = binding.pager.currentItem
        val target = (current + delta).coerceIn(0, files.size - 1)
        if (target != current) binding.pager.setCurrentItem(target, true)
    }

    companion object {
        private const val EXTRA_DIR = "dir"
        private const val EXTRA_TITLE = "title"

        fun start(context: Context, dir: String, title: String) {
            context.startActivity(
                Intent(context, LocalGalleryActivity::class.java)
                    .putExtra(EXTRA_DIR, dir)
                    .putExtra(EXTRA_TITLE, title)
            )
        }
    }
}

private class LocalPageAdapter(
    private val files: List<File>,
    private val onPlay: (File) -> Unit,
) : RecyclerView.Adapter<LocalPageAdapter.Holder>() {

    class Holder(val root: FrameLayout, val image: ZoomImageView) : RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context
        val root = FrameLayout(context)
        root.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        val image = ZoomImageView(context)
        root.addView(
            image,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            ),
        )
        return Holder(root, image)
    }

    override fun getItemCount(): Int = files.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val file = files[position]
        holder.image.resetZoom()
        holder.image.setImageDrawable(null)
        holder.image.visibility = View.VISIBLE
        // 清掉上一次复用的播放占位
        for (i in holder.root.childCount - 1 downTo 1) holder.root.removeViewAt(i)

        if (isVideo(file.name)) {
            holder.image.visibility = View.GONE
            holder.root.addView(buildVideoPlaceholder(holder.root, file))
            return
        }

        val bitmap = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
        if (bitmap != null) {
            holder.image.setImageBitmap(bitmap)
        } else {
            holder.image.setImageResource(R.drawable.ic_image_off)
        }
    }

    private fun buildVideoPlaceholder(root: FrameLayout, file: File): View {
        val context = root.context
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            isClickable = true
            setOnClickListener { onPlay(file) }
        }
        val icon = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_media_play)
            setColorFilter(Color.WHITE)
        }
        box.addView(icon, LinearLayout.LayoutParams(Util.dp(context, 72), Util.dp(context, 72)))
        box.addView(TextView(context).apply {
            text = context.getString(R.string.player_tap_to_play)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        box.addView(TextView(context).apply {
            text = file.name
            setTextColor(Color.parseColor("#B0B0B0"))
            gravity = Gravity.CENTER
        })
        return box
    }

    private fun isVideo(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in setOf("mp4", "webm", "mov", "m4v", "mkv")
}
