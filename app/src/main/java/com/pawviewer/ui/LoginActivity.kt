package com.pawviewer.ui

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.pawviewer.R
import com.pawviewer.core.Repo
import com.pawviewer.core.Util
import com.pawviewer.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

/**
 * 原生登录表单。
 *
 * 之前用 WebView 打开站点登录页，在 DNS 被污染的网络里 WebView 走系统解析、
 * 不经过应用内代理，会被解析到别的地方（表现为「点登录跳到色情站」）。
 * 现在直接对 /account/login 发 POST，复用应用内的 OkHttp（含代理/DoH），
 * 既不打开网页，也不会被劫持。
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val padLeft = binding.root.paddingLeft
        val padTop = binding.root.paddingTop
        val padRight = binding.root.paddingRight
        val padBottom = binding.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                padLeft + bars.left,
                padTop + bars.top,
                padRight + bars.right,
                padBottom + bars.bottom,
            )
            insets
        }

        binding.btnLogin.setOnClickListener { submit() }
        binding.inputPass.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }
    }

    private fun submit() {
        val user = binding.inputUser.text?.toString()?.trim().orEmpty()
        val pass = binding.inputPass.text?.toString().orEmpty()
        if (user.isBlank() || pass.isBlank()) {
            binding.status.setText(R.string.login_bad_credentials)
            return
        }

        binding.btnLogin.isEnabled = false
        binding.progress.visibility = View.VISIBLE
        binding.status.text = getString(R.string.login_checking)

        lifecycleScope.launch {
            val result = runCatching { Repo.login(user, pass) }
            binding.btnLogin.isEnabled = true
            binding.progress.visibility = View.GONE
            when {
                result.getOrNull() == true -> {
                    Util.toast(this@LoginActivity, getString(R.string.login_success))
                    setResult(RESULT_OK)
                    finish()
                }

                result.isFailure -> {
                    binding.status.text = Util.netMessage(this@LoginActivity, result.exceptionOrNull())
                }

                else -> binding.status.text = getString(R.string.login_bad_credentials)
            }
        }
    }
}
