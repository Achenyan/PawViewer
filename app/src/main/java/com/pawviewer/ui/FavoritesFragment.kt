package com.pawviewer.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.pawviewer.R
import com.pawviewer.core.Prefs
import com.pawviewer.core.Repo
import com.pawviewer.core.Util
import com.pawviewer.databinding.FragmentFavoritesBinding
import com.pawviewer.ui.adapter.PostAdapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class FavoritesFragment : Fragment() {

    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: PostAdapter

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) load()
        else updateLoginState()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = PostAdapter { post -> openPost(post) }
        binding.recycler.layoutManager =
            LinearLayoutManager(requireContext())
        binding.recycler.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )
        binding.recycler.adapter = adapter
        binding.refresh.setOnRefreshListener { load() }
        load()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null && Prefs.loggedIn && adapter.itemCount == 0) load()
    }

    private fun updateLoginState() {
        if (!Prefs.loggedIn) {
            binding.state.showError(
                getString(R.string.favorite_need_login),
                getString(R.string.login_title),
            ) {
                loginLauncher.launch(Intent(requireContext(), LoginActivity::class.java))
            }
        }
    }

    private fun load() {
        if (!Prefs.loggedIn) {
            updateLoginState()
            return
        }
        binding.state.showLoading(getString(R.string.loading))
        viewLifecycleOwner.lifecycleScope.launch {
            val binding = _binding ?: return@launch
            try {
                val list = Repo.favorites()
                adapter.submitList(list)
                if (list.isEmpty()) {
                    binding.state.showEmpty(getString(R.string.empty))
                } else {
                    binding.state.hideState()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                binding.state.showError(
                    Util.netMessage(requireContext(), e),
                    getString(R.string.retry),
                ) { load() }
            } finally {
                binding.refresh.isRefreshing = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
