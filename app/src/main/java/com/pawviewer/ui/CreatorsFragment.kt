package com.pawviewer.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pawviewer.R
import com.pawviewer.core.Repo
import com.pawviewer.core.Util
import com.pawviewer.databinding.FragmentCreatorsBinding
import com.pawviewer.ui.adapter.CreatorAdapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CreatorsFragment : Fragment() {

    private var _binding: FragmentCreatorsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: CreatorAdapter
    private var searchJob: Job? = null
    private var indexing = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCreatorsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = CreatorAdapter { creator ->
            openCreator(creator.service.orEmpty(), creator.id.orEmpty(), creator.displayName)
        }
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(280)
                    search()
                }
            }
        })

        binding.indexButton.setOnClickListener { buildIndex() }

        if (Repo.creatorCount() == 0) {
            showIndexPanel()
        } else {
            hideIndexPanel()
            search()
        }
    }

    private fun showIndexPanel() {
        binding.indexPanel.visibility = View.VISIBLE
        binding.indexButton.visibility = View.VISIBLE
        binding.indexProgress.visibility = View.GONE
        binding.state.hideState()
    }

    private fun hideIndexPanel() {
        binding.indexPanel.visibility = View.GONE
    }

    private fun buildIndex() {
        if (indexing) return
        indexing = true
        binding.indexButton.visibility = View.GONE
        binding.indexProgress.visibility = View.VISIBLE
        binding.indexHint.text = getString(R.string.creators_index_progress, 0)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val count = Repo.buildCreatorIndex { done ->
                    view?.post {
                        _binding?.indexHint?.text = getString(R.string.creators_index_progress, done)
                    }
                }
                if (_binding == null) return@launch
                Util.toast(requireContext(), getString(R.string.creators_index_done, count))
                hideIndexPanel()
                search()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val binding = _binding ?: return@launch
                binding.indexHint.text = e.message ?: getString(R.string.error_network)
                binding.indexButton.visibility = View.VISIBLE
                binding.indexProgress.visibility = View.GONE
            } finally {
                indexing = false
            }
        }
    }

    private fun search() {
        val query = binding.searchInput.text?.toString()?.trim().orEmpty()
        val results = Repo.searchCreators(query, null)
        adapter.submitList(results)
        if (results.isEmpty()) {
            if (Repo.creatorCount() == 0) {
                showIndexPanel()
            } else {
                binding.state.showEmpty(getString(R.string.empty))
            }
        } else {
            binding.state.hideState()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
