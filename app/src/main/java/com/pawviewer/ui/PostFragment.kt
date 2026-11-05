package com.pawviewer.ui

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.pawviewer.R
import com.pawviewer.core.DownloadService
import com.pawviewer.core.Repo
import com.pawviewer.core.Util
import com.pawviewer.core.img.ImageLoader
import com.pawviewer.core.media.MediaItem
import com.pawviewer.core.media.MediaKind
import com.pawviewer.core.media.MediaResolver
import com.pawviewer.core.model.Post
import com.pawviewer.databinding.FragmentPostBinding
import com.pawviewer.databinding.ItemAttachmentBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class PostFragment : Fragment(), MainActivity.Titled {

    private var _binding: FragmentPostBinding? = null
    private val binding get() = _binding!!

    private var post: Post? = null
    private var images: List<MediaItem> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPostBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setHasOptionsMenu(true)

        val initial = arguments?.getString(ARG_POST)
            ?.let { runCatching { Gson().fromJson(it, Post::class.java) }.getOrNull() }
        if (initial == null) {
            binding.state.showError(getString(R.string.error_network_hint), getString(R.string.back)) {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
            return
        }

        post = initial
        bind(initial)
        loadDetail(initial)
    }

    private fun loadDetail(initial: Post) {
        binding.state.showLoading()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val full = Repo.detail(initial)
                if (_binding == null) return@launch
                post = full
                bind(full)
                binding.state.hideState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_binding == null) return@launch
                binding.state.showError(
                    Util.netMessage(requireContext(), e),
                    getString(R.string.retry),
                ) { loadDetail(initial) }
            }
        }
    }

    private fun bind(p: Post) {
        binding.title.text = p.displayTitle
        binding.meta.text = "${Util.serviceName(p.service)} · ${p.user}"

        val date = Util.formatDate(p.published ?: p.added)
        binding.stats.text = buildString {
            if (date.isNotBlank()) append(date)
            if (isNotEmpty()) append(" · ")
            append(p.attachmentsOrEmpty.size)
            append(" 个文件")
        }

        val coverPath = p.file?.path
        if (coverPath.isNullOrBlank()) {
            binding.cover.scaleType = ImageView.ScaleType.CENTER
            binding.cover.setImageResource(R.drawable.ic_image_off)
        } else {
            binding.cover.scaleType = ImageView.ScaleType.CENTER_CROP
            ImageLoader.load(
                url = MediaResolver.thumbUrl(coverPath),
                cacheKey = "cover:${p.key}",
                view = binding.cover,
                reqWidth = Util.dp(requireContext(), 120),
                reqHeight = Util.dp(requireContext(), 160),
            )
        }

        val html = p.content.orEmpty()
        binding.content.text = if (html.isBlank()) {
            getString(R.string.post_content_empty)
        } else {
            HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
        }
        binding.content.movementMethod = LinkMovementMethod.getInstance()

        val embed = p.embed
        if (embed?.url != null) {
            binding.embed.visibility = View.VISIBLE
            binding.embed.text =
                "${embed.provider ?: getString(R.string.embed_link)}：${embed.subject ?: embed.url}"
            binding.embed.setOnClickListener {
                runCatching {
                    startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(embed.url)
                        )
                    )
                }
            }
        }

        images = MediaResolver.images(p)
        binding.readButton.isEnabled = images.isNotEmpty()
        binding.readButton.text = if (images.isEmpty()) {
            getString(R.string.post_content_empty)
        } else {
            "${getString(R.string.read)} (${images.size})"
        }
        binding.readButton.setOnClickListener { openReader(p, images, 0) }
        binding.downloadButton.setOnClickListener {
            DownloadService.enqueue(requireContext(), p)
            Util.toast(requireContext(), getString(R.string.download_started))
        }

        buildAttachments(p)
    }

    private fun buildAttachments(p: Post) {
        binding.attachmentContainer.removeAllViews()
        val all = MediaResolver.items(p)
        if (all.isEmpty()) {
            binding.attachmentTitle.visibility = View.GONE
            return
        }
        binding.attachmentTitle.visibility = View.VISIBLE
        binding.attachmentTitle.text = getString(R.string.attachments_count, all.size)

        all.forEach { item ->
            val row = ItemAttachmentBinding.inflate(layoutInflater, binding.attachmentContainer, false)
            row.name.text = item.name
            row.ext.text = item.name.substringAfterLast('.', "").uppercase().take(4).ifBlank { "?" }
            row.kind.text = when (item.kind) {
                MediaKind.IMAGE -> "图片"
                MediaKind.VIDEO -> "视频"
                MediaKind.ARCHIVE -> "压缩包"
                MediaKind.DOC -> "文件"
                MediaKind.OTHER -> ""
            }
            row.root.setOnClickListener {
                when {
                    item.isImage -> {
                        val index = images.indexOfFirst { it.key == item.key }
                        openReader(p, images, if (index >= 0) index else 0)
                    }

                    item.kind == MediaKind.VIDEO -> playVideo(p, item)
                    else -> openInBrowser(p)
                }
            }
            binding.attachmentContainer.addView(row.root)
        }
    }

    /** 视频附件：先解析签名直链，再用内置播放器播（走 Cronet，免代理）。 */
    private fun playVideo(p: Post, item: MediaItem) {
        context?.let { Util.toast(it, getString(R.string.loading)) }
        viewLifecycleOwner.lifecycleScope.launch {
            val url = runCatching { MediaResolver.fullUrl(p, item) }.getOrNull()
            if (url.isNullOrBlank()) {
                if (_binding != null) Util.toast(requireContext(), getString(R.string.player_failed))
                return@launch
            }
            PlayerActivity.start(requireContext(), url, item.name)
        }
    }

    private fun openInBrowser(p: Post) {        runCatching {
            startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(p.webUrl))
            )
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.post_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val p = post ?: return super.onOptionsItemSelected(item)
        return when (item.itemId) {
            R.id.action_open -> {
                openInBrowser(p)
                true
            }

            R.id.action_download -> {
                DownloadService.enqueue(requireContext(), p)
                Util.toast(requireContext(), getString(R.string.download_started))
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun sceneTitle(): CharSequence = post?.displayTitle ?: getString(R.string.app_name)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_POST = "post"

        fun newInstance(post: Post): PostFragment = PostFragment().apply {
            arguments = Bundle().apply { putString(ARG_POST, Gson().toJson(post)) }
        }
    }
}
