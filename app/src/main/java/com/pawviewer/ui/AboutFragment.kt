package com.pawviewer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.pawviewer.BuildConfig
import com.pawviewer.R
import com.pawviewer.databinding.FragmentAboutBinding

class AboutFragment : Fragment(), MainActivity.Titled {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.version.text = getString(R.string.about_version, BuildConfig.VERSION_NAME)
        val showAccept = arguments?.getBoolean(ARG_ACCEPT) == true
        binding.btnAccept.visibility = if (showAccept) View.VISIBLE else View.GONE
        binding.btnAccept.setOnClickListener {
            (requireActivity() as MainActivity).onDisclaimerAccepted()
        }
    }

    override fun sceneTitle(): CharSequence = getString(R.string.about_title)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_ACCEPT = "accept"

        fun newInstance(showAccept: Boolean): AboutFragment = AboutFragment().apply {
            arguments = Bundle().apply { putBoolean(ARG_ACCEPT, showAccept) }
        }
    }
}
