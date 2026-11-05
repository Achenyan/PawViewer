package com.pawviewer

import com.pawviewer.core.Util
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilTest {

    @Test
    fun formatsIsoDate() {
        assertEquals("2026-11-04 15:00", Util.formatDate("2026-11-04T15:00:12"))
    }

    @Test
    fun formatsEpochSeconds() {
        assertTrue(Util.formatDate("1785423600").startsWith("2026-"))
    }

    @Test
    fun formatsServiceNames() {
        assertEquals("Patreon", Util.serviceName("patreon"))
        assertEquals("Fanbox", Util.serviceName("fanbox"))
        assertEquals("SubscribeStar", Util.serviceName("subscribestar"))
    }

    @Test
    fun humanSize() {
        assertEquals("", Util.humanSize(0))
        assertEquals("512 B", Util.humanSize(512))
        assertEquals("1.0 KB", Util.humanSize(1024))
        assertEquals("1.5 MB", Util.humanSize(1024L * 1024 * 3 / 2))
    }

    @Test
    fun sha1IsStable() {
        assertEquals(40, Util.sha1("paw").length)
        assertEquals(Util.sha1("paw"), Util.sha1("paw"))
    }

    @Test
    fun sanitizesFileNames() {
        assertEquals("a_b.jpg", Util.safeFileName("a/b.jpg", "fallback.jpg"))
        assertEquals("fallback.jpg", Util.safeFileName("   ", "fallback.jpg"))
        assertEquals("_x_y.jpg", Util.safeFileName("../x:y.jpg", "f.jpg"))
    }
    @Test
    fun fuzzySearchToleratesOneTypoButRanksBehind() {
        assertEquals(0, Util.fuzzyScore("Alex pack", "alex"))
        assertEquals(1, Util.fuzzyScore("alex", "aiex"))
        assertEquals(null, Util.fuzzyScore("alex", "zzzz"))
    }

    @Test
    fun levenshteinBasics() {
        assertEquals(0, Util.levenshtein("alex", "alex"))
        assertEquals(1, Util.levenshtein("alex", "aiex"))
        assertEquals(4, Util.levenshtein("", "alex"))
    }
}
