package com.pawviewer.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cronet.CronetDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.pawviewer.R
import com.pawviewer.core.Util
import com.pawviewer.core.net.Http
import com.pawviewer.core.net.NetConfig
import com.pawviewer.databinding.ActivityPlayerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/**
 * 内置播放器。
 *
 * 网络地址先用应用内的 Cronet（HTTP/3）把文件流式缓存到本地，再本地播放——
 * 这样既不依赖系统网络（免代理），也避免播放器自身的网络栈被 SNI 阻断。
 */
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private var executor: java.util.concurrent.ExecutorService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.topBar.setPadding(
                Util.dp(this, 4),
                bars.top + Util.dp(this, 4),
                Util.dp(this, 4),
                Util.dp(this, 4),
            )
            view.setPadding(0, 0, 0, bars.bottom)
            insets
        }

        val uri = intent.getStringExtra(EXTRA_URI)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        if (uri.isNullOrBlank()) {
            finish()
            return
        }

        binding.title.text = title.ifBlank { uri.substringAfterLast('/') }
        binding.btnBack.setOnClickListener { finish() }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (uri.startsWith("http", ignoreCase = true)) {
            prepareRemote(uri)
        } else {
            playLocal(File(android.net.Uri.parse(uri).path ?: uri))
        }
    }

    private fun prepareRemote(url: String) {
        binding.loadingBox.visibility = View.VISIBLE
        binding.status.text = getString(R.string.player_buffering, 0)
        lifecycleScope.launch {
            val cacheDir = File(cacheDir, "video").apply { mkdirs() }
            val ext = url.substringBefore('?').substringAfterLast('.', "mp4").take(5)
            val target = File(cacheDir, Util.sha1(url.substringBefore('?')) + "." + ext)
            if (target.exists() && target.length() > 0) {
                playLocal(target)
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    Http.downloadToFile(url, target) { done, total ->
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            runOnUiThread {
                                if (!isFinishing) binding.status.text =
                                    getString(R.string.player_buffering, pct)
                            }
                        }
                        true
                    }
                }
            }
            if (isFinishing) return@launch
            result.fold(
                onSuccess = { playLocal(it) },
                onFailure = {
                    binding.loadingBox.visibility = View.GONE
                    val hint = getString(R.string.player_failed_net)
                    Util.toast(this@PlayerActivity, hint)
                    binding.status.text = hint
                },
            )
        }
    }

    private fun playLocal(file: File) {
        if (!file.exists()) {
            binding.loadingBox.visibility = View.GONE
            Util.toast(this, getString(R.string.player_failed))
            return
        }
        binding.loadingBox.visibility = View.VISIBLE
        binding.status.text = ""
        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFor(file)))
            .build()
        player = exo
        binding.playerView.player = exo
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                binding.loadingBox.visibility =
                    if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
            }

            override fun onPlayerError(error: PlaybackException) {
                binding.loadingBox.visibility = View.GONE
                Util.toast(this@PlayerActivity, getString(R.string.player_failed))
            }
        })
        exo.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file).toString()))
        exo.prepare()
        exo.playWhenReady = true
    }

    /** file:// 用默认数据源；只有在播放 http 地址时才用 Cronet（它不支持 file 协议）。 */
    private fun dataSourceFor(file: File): DataSource.Factory {
        val engine = Http.cronetEngine()
        val isRemote = !file.exists() && file.path.startsWith("http", ignoreCase = true)
        if (isRemote && engine != null && NetConfig.proxy() == null) {
            val exec = Executors.newSingleThreadExecutor()
            executor = exec
            return CronetDataSource.Factory(engine, exec)
        }
        return DefaultDataSource.Factory(this)
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        executor?.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_URI = "uri"
        private const val EXTRA_TITLE = "title"

        fun start(context: Context, url: String, title: String) {
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putExtra(EXTRA_URI, url)
                    .putExtra(EXTRA_TITLE, title)
            )
        }

        fun startLocal(context: Context, file: File, title: String) =
            start(context, android.net.Uri.fromFile(file).toString(), title)
    }
}
