package com.pawviewer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.pawviewer.R
import com.pawviewer.core.PostFilter
import com.pawviewer.core.Prefs
import com.pawviewer.core.Repo
import com.pawviewer.core.Util
import com.pawviewer.core.img.ImageLoader
import com.pawviewer.core.model.Post
import com.pawviewer.core.model.TagCount
import com.pawviewer.databinding.FragmentCreatorBinding
import com.pawviewer.ui.adapter.PostAdapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class CreatorFragment : Fragment(), MainActivity.Titled {

    private var _binding: FragmentCreatorBinding? = null
    private val binding get() = _binding!!

    private var service = ""
    private var creatorId = ""
    private var creatorName = ""

    private lateinit var adapter: PostAdapter
    private val items = ArrayList<Post>()
    private val keys = HashSet<String>()

    private var offset = 0
    private var loading = false
    private var end = false
    private var selectedTag = ""
    private var filter = PostFilter()
    private var favorited = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCreatorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        service = arguments?.getString(ARG_SERVICE).orEmpty()
        creatorId = arguments?.getString(ARG_UID).orEmpty()
        creatorName = arguments?.getString(ARG_NAME).orEmpty()

        binding.name.text = creatorName.ifBlank { creatorId }
        binding.meta.text = Util.serviceName(service)

        ImageLoader.load(
            url = "https://pawchive.pw/icons/$service/$creatorId",
            cacheKey = "avatar:$service:$creatorId",
            view = binding.avatar,
            reqWidth = Util.dp(requireContext(), 48),
            reqHeight = Util.dp(requireContext(), 48),
        )

        adapter = PostAdapter { post -> openPost(post) }
        binding.recycler.layoutManager =
            LinearLayoutManager(requireContext())
        binding.recycler.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )
        binding.recycler.adapter = adapter
        binding.recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val manager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                if (manager.findLastVisibleItemPosition() >= items.size - 4) loadMore()
            }
        })
        binding.refresh.setOnRefreshListener { reload() }

        binding.favButton.setOnClickListener { toggleFavorite() }

        binding.btnFilter.setOnClickListener {
            FilterDialog.show(requireContext(), filter) { next ->
                filter = next
                adapter.submitList(filter.apply(items))
                updateEmptyState()
            }
        }

        loadHeader()
        reload()
    }

    private fun loadHeader() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val profile = Repo.profile(service, creatorId)
                if (_binding == null) return@launch
                if (profile != null) {
                    binding.name.text = profile.displayName.ifBlank { creatorName }
                    binding.meta.text = buildString {
                        append(Util.serviceName(service))
                        val updated = Util.formatDate(profile.updated)
                        if (updated.isNotBlank()) {
                            append(" · 更新于 ")
                            append(updated)
                        }
                    }
                }
                val tags = Repo.tags(service, creatorId)
                if (_binding == null) return@launch
                buildTags(tags)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 头部信息失败不影响正文列表
            }
        }
    }

    private fun buildTags(tags: List<TagCount>) {
        binding.tagGroup.removeAllViews()
        if (tags.isEmpty()) {
            binding.tagGroup.visibility = View.GONE
            return
        }
        binding.tagGroup.visibility = View.VISIBLE
        val all = Chip(requireContext()).apply {
            text = getString(R.string.service_all)
            isCheckable = true
            isChecked = selectedTag.isEmpty()
            this.tag = ""
        }
        binding.tagGroup.addView(all)
        tags.take(30).forEach { entry ->
            val value = entry.tag ?: return@forEach
            val chip = Chip(requireContext()).apply {
                text = "${value} (${entry.postCount ?: 0})"
                isCheckable = true
                isChecked = selectedTag == value
                this.tag = value
            }
            binding.tagGroup.addView(chip)
        }
        binding.tagGroup.setOnCheckedStateChangeListener { group, ids ->
            val chip = ids.firstOrNull()?.let { group.findViewById<Chip>(it) }
                ?: return@setOnCheckedStateChangeListener
            val next = chip.tag as? String ?: ""
            if (next != selectedTag) {
                selectedTag = next
                reload()
            }
        }
    }

    private fun toggleFavorite() {
        if (!Prefs.loggedIn) {
            Util.toast(requireContext(), getString(R.string.favorite_need_login))
            return
        }
        val target = !favorited
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = Repo.toggleCreatorFavorite(service, creatorId, target)
            if (_binding == null) return@launch
            if (ok) {
                favorited = target
                updateFavoriteButton()
                Util.toast(
                    requireContext(),
                    getString(if (target) R.string.favorite_added else R.string.favorite_removed)
                )
            } else {
                Util.toast(requireContext(), getString(R.string.error_network_hint))
            }
        }
    }

    private fun updateFavoriteButton() {
        binding.favButton.setText(if (favorited) R.string.favorite_remove else R.string.favorite_add)
    }

    /** 空列表时的文案：按时间筛选出的空白给彩蛋。 */
    private fun updateEmptyState() {
        val b = _binding ?: return
        if (items.isEmpty()) {
            if (end) b.state.showEmpty(getString(R.string.empty)) else b.state.hideState()
            return
        }
        if (filter.apply(items).isNotEmpty()) {
            b.state.hideState()
            return
        }
        when (filter.dateMode) {
            com.pawviewer.core.PostFilter.DATE_AFTER ->
                b.state.showEmptyBrand(getString(R.string.egg_future))

            com.pawviewer.core.PostFilter.DATE_BEFORE ->
                b.state.showEmptyBrand(getString(R.string.egg_past))

            else -> b.state.showEmpty(getString(R.string.empty))
        }
    }

    private fun reload() {
        items.clear()
        keys.clear()
        offset = 0
        end = false
        loading = false
        adapter.submitList(emptyList())
        binding.state.showLoading(getString(R.string.loading))
        loadMore()
    }

    private fun loadMore() {
        if (loading || end) return
        loading = true
        viewLifecycleOwner.lifecycleScope.launch {
            val binding = _binding ?: return@launch
            try {
                val page = Repo.creatorPosts(service, creatorId, "", selectedTag, offset)
                if (page.isEmpty()) {
                    end = true
                } else {
                    offset += 50
                    for (post in page) {
                        if (keys.add(post.key)) items.add(post)
                    }
                    if (page.size < 50) end = true
                }
                adapter.submitList(filter.apply(items))
                updateEmptyState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (items.isEmpty()) {
                    binding.state.showError(
                        Util.netMessage(requireContext(), e),
                        getString(R.string.retry),
                    ) { reload() }
                } else {
                    context?.let { Util.toast(it, e.message) }
                }
            } finally {
                loading = false
                binding.refresh.isRefreshing = false
            }
        }
    }

    override fun sceneTitle(): CharSequence = creatorName.ifBlank { creatorId }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_SERVICE = "service"
        private const val ARG_UID = "uid"
        private const val ARG_NAME = "name"

        fun newInstance(service: String, creatorId: String, name: String): CreatorFragment =
            CreatorFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SERVICE, service)
                    putString(ARG_UID, creatorId)
                    putString(ARG_NAME, name)
                }
            }
    }
}
