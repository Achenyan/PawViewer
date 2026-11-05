package com.pawviewer.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.pawviewer.R
import com.pawviewer.core.Prefs
import com.pawviewer.core.Util
import com.pawviewer.core.db.Store
import com.pawviewer.core.img.ImageLoader
import com.pawviewer.core.media.MediaItem
import com.pawviewer.core.media.MediaResolver
import com.pawviewer.core.model.Post
import com.pawviewer.core.net.Http
import com.pawviewer.core.net.Lane
import com.pawviewer.databinding.ActivityReaderBinding
import com.pawviewer.ui.widget.ZoomImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object ReaderSession {
    var post: Post? = null
    var items: List<MediaItem> = emptyList()
}

class ReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReaderBinding
    private var post: Post? = null
    private var items: List<MediaItem> = emptyList()
    private var index = 0
    private var adapter: ReaderAdapter? = null
    private var chromeVisible = true
    private var verticalMode = false
    private var verticalAdapter: VerticalAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val currentPost = ReaderSession.post
        val currentItems = ReaderSession.items
        if (currentPost == null || currentItems.isEmpty()) {
            finish()
            return
        }
        post = currentPost
        items = currentItems
        index = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, items.size - 1)

        binding.title.text = currentPost.displayTitle
        binding.seek.max = (items.size - 1).coerceAtLeast(1)

        val mode = Prefs.readerMode
        verticalMode = mode == Prefs.READER_VERTICAL

        // 先把「文件名 → 签名直链」的表预热好，避免每张图各触发一次网页抓取
        lifecycleScope.launch { runCatching { MediaResolver.signedTable(currentPost) } }

        if (verticalMode) {
            setupVertical(currentPost)
        } else {
            val rtl = mode == Prefs.READER_RTL
            binding.pager.layoutDirection =
                if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR

            val pageAdapter = ReaderAdapter(
                post = currentPost,
                items = items,
                rtl = rtl,
                onCenterTap = { toggleChrome() },
                onPageTap = { delta -> turn(delta) },
            )
            adapter = pageAdapter
            binding.pager.adapter = pageAdapter
            binding.pager.offscreenPageLimit = 1
            binding.pager.setCurrentItem(index, false)

            binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    index = position
                    updateIndicator()
                    pageAdapter.prefetch(position)
                    post?.let { Store.touchHistory(this@ReaderActivity, it, position + 1, items.size) }
                }
            })
        }

        binding.seek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                if (verticalMode) {
                    (binding.verticalPager.layoutManager as? LinearLayoutManager)
                        ?.scrollToPositionWithOffset(progress, 0)
                } else {
                    binding.pager.setCurrentItem(progress, true)
                }
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar) = Unit
        })

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSave.setOnClickListener { saveCurrent() }
        binding.btnJump.setOnClickListener { showJumpDialog() }

        applyInsets()
        updateIndicator()
        goImmersive()
    }

    /** 全屏阅读时工具栏要避开状态栏/导航栏，否则标题会被时间盖住。 */
    private fun applyInsets() {
        val horizontal = Util.dp(this, 4)
        val vertical = Util.dp(this, 4)
        val bottomHorizontal = Util.dp(this, 12)
        val bottomVertical = Util.dp(this, 8)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.topBar.setPadding(horizontal, bars.top + vertical, horizontal, vertical)
            binding.bottomBar.setPadding(
                bottomHorizontal, bottomVertical, bottomHorizontal, bars.bottom + bottomVertical
            )
            insets
        }
    }

    private fun updateIndicator() {
        binding.pageText.text = getString(R.string.reader_page_of, index + 1, items.size)
        binding.seek.progress = index
    }

    private fun turn(delta: Int) {
        if (verticalMode) {
            val step = (binding.verticalPager.height * 0.85f).toInt().coerceAtLeast(1)
            binding.verticalPager.scrollBy(0, delta * step)
            return
        }
        val target = (index + delta).coerceIn(0, items.size - 1)
        if (target != index) binding.pager.setCurrentItem(target, true)
    }

    /** 条漫模式：整页竖向连续滚动，图片按屏宽铺满。 */
    private fun setupVertical(currentPost: Post) {
        binding.pager.visibility = View.GONE
        binding.verticalPager.visibility = View.VISIBLE

        val adapter = VerticalAdapter(currentPost, items) { toggleChrome() }
        verticalAdapter = adapter
        binding.verticalPager.layoutManager = LinearLayoutManager(this)
        binding.verticalPager.adapter = adapter
        binding.verticalPager.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val manager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val first = manager.findFirstVisibleItemPosition()
                if (first >= 0 && first != index) {
                    index = first
                    updateIndicator()
                    Store.touchHistory(this@ReaderActivity, currentPost, first + 1, items.size)
                }
            }
        })
        binding.verticalPager.post {
            (binding.verticalPager.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(index, 0)
        }
    }

    private fun toggleChrome() {
        chromeVisible = !chromeVisible
        val alpha = if (chromeVisible) 1f else 0f
        binding.topBar.animate().alpha(alpha).setDuration(160).start()
        binding.bottomBar.animate().alpha(alpha).setDuration(160).start()
        binding.topBar.visibility = if (chromeVisible) View.VISIBLE else View.GONE
        binding.bottomBar.visibility = if (chromeVisible) View.VISIBLE else View.GONE
        if (chromeVisible) goImmersive() else hideBars()
    }

    private fun goImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun hideBars() {
        WindowInsetsControllerCompat(window, binding.root).hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!Prefs.volumeKeys) return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                turn(1)
                return true
            }

            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_LEFT -> {
                turn(-1)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (Prefs.volumeKeys && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun showJumpDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.reader_jump_hint, items.size)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.reader_jump)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val page = input.text.toString().toIntOrNull() ?: return@setPositiveButton
                val target = (page - 1).coerceIn(0, items.size - 1)
                if (verticalMode) {
                    (binding.verticalPager.layoutManager as? LinearLayoutManager)
                        ?.scrollToPositionWithOffset(target, 0)
                } else {
                    binding.pager.setCurrentItem(target, true)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun saveCurrent() {
        val currentPost = post ?: return
        val item = items.getOrNull(index) ?: return
        lifecycleScope.launch {
            Util.toast(this@ReaderActivity, getString(R.string.loading))
            val url = runCatching { MediaResolver.bestImageUrl(currentPost, item) }.getOrNull()
            if (url == null) {
                Util.toast(this@ReaderActivity, getString(R.string.reader_failed))
                return@launch
            }
            val bytes = runCatching { Http.fetchMedia(url, Lane.FULL) }.getOrNull()
            if (bytes == null) {
                Util.toast(this@ReaderActivity, getString(R.string.reader_failed))
                return@launch
            }
            val ok = withContext(Dispatchers.IO) { saveToGallery(bytes, item.name) }
            Util.toast(
                this@ReaderActivity,
                getString(if (ok) R.string.reader_saved else R.string.reader_failed)
            )
        }
    }

    private fun saveToGallery(bytes: ByteArray, name: String): Boolean = runCatching {
        val safeName = Util.safeFileName(name, "paw_${System.currentTimeMillis()}.jpg")
        val extension = safeName.substringAfterLast('.', "jpg").lowercase()
        val mime = when (extension) {
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, safeName)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PawViewer")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "PawViewer"
            ).apply { mkdirs() }
            File(dir, safeName).writeBytes(bytes)
        }
        true
    }.getOrDefault(false)

    override fun onDestroy() {
        adapter?.release()
        verticalAdapter?.release()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_INDEX = "index"

        fun start(context: Context, post: Post, items: List<MediaItem>, index: Int) {
            ReaderSession.post = post
            ReaderSession.items = items
            context.startActivity(
                Intent(context, ReaderActivity::class.java)
                    .putExtra(EXTRA_INDEX, index)
            )
        }
    }
}

private class ReaderAdapter(
    private val post: Post,
    private val items: List<MediaItem>,
    private val rtl: Boolean,
    private val onCenterTap: () -> Unit,
    private val onPageTap: (Int) -> Unit,
) : RecyclerView.Adapter<ReaderAdapter.Holder>() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val jobs = HashMap<Int, Job>()
    private var warnedOnce = false

    class Holder(val frame: FrameLayout, val image: ZoomImageView, val progress: ProgressBar) :
        RecyclerView.ViewHolder(frame)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context
        val frame = FrameLayout(context)
        frame.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        val image = ZoomImageView(context)
        frame.addView(
            image,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            )
        )
        val progress = ProgressBar(context)
        progress.indeterminateTintList = android.content.res.ColorStateList.valueOf(0xFF63A4A8.toInt())
        frame.addView(progress, FrameLayout.LayoutParams(Util.dp(context, 36), Util.dp(context, 36), Gravity.CENTER))
        return Holder(frame, image, progress)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.image.resetZoom()
        holder.image.setImageDrawable(null)
        holder.progress.visibility = View.VISIBLE

        holder.image.onTap = { x ->
            val width = holder.itemView.width.toFloat().coerceAtLeast(1f)
            // 从右往左阅读时，左右点击的分工也要反过来
            when {
                x < width / 3f -> onPageTap(if (rtl) 1 else -1)
                x > width * 2f / 3f -> onPageTap(if (rtl) -1 else 1)
                else -> onCenterTap()
            }
        }

        jobs[position]?.cancel()
        jobs[position] = scope.launch {
            val bitmap = load(position)
            if (holder.bindingAdapterPosition != position) return@launch
            holder.progress.visibility = View.GONE
            if (bitmap != null) {
                holder.image.setImageBitmap(bitmap)
            } else {
                holder.image.setImageResource(android.R.drawable.ic_menu_report_image)
                if (!warnedOnce) {
                    warnedOnce = true
                    runCatching {
                        android.widget.Toast.makeText(
                            holder.frame.context,
                            R.string.reader_failed_tip,
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    }

    override fun onViewRecycled(holder: Holder) {
        super.onViewRecycled(holder)
        val position = holder.bindingAdapterPosition
        if (position >= 0) jobs.remove(position)?.cancel()
    }

    fun prefetch(position: Int) {
        for (offset in intArrayOf(1, -1)) {
            val target = position + offset
            if (target !in items.indices) continue
            val item = items[target]
            scope.launch {
                val url = runCatching { MediaResolver.bestImageUrl(post, item) }.getOrNull() ?: return@launch
                ImageLoader.prefetch(url, item.key, Lane.FULL)
            }
        }
    }

    private suspend fun load(position: Int): Bitmap? {
        val item = items[position]
        val url = runCatching { MediaResolver.bestImageUrl(post, item) }.getOrNull()
        if (url != null) {
            val bitmap = ImageLoader.loadBitmap(url, item.key, 1080, 1920, Lane.FULL)
            if (bitmap != null) return bitmap
        }
        val fresh = runCatching { MediaResolver.bestImageUrl(post, item, force = true) }.getOrNull()
            ?: return null
        return ImageLoader.loadBitmap(fresh, item.key, 1080, 1920, Lane.FULL, forceNetwork = true)
    }

    fun release() {
        scope.cancel()
    }
}

private class VerticalAdapter(
    private val post: Post,
    private val items: List<MediaItem>,
    private val onTap: () -> Unit,
) : RecyclerView.Adapter<VerticalAdapter.Holder>() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val jobs = HashMap<Int, Job>()
    private var warnedOnce = false

    class Holder(val frame: FrameLayout, val image: ImageView, val progress: ProgressBar) :
        RecyclerView.ViewHolder(frame)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context
        val frame = FrameLayout(context)
        frame.layoutParams = RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.MATCH_PARENT,
            RecyclerView.LayoutParams.WRAP_CONTENT,
        )
        val image = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            )
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFF000000.toInt())
            minimumHeight = context.resources.displayMetrics.heightPixels / 2
        }
        val progress = ProgressBar(context)
        progress.indeterminateTintList =
            android.content.res.ColorStateList.valueOf(0xFF63A4A8.toInt())
        frame.addView(image)
        frame.addView(
            progress,
            FrameLayout.LayoutParams(Util.dp(context, 36), Util.dp(context, 36), Gravity.CENTER),
        )
        frame.setOnClickListener { onTap() }
        return Holder(frame, image, progress)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.progress.visibility = View.VISIBLE
        holder.image.setImageDrawable(null)
        holder.image.minimumHeight = holder.frame.resources.displayMetrics.heightPixels / 2

        jobs[position]?.cancel()
        jobs[position] = scope.launch {
            val width = holder.frame.resources.displayMetrics.widthPixels
            val bitmap = load(position, width)
            if (holder.bindingAdapterPosition != position) return@launch
            holder.progress.visibility = View.GONE
            if (bitmap != null) {
                holder.image.minimumHeight = 0
                holder.image.setImageBitmap(bitmap)
            } else {
                holder.image.setImageResource(android.R.drawable.ic_menu_report_image)
                if (!warnedOnce) {
                    warnedOnce = true
                    runCatching {
                        android.widget.Toast.makeText(
                            holder.frame.context,
                            R.string.reader_failed_tip,
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    }

    override fun onViewRecycled(holder: Holder) {
        super.onViewRecycled(holder)
        val position = holder.bindingAdapterPosition
        if (position >= 0) jobs.remove(position)?.cancel()
    }

    private suspend fun load(position: Int, width: Int): Bitmap? {
        val item = items[position]
        val url = runCatching { MediaResolver.bestImageUrl(post, item) }.getOrNull()
        if (url != null) {
            val bitmap = ImageLoader.loadBitmap(url, item.key, width, width * 4, Lane.FULL)
            if (bitmap != null) return bitmap
        }
        val fresh = runCatching { MediaResolver.bestImageUrl(post, item, force = true) }.getOrNull()
            ?: return null
        return ImageLoader.loadBitmap(fresh, item.key, width, width * 4, Lane.FULL, forceNetwork = true)
    }

    fun release() {
        scope.cancel()
    }
}