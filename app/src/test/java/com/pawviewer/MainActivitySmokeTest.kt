package com.pawviewer

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.pawviewer.core.Prefs
import com.pawviewer.ui.MainActivity
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 启动冒烟测试：真正把 MainActivity 走一遍 onCreate + inflate + 首次导航，
 * 用来在没有真机的情况下抓主题 / 布局 / 绑定层面的崩溃。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class MainActivitySmokeTest {

    @Test
    fun launchesWithoutCrash() {
        val app = ApplicationProvider.getApplicationContext<App>()
        Prefs.init(app)
        Prefs.disclaimerAccepted = true
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertNotNull(activity)
        assertNotNull(activity.findViewById(android.R.id.content))
        controller.pause().stop().destroy()
    }
}
