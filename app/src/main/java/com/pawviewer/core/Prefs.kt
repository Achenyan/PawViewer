package com.pawviewer.core

import android.content.Context
import android.content.SharedPreferences

object Prefs {

    const val DEFAULT_UA = "PawViewer/1.0.0 (Android)"

    const val READER_LTR = 0
    const val READER_RTL = 1
    const val READER_VERTICAL = 2

    // 代理类型
    const val PROXY_NONE = 0
    const val PROXY_HTTP = 1
    const val PROXY_SOCKS = 2

    // DNS 模式
    const val DNS_SYSTEM = 0
    const val DNS_ALIYUN = 1
    const val DNS_CLOUDFLARE = 2

    private lateinit var sp: SharedPreferences

    fun init(c: Context) {
        sp = c.getSharedPreferences("paw_prefs", Context.MODE_PRIVATE)
    }

    var userAgent: String
        get() = sp.getString("ua", DEFAULT_UA)?.takeIf { it.isNotBlank() } ?: DEFAULT_UA
        set(v) = sp.edit().putString("ua", v.trim()).apply()

    /** 0 = follow system, 1 = light, 2 = dark */
    var themeMode: Int
        get() = sp.getInt("theme", 0)
        set(v) = sp.edit().putInt("theme", v).apply()

    /** Strict politeness: everything at 1 req/s. */
    var strictRate: Boolean
        get() = sp.getBoolean("strict", false)
        set(v) = sp.edit().putBoolean("strict", v).apply()

    var concurrency: Int
        get() = sp.getInt("conc", 3)
        set(v) = sp.edit().putInt("conc", v.coerceIn(1, 5)).apply()

    var disclaimerAccepted: Boolean
        get() = sp.getBoolean("disclaimer", false)
        set(v) = sp.edit().putBoolean("disclaimer", v).apply()

    var creatorsIndexedAt: Long
        get() = sp.getLong("creators_indexed", 0L)
        set(v) = sp.edit().putLong("creators_indexed", v).apply()

    var loggedIn: Boolean
        get() = sp.getBoolean("logged_in", false)
        set(v) = sp.edit().putBoolean("logged_in", v).apply()

    var lastSearch: String
        get() = sp.getString("last_search", "") ?: ""
        set(v) = sp.edit().putString("last_search", v).apply()

    // ------------------------------------------------------------- 阅读器

    /** 0 = 从左到右，1 = 从右到左（日漫），2 = 从上到下（条漫） */
    var readerMode: Int
        get() = sp.getInt(
            "reader_mode",
            if (sp.getBoolean("reader_rtl", false)) READER_RTL else READER_LTR
        )
        set(v) = sp.edit().putInt("reader_mode", v).apply()

    val readerRtl: Boolean get() = readerMode == READER_RTL
    val readerVertical: Boolean get() = readerMode == READER_VERTICAL

    /** 音量键翻页 */
    var volumeKeys: Boolean
        get() = sp.getBoolean("reader_volume", true)
        set(v) = sp.edit().putBoolean("reader_volume", v).apply()

    // ------------------------------------------------------------- 网络

    var proxyType: Int
        get() = sp.getInt("proxy_type", PROXY_NONE)
        set(v) = sp.edit().putInt("proxy_type", v).apply()

    /** "host:port"，例如 127.0.0.1:7890 */
    var proxyAddress: String
        get() = sp.getString("proxy_addr", "") ?: ""
        set(v) = sp.edit().putString("proxy_addr", v.trim()).apply()

    var dnsMode: Int
        get() = sp.getInt("dns_mode", DNS_SYSTEM)
        set(v) = sp.edit().putInt("dns_mode", v).apply()

    /** 每行一条：pawchive.pw=104.21.95.170 */
    var customHosts: String
        get() = sp.getString("custom_hosts", "") ?: ""
        set(v) = sp.edit().putString("custom_hosts", v).apply()

    fun clearCookies() {
        sp.edit().remove("cookies").apply()
        loggedIn = false
    }
}
