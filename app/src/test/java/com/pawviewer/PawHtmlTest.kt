package com.pawviewer

import com.pawviewer.core.net.PawHtml
import org.junit.Assert.assertEquals
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

    @Test
    fun extractsSignedUrlsKeyedByBasename() {
        val map = PawHtml.extractSigned(html)
        assertEquals(2, map.size)
        assertTrue(map.containsKey("2.jpg"))
        assertTrue(map.containsKey("3.jpg"))
        assertEquals(1793837096L, map.getValue("2.jpg").expiresAt)
        assertTrue(map.getValue("2.jpg").url.contains("&s=8827ab60"))
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
