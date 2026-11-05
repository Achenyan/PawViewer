package com.pawviewer.core.net

import android.content.Context
import android.content.SharedPreferences
import com.pawviewer.core.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.UploadDataProviders
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class HttpException(val code: Int, val bodyText: String? = null) :
    IOException("HTTP $code" + if (bodyText.isNullOrBlank()) "" else ": ${bodyText.take(180)}")

/** 调用方主动中止下载。 */
class DownloadCancelled : IOException("cancelled")

enum class Lane { API, THUMB, FULL }

/**
 * 网络层：Cronet（HTTP/3）为主，OkHttp 兜底。
 *
 * 站点被按 SNI 阻断（TCP+TLS 握手即被 RST），而 HTTP/3 的 SNI 在 QUIC 里是加密的，
 * 所以直连走 Cronet + QUIC 可以正常访问；配置了应用内代理时仍然走 OkHttp。
 */
object Http {

    const val HOST = "https://pawchive.pw"
    const val API = "$HOST/api/v1"

    private val QUIC_HOSTS = listOf(
        "pawchive.pw",
        "img.pawchive.pw",
        "file.pawchive.pw",
        "t1.pawchive.pw",
    )

    private val JSON: MediaType? = MediaType.parse("application/json")

    private lateinit var appContext: Context
    private lateinit var cookieJar: PersistentCookieJar
    private lateinit var okClient: OkHttpClient
    private var cronet: CronetEngine? = null

    private val executor: Executor = Executors.newFixedThreadPool(4)

    fun init(c: Context) {
        appContext = c.applicationContext
        cookieJar = PersistentCookieJar(c.getSharedPreferences("paw_cookies", Context.MODE_PRIVATE))
        rebuild()
    }

    /** 代理 / UA / DNS 变更后重建两个客户端。 */
    fun rebuild() {
        okClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .dns(NetConfig.dns())
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", Prefs.userAgent)
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .build()
                chain.proceed(req)
            }
            .apply { NetConfig.proxy()?.let { proxy(it) } }
            .build()

        cronet = runCatching {
            CronetEngine.Builder(appContext)
                .enableQuic(true)
                .enableHttp2(true)
                .enableBrotli(true)
                .apply { QUIC_HOSTS.forEach { addQuicHint(it, 443, 443) } }
                .apply {
                    runCatching {
                        setStoragePath(File(appContext.cacheDir, "cronet").absolutePath)
                    }
                }
                .build()
        }.getOrNull()

        Lanes.applyStrict(Prefs.strictRate, Prefs.concurrency)
    }

    /** 供播放器复用同一个 Cronet 引擎（HTTP/3 直连）。 */
    fun cronetEngine(): CronetEngine? = cronet

    fun hasCookies(): Boolean = cookieJar.hasCookies()

    fun clearCookies() = cookieJar.clear()

    fun cookieHeader(): String = cookieJar.dump()

    /** 从外部（例如 WebView）导入 Cookie。 */
    fun importCookies(url: String, header: String?) {
        if (header.isNullOrBlank()) return
        val httpUrl = HttpUrl.parse(url) ?: return
        val list = header.split(";").mapNotNull { part ->
            val trimmed = part.trim()
            val index = trimmed.indexOf('=')
            if (index <= 0) return@mapNotNull null
            runCatching {
                Cookie.Builder()
                    .name(trimmed.substring(0, index).trim())
                    .value(trimmed.substring(index + 1).trim())
                    .hostOnlyDomain(httpUrl.host())
                    .path("/")
                    .expiresAt(System.currentTimeMillis() + 30L * 24 * 3600 * 1000)
                    .build()
            }.getOrNull()
        }
        if (list.isNotEmpty()) cookieJar.saveFromResponse(httpUrl, list)
    }

    // ---------------------------------------------------------------- text

    suspend fun getText(url: String, referer: String? = null, lane: Lane = Lane.API): String =
        String(getBytes(url, referer, lane), Charsets.UTF_8)

    suspend fun api(path: String, params: Map<String, String> = emptyMap()): String {
        val builder = HttpUrl.parse(API + path)?.newBuilder()
            ?: throw IOException("bad url: $path")
        params.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return getText(builder.build().toString(), referer = HOST)
    }

    suspend fun post(path: String): String = write("POST", path)

    suspend fun delete(path: String): String = write("DELETE", path)

    private suspend fun write(method: String, path: String): String =
        withContext(Dispatchers.IO) {
            Lanes.api.acquire()
            val response = request(
                url = API + path,
                method = method,
                headers = mapOf("Accept" to "application/json", "Referer" to HOST),
                body = ByteArray(0),
                contentType = "application/json",
            )
            if (response.status !in 200..299) throw HttpException(response.status, response.text())
            response.text()
        }

    /** 表单 POST（登录用），跟随重定向。 */
    suspend fun postForm(url: String, fields: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            Lanes.api.acquire()
            val encoded = fields.entries.joinToString("&") { (k, v) ->
                "${java.net.URLEncoder.encode(k, "UTF-8")}=${java.net.URLEncoder.encode(v, "UTF-8")}"
            }
            val response = request(
                url = url,
                method = "POST",
                headers = mapOf(
                    "Referer" to HOST,
                    "Accept" to "text/html,application/xhtml+xml",
                ),
                body = encoded.toByteArray(),
                contentType = "application/x-www-form-urlencoded",
            )
            response.text()
        }

    // --------------------------------------------------------------- bytes

    suspend fun getBytes(
        url: String,
        referer: String? = null,
        lane: Lane = Lane.API,
    ): ByteArray = withContext(Dispatchers.IO) {
        val gate = when (lane) {
            Lane.API -> Lanes.api
            Lane.THUMB -> Lanes.thumbGate
            Lane.FULL -> Lanes.fullGate
        }
        val semaphore = when (lane) {
            Lane.API -> null
            Lane.THUMB -> Lanes.thumbConcurrency
            Lane.FULL -> Lanes.fullConcurrency
        }
        val fetch: suspend () -> ByteArray = {
            var lastError: Exception? = null
            var result: ByteArray? = null
            for (attempt in 0..2) {
                try {
                    gate.acquire()
                    val headers = LinkedHashMap<String, String>()
                    headers["Accept-Language"] = "zh-CN,zh;q=0.9,en;q=0.8"
                    if (referer != null) headers["Referer"] = referer
                    val response = request(url, "GET", headers)
                    if (response.status !in 200..299) {
                        throw HttpException(response.status, response.text())
                    }
                    result = response.body
                    break
                } catch (e: HttpException) {
                    lastError = e
                    if (e.code == 429 || e.code == 503 || e.code == 502) {
                        delay(1200L * (attempt + 1))
                        continue
                    }
                    throw e
                } catch (e: IOException) {
                    lastError = e
                    delay(600L * (attempt + 1))
                }
            }
            result ?: throw (lastError ?: IOException("request failed"))
        }
        if (semaphore != null) semaphore.withPermit { fetch() } else fetch()
    }

    suspend fun fetchMedia(url: String, lane: Lane = Lane.FULL): ByteArray =
        getBytes(url, HOST, lane)

    /**
     * 流式下载到文件（大视频不进内存）。
     *
     * @param resume 为 true 时若目标文件已存在，会带 Range 头续传、追加写入（视频用）。
     * @param onProgress 返回 false 表示调用方要求中止（取消），会抛 DownloadCancelled。
     *
     * 图片调用方传 resume=false，并自己用 .part 临时文件，保证「没下完的那张不留半张」。
     */
    suspend fun downloadToFile(
        url: String,
        target: File,
        resume: Boolean = false,
        onProgress: ((Long, Long) -> Boolean)? = null,
    ): File = withContext(Dispatchers.IO) {
        Lanes.fullGate.acquire()
        target.parentFile?.mkdirs()
        val startOffset = if (resume && target.exists()) target.length() else 0L
        if (!resume && target.exists()) target.delete()
        val engine = if (NetConfig.proxy() == null) cronet else null
        if (engine != null) {
            downloadWithCronet(engine, url, target, startOffset, onProgress)
        } else {
            val builder = Request.Builder().url(url).header("Referer", HOST)
            if (startOffset > 0) builder.header("Range", "bytes=" + startOffset + "-")
            okClient.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) throw HttpException(resp.code(), null)
                val len = resp.body()?.contentLength() ?: -1L
                val total = if (len >= 0) len + startOffset else -1L
                var written = startOffset
                resp.body()?.byteStream()?.use { input ->
                    java.io.FileOutputStream(target, startOffset > 0).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            written += read
                            if (onProgress?.invoke(written, total) == false) {
                                throw DownloadCancelled()
                            }
                        }
                    }
                }
            }
            target
        }
    }

    private fun downloadWithCronet(
        engine: CronetEngine,
        url: String,
        target: File,
        startOffset: Long,
        onProgress: ((Long, Long) -> Boolean)?,
    ): File {
        val latch = java.util.concurrent.CountDownLatch(1)
        var failure: Exception? = null
        var output: java.io.OutputStream? = null
        var written = startOffset
        var total = -1L

        val callback = object : UrlRequest.Callback() {
            override fun onRedirectReceived(request: UrlRequest, info: UrlResponseInfo, newLocationUrl: String) {
                request.followRedirect()
            }

            override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
                if (info.httpStatusCode !in 200..299) {
                    failure = HttpException(info.httpStatusCode, null)
                    runCatching { request.cancel() }
                    latch.countDown()
                    return
                }
                val len = info.allHeaders.entries
                    .firstOrNull { it.key.equals("content-length", true) }
                    ?.value?.firstOrNull()?.toLongOrNull() ?: -1L
                total = if (len >= 0) len + startOffset else -1L
                output = java.io.FileOutputStream(target, startOffset > 0)
                request.read(ByteBuffer.allocateDirect(64 * 1024))
            }

            override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, byteBuffer: ByteBuffer) {
                byteBuffer.flip()
                if (byteBuffer.hasRemaining()) {
                    val chunk = ByteArray(byteBuffer.remaining())
                    byteBuffer.get(chunk)
                    runCatching { output?.write(chunk) }
                    written += chunk.size
                    if (onProgress?.invoke(written, total) == false) {
                        failure = DownloadCancelled()
                        runCatching { request.cancel() }
                        latch.countDown()
                        return
                    }
                }
                request.read(ByteBuffer.allocateDirect(64 * 1024))
            }

            override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
                latch.countDown()
            }

            override fun onFailed(request: UrlRequest, info: UrlResponseInfo, error: CronetException) {
                if (failure !is DownloadCancelled) failure = HttpException(0, error.message)
                latch.countDown()
            }

            override fun onCanceled(request: UrlRequest, info: UrlResponseInfo) {
                latch.countDown()
            }
        }

        val builder = engine.newUrlRequestBuilder(url, callback, executor)
            .addHeader("User-Agent", Prefs.userAgent)
            .addHeader("Referer", HOST)
        if (startOffset > 0) builder.addHeader("Range", "bytes=" + startOffset + "-")
        val request = builder.build()
        request.start()
        latch.await()
        runCatching { output?.flush(); output?.close() }

        failure?.let { throw it }
        if (written == 0L) throw IOException("empty response")
        return target
    }

    // ------------------------------------------------------------- 引擎选择

    private class Response(val status: Int, val body: ByteArray) {
        fun text(): String = String(body, Charsets.UTF_8)
    }

    private suspend fun request(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: ByteArray? = null,
        contentType: String? = null,
    ): Response {
        // 配了应用内代理就走 OkHttp（代理对 QUIC 无效）；Cronet 不可用时也退回 OkHttp
        if (NetConfig.proxy() != null || cronet == null) {
            return okhttpRequest(url, method, headers, body, contentType)
        }
        return try {
            cronetRequest(url, method, headers, body, contentType)
        } catch (e: HttpException) {
            throw e
        } catch (e: Exception) {
            // QUIC 被挡时退回 TCP（例如系统级 VPN 环境下）
            android.util.Log.w("PawViewer", "cronet failed, fallback to okhttp: ${e.message}")
            okhttpRequest(url, method, headers, body, contentType)
        }
    }

    private suspend fun okhttpRequest(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: ByteArray?,
        contentType: String?,
    ): Response {
        val builder = Request.Builder().url(url)
        headers.forEach { (k, v) -> builder.header(k, v) }
        when (method) {
            "POST" -> builder.post(
                RequestBody.create(contentType?.let { MediaType.parse(it) } ?: JSON, body ?: ByteArray(0))
            )

            "DELETE" -> builder.delete()
            else -> builder.get()
        }
        okClient.newCall(builder.build()).execute().use { resp ->
            return Response(resp.code(), resp.body()?.bytes() ?: ByteArray(0))
        }
    }

    private suspend fun cronetRequest(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: ByteArray?,
        contentType: String?,
    ): Response = suspendCancellableCoroutine { cont ->
        val sink = ByteArrayOutputStream()
        var status = 0
        var finished = false

        fun succeed() {
            if (finished) return
            finished = true
            cont.resume(Response(status, sink.toByteArray()))
        }

        fun fail(error: Throwable) {
            if (finished) return
            finished = true
            cont.resumeWithException(error)
        }

        val callback = object : UrlRequest.Callback() {
            override fun onRedirectReceived(
                request: UrlRequest,
                info: UrlResponseInfo,
                newLocationUrl: String,
            ) {
                // 登录的 302 上才有 Set-Cookie，必须在这里收下
                storeCookies(url, info.allHeaders)
                request.followRedirect()
            }

            override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
                status = info.httpStatusCode
                storeCookies(url, info.allHeaders)
                request.read(ByteBuffer.allocateDirect(READ_CHUNK))
            }

            override fun onReadCompleted(
                request: UrlRequest,
                info: UrlResponseInfo,
                byteBuffer: ByteBuffer,
            ) {
                byteBuffer.flip()
                if (byteBuffer.hasRemaining()) {
                    val chunk = ByteArray(byteBuffer.remaining())
                    byteBuffer.get(chunk)
                    sink.write(chunk)
                }
                request.read(ByteBuffer.allocateDirect(READ_CHUNK))
            }

            override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
                succeed()
            }

            override fun onFailed(
                request: UrlRequest,
                info: UrlResponseInfo,
                error: CronetException,
            ) {
                fail(HttpException(0, error.message))
            }

            override fun onCanceled(request: UrlRequest, info: UrlResponseInfo) {
                fail(IOException("canceled"))
            }
        }

        val engine = cronet ?: run {
            fail(IOException("cronet unavailable"))
            return@suspendCancellableCoroutine
        }
        val builder = engine.newUrlRequestBuilder(url, callback, executor)
            .setHttpMethod(method)
            .addHeader("User-Agent", Prefs.userAgent)

        headers.forEach { (k, v) -> builder.addHeader(k, v) }

        val cookie = cookieHeaderFor(url)
        if (cookie.isNotEmpty()) builder.addHeader("Cookie", cookie)

        if (body != null) {
            if (contentType != null) builder.addHeader("Content-Type", contentType)
            builder.setUploadDataProvider(UploadDataProviders.create(body), executor)
        }

        val request = runCatching { builder.build() }.getOrElse {
            fail(it)
            return@suspendCancellableCoroutine
        }
        cont.invokeOnCancellation { runCatching { request.cancel() } }
        request.start()
    }

    private fun cookieHeaderFor(url: String): String {
        val httpUrl = HttpUrl.parse(url) ?: return ""
        return cookieJar.loadForRequest(httpUrl).joinToString("; ") { "${it.name()}=${it.value()}" }
    }

    private fun storeCookies(url: String, headers: Map<String, List<String>>) {
        val httpUrl = HttpUrl.parse(url) ?: return
        val raw = headers.entries
            .filter { it.key.equals("Set-Cookie", true) }
            .flatMap { it.value }
        if (raw.isEmpty()) return
        val cookies = raw.mapNotNull { parseSetCookie(httpUrl, it) }
        if (cookies.isNotEmpty()) cookieJar.saveFromResponse(httpUrl, cookies)
    }

    /** Flask 的 Set-Cookie 带 SameSite/Expires，OkHttp 的解析器偶尔判为非法，这里补一个手写解析。 */
    private fun parseSetCookie(httpUrl: HttpUrl, raw: String): Cookie? {
        runCatching { Cookie.parse(httpUrl, raw) }.getOrNull()?.let { return it }
        return runCatching {
            val parts = raw.split(';')
            val pair = parts.first().trim()
            val index = pair.indexOf('=')
            if (index <= 0) return null
            val name = pair.substring(0, index).trim()
            val value = pair.substring(index + 1).trim()
            var path = "/"
            var expiresAt = Long.MAX_VALUE
            var secure = false
            var httpOnly = false
            for (part in parts.drop(1)) {
                val attr = part.trim()
                when {
                    attr.startsWith("Path=", true) -> path = attr.substringAfter('=').trim()
                    attr.startsWith("Expires=", true) -> {
                        val text = attr.substringAfter('=').trim()
                        expiresAt = runCatching {
                            java.time.ZonedDateTime.parse(
                                text,
                                java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
                            ).toInstant().toEpochMilli()
                        }.getOrDefault(Long.MAX_VALUE)
                    }

                    attr.startsWith("Max-Age=", true) -> {
                        attr.substringAfter('=').trim().toLongOrNull()?.let {
                            expiresAt = System.currentTimeMillis() + it * 1000
                        }
                    }

                    attr.startsWith("Secure", true) -> secure = true
                    attr.startsWith("HttpOnly", true) -> httpOnly = true
                }
            }
            Cookie.Builder()
                .name(name)
                .value(value)
                .hostOnlyDomain(httpUrl.host())
                .path(path)
                .expiresAt(expiresAt)
                .apply {
                    if (secure) secure()
                    if (httpOnly) httpOnly()
                }
                .build()
        }.getOrNull()
    }

    /** 设置页的「测试连接」用。 */
    suspend fun testConnection(): String = getText("$API/app_version", referer = HOST).trim().take(40)

    private const val READ_CHUNK = 64 * 1024
}

/** 持久化 Cookie，用于站点账号会话（收藏需要）。 */
class PersistentCookieJar(private val sp: SharedPreferences) : CookieJar {

    private val lock = Any()
    private val cookies = ArrayList<Cookie>()

    init {
        sp.getStringSet("jar", emptySet())?.forEach { line ->
            runCatching {
                val url = HttpUrl.parse(line.substringBefore('\t'))
                val cookie = url?.let { Cookie.parse(it, line.substringAfter('\t')) }
                if (cookie != null && cookie.expiresAt() > System.currentTimeMillis()) cookies.add(cookie)
            }
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(lock) {
            for (c in cookies) {
                this.cookies.removeAll {
                    it.name() == c.name() && it.domain() == c.domain() && it.path() == c.path()
                }
                if (c.expiresAt() > System.currentTimeMillis()) this.cookies.add(c)
            }
            persist()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
        val now = System.currentTimeMillis()
        cookies.filter { it.expiresAt() > now && it.matches(url) }
    }

    private fun persist() {
        val set = HashSet<String>()
        for (c in cookies) {
            val host = c.domain().removePrefix(".")
            set.add("https://$host${c.path()}\t${c}")
        }
        sp.edit().putStringSet("jar", set).apply()
    }

    fun hasCookies(): Boolean = synchronized(lock) { cookies.isNotEmpty() }

    fun dump(): String = synchronized(lock) { cookies.joinToString("; ") { "${it.name()}=${it.value()}" } }

    fun clear() = synchronized(lock) {
        cookies.clear()
        sp.edit().remove("jar").apply()
    }
}
