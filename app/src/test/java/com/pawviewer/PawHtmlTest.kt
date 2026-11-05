package com.pawviewer

import com.pawviewer.core.net.PawHtml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PawHtmlTest {

    private val html = """
        <div class="post__content">
          <img src="https://img.pawchive.pw/thumbnail/data/34/0c/aaa.jpg">
          <img src="/data/aa/bb/ccc.png">
          <a href="https://t1.pawchive.pw/f/461b527bff50a06f/2.jpg?e=1793837096&amp;s=8827ab60">x</a>
          <a href="https://t1.pawchive.pw/f/461b527bff50a06f/3.jpg?e=1793837096&s=deadbeef">y</a>
        </div>
    """.trimIndent()

    private val videoHtml = """
        <div class="post__content">
          <a href="https://t1.pawchive.pw/d/8e3c0ebb17edf42c/778091426.mp4?e=1793909459&s=305be259">file</a>
          <source src="https://t1.pawchive.pw/v/8e3c0ebb17edf42c/master.m3u8?e=1793909459&s=59b9b240">
        </div>
    """.trimIndent()

    @Test
    fun extractsSignedUrlsKeyedByBasename() {
        val table = PawHtml.extractSigned(html)
        assertEquals(2, table.byName.size)
        assertTrue(table.byName.containsKey("2.jpg"))
        assertTrue(table.byName.containsKey("3.jpg"))
        assertEquals(1793837096L, table.byName.getValue("2.jpg").expiresAt)
        assertTrue(table.byName.getValue("2.jpg").url.contains("&s=8827ab60"))
    }

    @Test
    fun pairsVideoFileWithHlsPlaylistByHash() {
        val table = PawHtml.extractSigned(videoHtml)
        assertEquals("8e3c0ebb17edf42c", table.byName.getValue("778091426.mp4").hash)
        assertNotNull(table.hlsByHash["8e3c0ebb17edf42c"])
        // 播放优先给 mp4 直链
        assertTrue(table.videoUrlFor("778091426.mp4")!!.contains(".mp4"))
        // HLS 也解析出来了，作为兜底
        assertTrue(table.hlsByHash.getValue("8e3c0ebb17edf42c").url.contains("master.m3u8"))
    }

    @Test
    fun extractsContentImagePaths() {
        val paths = PawHtml.contentImagePaths(html)
        assertEquals(listOf("/34/0c/aaa.jpg", "/aa/bb/ccc.png"), paths)
    }

    @Test
    fun toDataPathRejectsForeignUrls() {
        assertEquals("/aa/bb/x.png", PawHtml.toDataPath("https://pawchive.pw/data/aa/bb/x.png"))
        assertEquals(null, PawHtml.toDataPath("https://example.com/aa/bb/x.png"))
        assertEquals(null, PawHtml.toDataPath("/data/"))
    }
}
