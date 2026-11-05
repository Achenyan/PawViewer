package com.pawviewer

import com.pawviewer.core.media.MediaKind
import com.pawviewer.core.media.MediaResolver
import com.pawviewer.core.model.Attachment
import com.pawviewer.core.model.FileRef
import com.pawviewer.core.model.Post
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaResolverTest {

    @Test
    fun buildsOrderedItemsFromFileAttachmentsAndContent() {
        val post = Post(
            id = "1",
            user = "2",
            service = "patreon",
            title = "t",
            file = FileRef(name = "cover.png", path = "/aa/bb/cover.png"),
            attachments = listOf(
                Attachment(name = "2.jpg", path = "/cc/dd/2.jpg"),
                Attachment(name = "clip.mp4", deferred = true),
            ),
            content = "<img src=\"https://img.pawchive.pw/thumbnail/data/ee/ff/inline.jpg\">",
        )

        val items = MediaResolver.items(post)
        assertEquals(4, items.size)

        assertEquals(MediaKind.IMAGE, items[0].kind)
        assertEquals("https://file.pawchive.pw/data/aa/bb/cover.png", items[0].directUrl)
        assertEquals("https://img.pawchive.pw/thumbnail/data/aa/bb/cover.png", items[0].thumbUrl)

        assertEquals("https://img.pawchive.pw/thumbnail/data/cc/dd/2.jpg", items[1].thumbUrl)
        assertNull(items[1].directUrl)
        assertEquals("2.jpg", items[1].signedName)

        assertEquals(MediaKind.VIDEO, items[2].kind)
        assertNull(items[2].path)

        assertEquals("/ee/ff/inline.jpg", items[3].path)
        assertEquals(MediaKind.IMAGE, items[3].kind)
    }

    @Test
    fun previewOnlyPostHasNoDirectUrl() {
        val post = Post(
            id = "1",
            user = "2",
            service = "patreon",
            file = FileRef(name = "a.jpg", path = "/x/y/a.jpg", previewOnly = true),
        )
        val items = MediaResolver.items(post)
        assertEquals(1, items.size)
        assertNull(items[0].directUrl)
    }

    @Test
    fun deduplicatesSamePath() {
        val post = Post(
            id = "1",
            user = "2",
            service = "patreon",
            file = FileRef(name = "a.jpg", path = "/x/y/a.jpg"),
            content = "<img src=\"/data/x/y/a.jpg\">",
        )
        assertEquals(1, MediaResolver.items(post).size)
    }

    @Test
    fun detectsKinds() {
        assertEquals(MediaKind.IMAGE, MediaResolver.kindOf("a.JPEG"))
        assertEquals(MediaKind.VIDEO, MediaResolver.kindOf("b.mp4"))
        assertEquals(MediaKind.ARCHIVE, MediaResolver.kindOf("c.7z"))
        assertEquals(MediaKind.DOC, MediaResolver.kindOf("d.psd"))
        assertEquals(MediaKind.OTHER, MediaResolver.kindOf("noext"))
    }
}
