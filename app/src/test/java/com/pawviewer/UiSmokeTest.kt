package com.pawviewer

import android.os.Build
import com.pawviewer.core.media.MediaItem
import com.pawviewer.core.media.MediaKind
import com.pawviewer.core.model.FileRef
import com.pawviewer.core.model.Post
import com.pawviewer.ui.AboutFragment
import com.pawviewer.ui.CreatorFragment
import com.pawviewer.ui.DownloadsFragment
import com.pawviewer.ui.LoginActivity
import com.pawviewer.ui.MainActivity
import com.pawviewer.ui.PostFragment
import com.pawviewer.ui.ReaderActivity
import com.pawviewer.ui.ReaderSession
import com.pawviewer.ui.SettingsFragment
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class UiSmokeTest {

    private fun mainActivity(): MainActivity =
        Robolectric.buildActivity(MainActivity::class.java).setup().get()

    private fun samplePost() = Post(
        id = "174327493",
        user = "86513152",
        service = "patreon",
        title = "sample",
        file = FileRef(name = "a.png", path = "/aa/bb/a.png"),
    )

    @Test
    fun switchesAllRootTabs() {
        val activity = mainActivity()
        val nav = activity.findViewById<com.google.android.material.navigation.NavigationView>(R.id.nav_view)
        assertNotNull(nav)
        for (id in intArrayOf(R.id.nav_creators, R.id.nav_favorites, R.id.nav_recent)) {
            nav.menu.performIdentifierAction(id, 0)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        }
        assertTrue(activity.supportFragmentManager.fragments.isNotEmpty())
    }

    @Test
    fun pushesDetailScreens() {
        val activity = mainActivity()
        activity.pushDetail(PostFragment.newInstance(samplePost()), "post")
        activity.pushDetail(CreatorFragment.newInstance("patreon", "86513152", "creator"), "creator")
        activity.pushDetail(SettingsFragment(), "settings")
        activity.pushDetail(DownloadsFragment(), "downloads")
        activity.pushDetail(AboutFragment.newInstance(false), "about")
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(activity.supportFragmentManager.fragments.size >= 2)
    }

    @Test
    fun launchesReader() {
        ReaderSession.post = samplePost()
        ReaderSession.items = listOf(
            MediaItem(
                key = "k1",
                name = "a.png",
                kind = MediaKind.IMAGE,
                path = "/aa/bb/a.png",
                thumbUrl = "https://img.pawchive.pw/thumbnail/data/aa/bb/a.png",
                directUrl = "https://file.pawchive.pw/data/aa/bb/a.png",
                signedName = "a.png",
            )
        )
        val controller = Robolectric.buildActivity(ReaderActivity::class.java).setup()
        assertNotNull(controller.get())
        controller.pause().stop().destroy()
    }

    @Test
    fun launchesLoginWebView() {
        val controller = Robolectric.buildActivity(LoginActivity::class.java).setup()
        assertNotNull(controller.get())
        controller.pause().stop().destroy()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU], qualifiers = "night")
    fun launchesInDarkTheme() {
        val activity = mainActivity()
        activity.pushDetail(SettingsFragment(), "settings")
        assertTrue(activity.supportFragmentManager.fragments.isNotEmpty())
    }

    @Test
    fun opensSiteDeepLink() {
        val intent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse("https://pawchive.pw/patreon/post/174327493"),
        )
        val controller = Robolectric.buildActivity(MainActivity::class.java, intent).setup()
        assertNotNull(controller.get())
        assertTrue(controller.get().supportFragmentManager.fragments.isNotEmpty())
        controller.pause().stop().destroy()
    }
}
