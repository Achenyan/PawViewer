package com.pawviewer

import com.pawviewer.core.PostFilter
import com.pawviewer.core.Util
import com.pawviewer.core.model.Attachment
import com.pawviewer.core.model.Post
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostFilterTest {

    private fun post(title: String, published: String, vararg attachmentNames: String) = Post(
        id = title,
        title = title,
        published = published,
        attachments = attachmentNames.map { Attachment(name = it) },
    )

    private val imagePost = post("pack", "2026-11-01T10:00:00", "a.png", "b.jpg")
    private val videoPost = post("clip", "2026-11-05T10:00:00", "c.mp4")

    @Test
    fun detectsVideoPosts() {
        assertFalse(Util.isVideoPost(imagePost))
        assertTrue(Util.isVideoPost(videoPost))
    }

    @Test
    fun filtersByType() {
        val packOnly = PostFilter(type = PostFilter.TYPE_PACK)
        assertTrue(packOnly.matches(imagePost))
        assertFalse(packOnly.matches(videoPost))

        val videoOnly = PostFilter(type = PostFilter.TYPE_VIDEO)
        assertFalse(videoOnly.matches(imagePost))
        assertTrue(videoOnly.matches(videoPost))
    }

    @Test
    fun filtersByDate() {
        val cutoff = Util.epochOf("2026-11-03T00:00:00")!!
        val after = PostFilter(dateMode = PostFilter.DATE_AFTER, epoch = cutoff)
        assertFalse(after.matches(imagePost))
        assertTrue(after.matches(videoPost))

        val before = PostFilter(dateMode = PostFilter.DATE_BEFORE, epoch = cutoff)
        assertTrue(before.matches(imagePost))
        assertFalse(before.matches(videoPost))
    }

    @Test
    fun inactiveFilterKeepsEverything() {
        val all = listOf(imagePost, videoPost)
        assertEquals(2, PostFilter().apply(all).size)
    }
    @Test
    fun filtersAiTaggedPosts() {
        val ai = post("AI art pack", "2026-11-04T00:00:00", "x.png")
        val normal = post("hand drawn pack", "2026-11-04T00:00:00", "y.png")
        val f = PostFilter(excludeAi = true)
        assertFalse(f.matches(ai))
        assertTrue(f.matches(normal))
    }

    @Test
    fun aiTagIsCaseAndWordSensitive() {
        assertTrue(Util.hasAiTag(post("AI pack", "2026-11-04T00:00:00")))
        assertFalse(Util.hasAiTag(post("ai pack", "2026-11-04T00:00:00")))
        assertFalse(Util.hasAiTag(post("AIR pack", "2026-11-04T00:00:00")))
    }
    @Test
    fun applyReturnsNewList() {
        // ListAdapter 内部异步 diff，绝不能把调用方的可变列表原样交出去
        val source = arrayListOf(imagePost, videoPost)
        val out = PostFilter().apply(source)
        assertTrue(out !== source)
        source.clear()
        assertEquals(2, out.size)
    }
}
