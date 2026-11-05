package com.pawviewer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.pawviewer.App
import com.pawviewer.R
import com.pawviewer.core.Prefs
import com.pawviewer.core.Util
import com.pawviewer.core.img.ImageLoader
import com.pawviewer.core.net.Http
import com.pawviewer.core.net.Lanes
import com.pawviewer.databinding.FragmentSettingsBinding
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bindTheme()
        bindReader()
        bindNetwork()
        bindRate()
        bindCache()
        binding.uaInput.setText(Prefs.userAgent)
    }

    // ------------------------------------------------------------- 主题

    private fun bindTheme() {
        binding.themeGroup.check(
            when (Prefs.themeMode) {
                1 -> R.id.theme_light
                2 -> R.id.theme_dark
                else -> R.id.theme_system
            }
        )
        binding.themeGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.theme_light -> 1
                R.id.theme_dark -> 2
                else -> 0
            }
            if (mode != Prefs.themeMode) {
                Prefs.themeMode = mode
                App.instance.applyTheme()
                requireActivity().recreate()
            }
        }
    }

    // ------------------------------------------------------------- 阅读

    private fun bindReader() {
        binding.dirGroup.check(
            when (Prefs.readerMode) {
                Prefs.READER_RTL -> R.id.dir_rtl
                Prefs.READER_VERTICAL -> R.id.dir_vertical
                else -> R.id.dir_ltr
            }
        )
        binding.dirGroup.setOnCheckedChangeListener { _, checkedId ->
            Prefs.readerMode = when (checkedId) {
                R.id.dir_rtl -> Prefs.READER_RTL
                R.id.dir_vertical -> Prefs.READER_VERTICAL
                else -> Prefs.READER_LTR
            }
        }

        binding.volumeSwitch.isChecked = Prefs.volumeKeys
        binding.volumeSwitch.setOnCheckedChangeListener { _, checked ->
            Prefs.volumeKeys = checked
        }
    }

    // ------------------------------------------------------------- 网络

    private fun bindNetwork() {
        binding.proxyGroup.check(
            when (Prefs.proxyType) {
                Prefs.PROXY_HTTP -> R.id.proxy_http
                Prefs.PROXY_SOCKS -> R.id.proxy_socks
                else -> R.id.proxy_none
            }
        )
        binding.proxyInput.setText(Prefs.proxyAddress)

        binding.dnsGroup.check(
            when (Prefs.dnsMode) {
                Prefs.DNS_ALIYUN -> R.id.dns_aliyun
                Prefs.DNS_CLOUDFLARE -> R.id.dns_cloudflare
                else -> R.id.dns_system
            }
        )
        binding.hostsInput.setText(Prefs.customHosts)

        binding.btnApplyNet.setOnClickListener {
            saveNetwork()
            Http.rebuild()
            Util.toast(requireContext(), getString(R.string.settings_applied))
        }

        binding.btnTestNet.setOnClickListener {
            saveNetwork()
            Http.rebuild()
            testConnection()
        }
    }

    private fun saveNetwork() {
        Prefs.proxyType = when (binding.proxyGroup.checkedRadioButtonId) {
            R.id.proxy_http -> Prefs.PROXY_HTTP
            R.id.proxy_socks -> Prefs.PROXY_SOCKS
            else -> Prefs.PROXY_NONE
        }
        Prefs.proxyAddress = binding.proxyInput.text?.toString().orEmpty()
        Prefs.dnsMode = when (binding.dnsGroup.checkedRadioButtonId) {
            R.id.dns_aliyun -> Prefs.DNS_ALIYUN
            R.id.dns_cloudflare -> Prefs.DNS_CLOUDFLARE
            else -> Prefs.DNS_SYSTEM
        }
        Prefs.customHosts = binding.hostsInput.text?.toString().orEmpty()
    }

    private fun testConnection() {
        binding.netStatus.setText(R.string.settings_net_testing)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { Http.testConnection() }
            if (_binding == null) return@launch
            result.fold(
                onSuccess = { version ->
                    binding.netStatus.text = getString(R.string.settings_net_ok, version)
                },
                onFailure = { error ->
                    binding.netStatus.text = getString(
                        R.string.settings_net_fail,
                        error.message ?: error.javaClass.simpleName,
                    )
                },
            )
        }
    }

    // ------------------------------------------------------------- 限速

    private fun bindRate() {
        binding.strictSwitch.isChecked = Prefs.strictRate
        binding.strictSwitch.setOnCheckedChangeListener { _, checked ->
            Prefs.strictRate = checked
            Lanes.applyStrict(checked, Prefs.concurrency)
        }

        binding.concurrencySlider.value = Prefs.concurrency.toFloat()
        binding.concurrencySlider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            Prefs.concurrency = value.toInt()
            Lanes.applyStrict(Prefs.strictRate, value.toInt())
        }
    }

    // ------------------------------------------------------------- 缓存

    private fun bindCache() {
        binding.cacheSize.text = Util.humanSize(ImageLoader.diskUsage())
        binding.btnClearCache.setOnClickListener {
            ImageLoader.clearDisk()
            binding.cacheSize.text = Util.humanSize(0)
            Util.toast(requireContext(), getString(R.string.settings_cleared))
        }

        binding.btnCleanOrphan.setOnClickListener {
            val removed = with(requireContext()) {
                val root = getExternalFilesDir("downloads") ?: filesDir
                val known = com.pawviewer.core.db.Store.downloads(this).map { it.dir }.toSet()
                var count = 0
                root.listFiles()?.forEach { serviceDir ->
                    serviceDir.listFiles()?.forEach { postDir ->
                        if (!known.contains(postDir.absolutePath)) {
                            if (postDir.deleteRecursively()) count++
                        }
                    }
                    if (serviceDir.listFiles().isNullOrEmpty()) serviceDir.delete()
                }
                count
            }
            Util.toast(requireContext(), getString(R.string.settings_clean_orphan_done, removed))
        }

        binding.btnReset.setOnClickListener {
            Prefs.userAgent = Prefs.DEFAULT_UA
            Prefs.strictRate = false
            Prefs.concurrency = 3
            Prefs.themeMode = 0
            Prefs.readerMode = Prefs.READER_LTR
            Prefs.volumeKeys = true
            Prefs.proxyType = Prefs.PROXY_NONE
            Prefs.proxyAddress = ""
            Prefs.dnsMode = Prefs.DNS_SYSTEM
            Prefs.customHosts = ""
            Lanes.applyStrict(false, 3)
            Http.rebuild()
            App.instance.applyTheme()
            requireActivity().recreate()
        }
    }

    override fun onPause() {
        super.onPause()
        val text = _binding?.uaInput?.text?.toString()?.trim().orEmpty()
        if (text.isNotBlank() && text != Prefs.userAgent) {
            Prefs.userAgent = text
            Http.rebuild()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
