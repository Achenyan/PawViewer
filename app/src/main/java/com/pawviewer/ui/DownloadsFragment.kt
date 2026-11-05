package com.pawviewer.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pawviewer.R
import com.pawviewer.core.DownloadService
import com.pawviewer.core.Util
import com.pawviewer.core.db.DownloadRow
import com.pawviewer.core.db.Store
import com.pawviewer.databinding.FragmentDownloadsBinding
import com.pawviewer.ui.adapter.DownloadAdapter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class DownloadsFragment : Fragment() {

    private var _binding: FragmentDownloadsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: DownloadAdapter

    private var allRows: List<DownloadRow> = emptyList()
    private var query: String = ""
    /** 0 全部，1 图包（不含视频），2 视频 */
    private var typeFilter: Int = 0
    private var selectionMode = false
    private val selected = LinkedHashSet<Long>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDownloadsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = DownloadAdapter(
            onCancel = { row -> DownloadService.cancel(requireContext(), row.id); refresh() },
            onResume = { row -> DownloadService.resume(requireContext(), row.id); refresh() },
            onOpen = { row -> openRow(row) },
            hasFiles = { row -> mediaFiles(row).isNotEmpty() },
            onToggleSelect = { row -> toggleSelect(row) },
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        binding.btnMore.setOnClickListener { showMenu(it) }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty()
                applyFilters()
            }
        })
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                true
            } else {
                false
            }
        }

        binding.btnSelectAll.setOnClickListener { toggleSelectAll() }
        binding.btnSelectCancel.setOnClickListener { exitSelection() }
        binding.btnSelectDelete.setOnClickListener { deleteSelected() }

        viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                refresh()
                delay(1200)
            }
        }
    }

    // ------------------------------------------------------------- 菜单

    private fun showMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, R.string.menu_search)
        popup.menu.add(0, 2, 1, R.string.menu_delete)
        popup.menu.add(0, 3, 2, R.string.menu_filter)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    binding.searchWrap.visibility = View.VISIBLE
                    binding.searchInput.requestFocus()
                    val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                        as InputMethodManager
                    imm.showSoftInput(binding.searchInput, InputMethodManager.SHOW_IMPLICIT)
                    true
                }

                2 -> {
                    enterSelection()
                    true
                }

                3 -> {
                    showTypeFilter()
                    true
                }

                else -> false
            }
        }
        popup.show()
    }

    private fun showTypeFilter() {
        val labels = arrayOf(
            getString(R.string.filter_type_all),
            getString(R.string.filter_type_pack),
            getString(R.string.filter_type_video),
        )
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.menu_filter)
            .setSingleChoiceItems(labels, typeFilter) { dialog, which ->
                typeFilter = which
                dialog.dismiss()
                applyFilters()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ------------------------------------------------------------- 选择/删除

    private fun enterSelection() {
        selectionMode = true
        selected.clear()
        binding.selectBar.visibility = View.VISIBLE
        adapter.selectionMode = true
        adapter.selectedIds = emptySet()
        updateSelectAllLabel()
    }

    private fun exitSelection() {
        selectionMode = false
        selected.clear()
        binding.selectBar.visibility = View.GONE
        adapter.selectionMode = false
        adapter.selectedIds = emptySet()
    }

    private fun toggleSelect(row: DownloadRow) {
        if (!selected.add(row.id)) selected.remove(row.id)
        adapter.selectedIds = selected.toSet()
        updateSelectAllLabel()
    }

    private fun toggleSelectAll() {
        val visible = visibleRows()
        if (selected.containsAll(visible.map { it.id })) {
            selected.clear()
        } else {
            selected.clear()
            selected.addAll(visible.map { it.id })
        }
        adapter.selectedIds = selected.toSet()
        updateSelectAllLabel()
    }

    private fun updateSelectAllLabel() {
        val visible = visibleRows()
        val all = visible.isNotEmpty() && selected.containsAll(visible.map { it.id })
        binding.btnSelectAll.setText(if (all) R.string.select_none else R.string.select_all)
    }

    private fun deleteSelected() {
        val ids = selected.toList()
        if (ids.isEmpty()) return
        AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.delete_confirm, ids.size))
            .setPositiveButton(R.string.delete) { _, _ ->
                ids.forEach { id ->
                    Store.download(requireContext(), id)?.let { row ->
                        runCatching { File(row.dir).deleteRecursively() }
                        runCatching { File(row.dir).parentFile?.takeIf { it.listFiles().isNullOrEmpty() }?.delete() }
                    }
                    Store.deleteDownload(requireContext(), id)
                }
                exitSelection()
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ------------------------------------------------------------- 数据

    private var currentVisible: List<DownloadRow> = emptyList()

    private fun visibleRows(): List<DownloadRow> = currentVisible

    private fun refresh() {
        allRows = Store.downloads(requireContext())
        applyFilters()
    }

    private fun applyFilters() {
        var list = allRows
        if (typeFilter != 0) {
            list = list.filter { row ->
                val hasVideo = hasVideoFile(row)
                if (typeFilter == 2) hasVideo else !hasVideo
            }
        }
        if (query.isNotBlank()) {
            list = list
                .mapNotNull { row -> Util.fuzzyScore(row.title, query)?.let { score -> row to score } }
                .sortedWith(compareBy({ it.second }, { -it.first.created }))
                .map { it.first }
        }
        currentVisible = list
        adapter.submit(list)
        if (selectionMode) {
            selected.retainAll(list.map { it.id }.toSet())
            adapter.selectedIds = selected.toSet()
            updateSelectAllLabel()
        }
        if (list.isEmpty() && allRows.isEmpty()) {
            binding.state.showEmpty(getString(R.string.downs_empty))
        } else if (list.isEmpty()) {
            binding.state.showEmpty(getString(R.string.search_no_result))
        } else {
            binding.state.hideState()
        }
    }

    private fun hasVideoFile(row: DownloadRow): Boolean =
        File(row.dir).listFiles()?.any { it.isFile && Util.isVideoName(it.name) } == true

    private fun mediaFiles(row: DownloadRow): List<File> =
        File(row.dir).listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") && isMedia(it.name) }
            ?.sortedBy { it.name }
            .orEmpty()

    private fun openRow(row: DownloadRow) {
        if (mediaFiles(row).isEmpty()) {
            Util.toast(requireContext(), getString(R.string.downloads_empty_files))
            return
        }
        LocalPostActivity.start(requireContext(), row.dir, row.title)
    }

    private fun isMedia(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in setOf(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "avif", "jfif",
            "mp4", "webm", "mov", "m4v", "mkv",
        )

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
            as? InputMethodManager ?: return
        imm.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
