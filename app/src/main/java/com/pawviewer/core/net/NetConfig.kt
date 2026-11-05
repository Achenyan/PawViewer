package com.pawviewer.core.net

import com.google.gson.JsonParser
import com.pawviewer.core.Prefs
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * DNS 与代理。参照 EhViewer 的两层做法：
 *  1) 自定义 Dns（内置 hosts + DoH），绕开本地 DNS 污染；
 *  2) ProxySelector（DIRECT / HTTP / SOCKS）。
 *
 * 这里用 JSON 版 DoH（RFC 8484 的 ?name=&type= 形式），比二进制报文简单，
 * 阿里/Cloudflare 都支持；DoH 客户端自己用系统 DNS 解析，避免递归。
 */
object NetConfig {

    private const val ALIYUN_DOH = "https://dns.alidns.com/resolve"
    private const val CLOUDFLARE_DOH = "https://cloudflare-dns.com/dns-query"

    fun proxy(): Proxy? {
        if (Prefs.proxyType == Prefs.PROXY_NONE) return null
        val address = Prefs.proxyAddress.trim()
        if (address.isBlank()) return null
        val host: String
        val port: Int
        val parts = address.split(":")
        if (parts.size == 2) {
            host = parts[0].trim()
            port = parts[1].trim().toIntOrNull() ?: return null
        } else {
            host = address
            port = if (Prefs.proxyType == Prefs.PROXY_SOCKS) 1080 else 8080
        }
        if (host.isBlank() || port !in 1..65535) return null
        return runCatching {
            val type = if (Prefs.proxyType == Prefs.PROXY_SOCKS) Proxy.Type.SOCKS else Proxy.Type.HTTP
            Proxy(type, InetSocketAddress(host, port))
        }.getOrNull()
    }

    fun dns(): Dns {
        val hosts = parseHosts(Prefs.customHosts)
        val endpoint = when (Prefs.dnsMode) {
            Prefs.DNS_ALIYUN -> ALIYUN_DOH
            Prefs.DNS_CLOUDFLARE -> CLOUDFLARE_DOH
            else -> null
        }
        if (hosts.isEmpty() && endpoint == null) return Dns.SYSTEM

        val dohClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .apply { proxy()?.let { proxy(it) } }
            .build()

        return DohDns(hosts, endpoint, dohClient)
    }

    fun parseHosts(raw: String): Map<String, List<InetAddress>> {
        if (raw.isBlank()) return emptyMap()
        val map = HashMap<String, MutableList<InetAddress>>()
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            val sep = trimmed.indexOfFirst { it == '=' || it == ' ' || it == '\t' }
            if (sep <= 0) return@forEach
            val host = trimmed.substring(0, sep).trim().lowercase()
            val ip = trimmed.substring(sep + 1).trim()
            if (host.isEmpty() || ip.isEmpty()) return@forEach
            val address = runCatching { InetAddress.getByName(ip) }.getOrNull() ?: return@forEach
            map.getOrPut(host) { mutableListOf() }.add(address)
        }
        return map
    }

    /** 只对配置里出现的域名做覆盖，其余仍走系统或 DoH。 */
    private class DohDns(
        private val hosts: Map<String, List<InetAddress>>,
        private val endpoint: String?,
        private val client: OkHttpClient,
    ) : Dns {

        override fun lookup(hostname: String): List<InetAddress> {
            hosts[hostname.lowercase()]?.takeIf { it.isNotEmpty() }?.let { return it }

            if (endpoint != null) {
                runCatching { resolveDoh(hostname) }
                    .getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { return it }
            }
            return Dns.SYSTEM.lookup(hostname)
        }

        private fun resolveDoh(hostname: String): List<InetAddress> {
            val url = "$endpoint?name=${URLEncoder.encode(hostname, "UTF-8")}&type=A"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/dns-json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body()?.string().orEmpty()
                val json = JsonParser.parseString(body).asJsonObject
                val answers = json.getAsJsonArray("Answer") ?: return emptyList()
                val out = ArrayList<InetAddress>(answers.size())
                for (element in answers) {
                    val obj = element.asJsonObject
                    if (obj.get("type")?.asInt != 1) continue
                    val data = obj.get("data")?.asString ?: continue
                    runCatching { InetAddress.getByName(data) }.getOrNull()?.let { out.add(it) }
                }
                return out
            }
        }
    }
}
