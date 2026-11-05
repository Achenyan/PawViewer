package com.pawviewer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
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
import com.pawviewer.core.model.Post
import com.pawviewer.databinding.FragmentRecentBinding
import com.pawviewer.ui.adapter.PostAdapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class RecentFragment : Fragment() {

    private var _binding: FragmentRecentBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: PostAdapter
    private val items = ArrayList<Post>()
    private val keys = HashSet<String>()

    private var offset = 0
    private var loading = false
    private var end = false
    private var query = ""
    private var service = ""
    private var filter = PostFilter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentRecentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = PostAdapter { post -> openPost(post) }
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
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

        query = Prefs.lastSearch
        binding.searchInput.setText(query)
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                applySearch()
                true
            } else {
                false
            }
        }

        buildChips()
        binding.btnFilter.setOnClickListener {
            FilterDialog.show(requireContext(), filter) { next ->
                filter = next
                adapter.submitList(filter.apply(items))
                updateEmptyState()
            }
        }
        reload()
    }

    private fun applySearch() {
        val text = binding.searchInput.text?.toString()?.trim().orEmpty()
        if (text == query) return
        query = text
        Prefs.lastSearch = text
        reload()
    }

    private fun buildChips() {
        val services = listOf(
            "" to getString(R.string.service_all),
            "patreon" to getString(R.string.service_patreon),
            "fanbox" to getString(R.string.service_fanbox),
            "discord" to getString(R.string.service_discord),
            "gumroad" to getString(R.string.service_gumroad),
            "subscribestar" to getString(R.string.service_subscribestar),
            "fantia" to getString(R.string.service_fantia),
            "boosty" to getString(R.string.service_boosty),
        )
        services.forEach { (value, label) ->
            val chip = Chip(requireContext()).apply {
                text = label
                isCheckable = true
                isChecked = value == service
                tag = value
            }
            binding.chipGroup.addView(chip)
        }
        binding.chipGroup.setOnCheckedStateChangeListener { group, ids ->
            val chip = ids.firstOrNull()?.let { group.findViewById<Chip>(it) } ?: return@setOnCheckedStateChangeListener
            val next = chip.tag as? String ?: ""
            if (next != service) {
                service = next
                reload()
            }
        }
    }

    private fun gridSpan(): Int =
        (resources.configuration.screenWidthDp / 180).coerceAtLeast(2)

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
                var guard = 0
                while (guard++ < 6) {
                    val page = Repo.feed(query, offset)
                    if (page.isEmpty()) {
                        end = true
                        break
                    }
                    offset += 50
                    for (post in page) {
                        if (service.isNotEmpty() && post.service != service) continue
                        if (keys.add(post.key)) items.add(post)
                    }
                    if (page.size < 50) end = true
                    if (items.isNotEmpty() || end) break
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
