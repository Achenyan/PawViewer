package com.pawviewer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pawviewer.R
import com.pawviewer.core.DownloadService
import com.pawviewer.core.db.DownloadState
import com.pawviewer.core.db.Store
import com.pawviewer.databinding.FragmentDownloadsBinding
import com.pawviewer.ui.adapter.DownloadAdapter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DownloadsFragment : Fragment() {

    private var _binding: FragmentDownloadsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: DownloadAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDownloadsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = DownloadAdapter { row ->
            if (row.state == DownloadState.FAILED) {
                Store.updateDownload(requireContext(), row.id, state = DownloadState.WAITING, done = 0)
                DownloadService.kick(requireContext())
            } else {
                Store.deleteDownload(requireContext(), row.id)
            }
            refresh()
        }
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                refresh()
                delay(1200)
            }
        }
    }

    private fun refresh() {
        val rows = Store.downloads(requireContext())
        adapter.submit(rows)
        if (rows.isEmpty()) {
            binding.state.showEmpty(getString(R.string.downs_empty))
        } else {
            binding.state.hideState()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
