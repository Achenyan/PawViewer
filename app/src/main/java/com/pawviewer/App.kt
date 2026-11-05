package com.pawviewer

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.pawviewer.core.Prefs
import com.pawviewer.core.Repo
import com.pawviewer.core.img.ImageLoader
import com.pawviewer.core.net.Http

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        Prefs.init(this)
        Http.init(this)
        ImageLoader.init(this)
        Repo.init(this)
        applyTheme()
    }

    fun applyTheme() {
        AppCompatDelegate.setDefaultNightMode(
            when (Prefs.themeMode) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    companion object {
        lateinit var instance: App
            private set

        val ctx get() = instance.applicationContext
    }
}
