package com.pawviewer

import android.os.Build
import com.pawviewer.core.media.MediaResolver
import com.pawviewer.core.net.Http
import com.pawviewer.core.net.Lane
import com.pawviewer.core.net.PawApi
import com.pawviewer.core.net.PawHtml
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 真·联网集成测试：走一遍「列表 → 详情 → 帖子网页签名 → 下载原图」的完整链路。
 *
 * 注意：Robolectric 里的 socket 实现连不上外网（OkHttp 会走 AndroidPlatform），
 * 所以这里默认忽略。要跑的话请用 instrumentation 或在真机上手动验证：
 *   $env:PAWVIEWER_LIVE_TEST='1'
 */
@Ignore("Robolectric socket 无法访问外网，请在真机 / instrumentation 下运行")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class LiveApiTest {

    @Test
    fun feedDetailSignedUrlAndDownload() = runBlocking {
        assumeTrue(System.getenv("PAWVIEWER_LIVE_TEST") == "1")

        val posts = PawApi.recent("", 0)
        assertTrue("feed should not be empty", posts.isNotEmpty())

        val candidate = posts.firstOrNull { it.file?.path != null } ?: posts.first()
        val detail = PawApi.post(
            candidate.service.orEmpty(),
            candidate.user.orEmpty(),
            candidate.id.orEmpty(),
        )
        assertNotNull("post detail", detail)
        val post = detail!!

        val images = MediaResolver.images(post)
        assertTrue("post should expose images", images.isNotEmpty())

        // 原图直链必须来自帖子网页里的签名表
        val html = PawApi.postPageHtml(post.service.orEmpty(), post.id.orEmpty())
        val signed = PawHtml.extractSigned(html).byName
        println("post=${post.key} images=${images.size} signed=${signed.size}")

        val first = images.first()
        val url = MediaResolver.bestImageUrl(post, first)
        assertNotNull("resolved image url", url)
        println("resolved url=${url!!.take(80)}")

        val bytes = Http.fetchMedia(url, Lane.FULL)
        assertTrue("image bytes", bytes.size > 512)
        println("downloaded ${bytes.size} bytes")
    }
}
