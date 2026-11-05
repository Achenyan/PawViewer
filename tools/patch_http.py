import io, sys, re

path = r"D:\work_zone\pawviewer\app\src\main\java\com\pawviewer\core\net\Http.kt"
src = io.open(path, encoding="utf-8").read()

start_marker = "    /** 流式下载到文件（大视频不进内存），回调 (已下载字节, 总字节)。 */"
end_marker = "    // ------------------------------------------------------------- 引擎选择"

i = src.index(start_marker)
j = src.index(end_marker)

new_block = '''    /**
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

'''

src = src[:i] + new_block + src[j:]

# 追加取消异常类型（放在 HttpException 之后）
anchor = 'class HttpException(val code: Int, val bodyText: String? = null) :\n    IOException("HTTP $code" + if (bodyText.isNullOrBlank()) "" else ": ${bodyText.take(180)}")'
if "class DownloadCancelled" not in src:
    src = src.replace(anchor, anchor + '\n\n/** 调用方主动中止下载。 */\nclass DownloadCancelled : IOException("cancelled")')

io.open(path, "w", encoding="utf-8", newline="").write(src)
print("patched, len =", len(src))
