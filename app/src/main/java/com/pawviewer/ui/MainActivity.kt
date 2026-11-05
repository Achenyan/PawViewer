package com.pawviewer.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pawviewer.R
import com.pawviewer.core.Prefs
import com.pawviewer.core.model.Post
import com.pawviewer.core.net.Http
import com.pawviewer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val roots = LinkedHashMap<Int, Fragment>()
    private val details = ArrayList<Fragment>()
    private var currentRootId = R.id.nav_recent
    private var visible: Fragment? = null
    private var pendingLink: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.drawer) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.contentRoot.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            binding.navView.setPadding(0, bars.top, 0, 0)
            insets
        }

        requestNotificationPermission()

        if (savedInstanceState != null) {
            supportFragmentManager.fragments.toList().forEach { fragment ->
                supportFragmentManager.beginTransaction()
                    .remove(fragment)
                    .commitNowAllowingStateLoss()
            }
        }

        binding.toolbar.setNavigationOnClickListener {
            if (details.isNotEmpty()) goBack() else binding.drawer.openDrawer(GravityCompat.START)
        }

        binding.navView.setNavigationItemSelectedListener { item ->
            binding.drawer.closeDrawers()
            when (item.itemId) {
                R.id.nav_recent, R.id.nav_creators, R.id.nav_favorites -> {
                    openRoot(item.itemId)
                    true
                }

                R.id.nav_downloads -> {
                    pushDetail(DownloadsFragment(), getString(R.string.downs_title))
                    true
                }

                R.id.nav_settings -> {
                    pushDetail(SettingsFragment(), getString(R.string.settings_title))
                    true
                }

                R.id.nav_about -> {
                    pushDetail(AboutFragment.newInstance(false), getString(R.string.about_title))
                    true
                }

                else -> false
            }
        }

        binding.navView.getHeaderView(0).findViewById<View>(R.id.account_row)
            .setOnClickListener { onAccountClicked() }

        openRoot(R.id.nav_recent)

        if (savedInstanceState == null && !Prefs.disclaimerAccepted) {
            showDisclaimerDialog()
        }

        if (savedInstanceState == null) pendingLink = intent

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.drawer.isDrawerOpen(GravityCompat.START) -> binding.drawer.closeDrawers()
                    details.isNotEmpty() -> goBack()
                    else -> finish()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        updateAccountHeader()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onPostResume() {
        super.onPostResume()
        val link = pendingLink ?: return
        pendingLink = null
        handleIntent(link)
    }

    // ------------------------------------------------------------- 导航

    fun pushDetail(fragment: Fragment, title: String) {
        val tx = supportFragmentManager.beginTransaction()
        visible?.let { if (it !== fragment) tx.hide(it) }
        tx.add(R.id.container, fragment, "detail-" + System.nanoTime())
        tx.commitAllowingStateLoss()
        details.add(fragment)
        visible = fragment
        setTitleText(title)
        updateChrome()
    }

    fun setTitleText(title: CharSequence) {
        binding.toolbar.title = title
    }

    fun onDisclaimerAccepted() {
        Prefs.disclaimerAccepted = true
        goBack()
    }

    private fun goBack(): Boolean {
        if (details.isEmpty()) return false
        val top = details.removeAt(details.size - 1)
        val next = details.lastOrNull() ?: roots[currentRootId]
        val tx = supportFragmentManager.beginTransaction()
        tx.remove(top)
        if (next != null) {
            if (next.isAdded) tx.show(next) else tx.add(R.id.container, next, "root-$currentRootId")
        }
        tx.commitAllowingStateLoss()
        visible = next
        setTitleText(titleForCurrent())
        updateChrome()
        return true
    }

    private fun openRoot(id: Int) {
        while (details.isNotEmpty()) {
            val f = details.removeAt(details.size - 1)
            supportFragmentManager.beginTransaction().remove(f).commitAllowingStateLoss()
        }
        currentRootId = id
        val root = roots.getOrPut(id) { createRoot(id) }
        val tx = supportFragmentManager.beginTransaction()
        if (!root.isAdded) tx.add(R.id.container, root, "root-$id")
        visible?.let { if (it !== root) tx.hide(it) }
        tx.show(root)
        tx.commitAllowingStateLoss()
        visible = root
        binding.navView.setCheckedItem(id)
        setTitleText(getString(titleFor(id)))
        updateChrome()
    }

    private fun createRoot(id: Int): Fragment = when (id) {
        R.id.nav_creators -> CreatorsFragment()
        R.id.nav_favorites -> FavoritesFragment()
        else -> RecentFragment()
    }

    private fun titleFor(id: Int): Int = when (id) {
        R.id.nav_creators -> R.string.tab_creators
        R.id.nav_favorites -> R.string.tab_favorites
        else -> R.string.tab_recent
    }

    private fun titleForCurrent(): CharSequence {
        val top = details.lastOrNull()
        return if (top is Titled) top.sceneTitle() else getString(titleFor(currentRootId))
    }

    private fun updateChrome() {
        val hasDetail = details.isNotEmpty()
        if (hasDetail) {
            binding.toolbar.setNavigationIcon(R.drawable.ic_back)
        } else {
            binding.toolbar.setNavigationIcon(R.drawable.ic_menu)
        }
    }

    // ------------------------------------------------------------- 账号

    private fun updateAccountHeader() {
        val header = binding.navView.getHeaderView(0)
        header.findViewById<android.widget.TextView>(R.id.account_state)?.text =
            getString(if (Prefs.loggedIn) R.string.mine_login_state else R.string.mine_not_login)
        header.findViewById<android.widget.TextView>(R.id.account_action)?.text =
            getString(if (Prefs.loggedIn) R.string.logout else R.string.login_title)
    }

    private fun onAccountClicked() {
        binding.drawer.closeDrawers()
        if (!Prefs.loggedIn) {
            startActivity(Intent(this, LoginActivity::class.java))
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.logout)
            .setMessage(R.string.logout_confirm)
            .setPositiveButton(R.string.logout) { _, _ ->
                Http.clearCookies()
                Prefs.loggedIn = false
                updateAccountHeader()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ------------------------------------------------------------- 其他

    private fun showDisclaimerDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about_disclaimer)
            .setMessage(R.string.about_disclaimer_short)
            .setCancelable(false)
            .setPositiveButton(R.string.about_accept) { _, _ -> Prefs.disclaimerAccepted = true }
            .setNegativeButton(R.string.about_decline) { _, _ -> finish() }
            .show()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001
            )
        }
    }

    /** 站内链接可以直接在本应用里打开。 */
    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        val host = data.host ?: return
        if (host != "pawchive.pw" && !host.endsWith(".pawchive.pw")) return
        val segments = data.pathSegments ?: return
        if (segments.size < 3) return
        val service = segments[0]
        when {
            segments.size >= 5 && segments[1] == "user" && segments[3] == "post" ->
                openPostLink(service, segments[2], segments[4])

            segments.size >= 3 && segments[1] == "post" ->
                openPostLink(service, "", segments[2])

            segments.size >= 3 && segments[1] == "user" ->
                pushDetail(
                    CreatorFragment.newInstance(service, segments[2], segments[2]),
                    segments[2],
                )
        }
    }

    private fun openPostLink(service: String, creatorId: String, postId: String) {
        val post = Post(id = postId, user = creatorId, service = service, title = "")
        pushDetail(PostFragment.newInstance(post), getString(R.string.read))
    }

    interface Titled {
        fun sceneTitle(): CharSequence
    }
}
