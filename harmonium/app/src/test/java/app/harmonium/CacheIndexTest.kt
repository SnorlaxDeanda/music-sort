package app.harmonium

import app.harmonium.data.cache.CacheEntry
import app.harmonium.data.cache.CacheIndex
import app.harmonium.data.cache.CacheKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheIndexTest {
    @Test
    fun serializeRoundTripPreservesEntries() {
        val index = CacheIndex()
        index.put(sample("a", CacheKind.PERMANENT, bytes = 100, access = 10, pinned = true))
        index.put(sample("b", CacheKind.ROLLING, bytes = 200, access = 20))
        val restored = CacheIndex.deserialize(index.serialize())
        assertEquals(2, restored.all().size)
        assertEquals(100L, restored.get("a")?.bytes)
        assertTrue(restored.get("a")?.pinned == true)
        assertEquals(CacheKind.ROLLING, restored.get("b")?.kind)
    }

    @Test
    fun enforceRollingLimitNeverEvictsPinned() {
        val index = CacheIndex()
        index.put(sample("pin", CacheKind.PERMANENT, bytes = 900, access = 1, pinned = true))
        index.put(sample("old", CacheKind.ROLLING, bytes = 400, access = 2))
        index.put(sample("new", CacheKind.ROLLING, bytes = 400, access = 3))

        val removed = index.enforceRollingLimit(maxBytes = 500)
        assertTrue(removed.any { it.trackId == "old" })
        assertNull(index.get("old"))
        assertTrue(index.get("pin")?.pinned == true)
        assertTrue(index.get("new") != null)
    }

    @Test
    fun promoteAndDemoteUpdateKind() {
        val index = CacheIndex()
        index.put(sample("t", CacheKind.ROLLING, bytes = 10, access = 1))
        index.promoteToPermanent("t")
        assertEquals(CacheKind.PERMANENT, index.get("t")?.kind)
        assertTrue(index.get("t")?.pinned == true)
        index.demoteToRolling("t")
        assertEquals(CacheKind.ROLLING, index.get("t")?.kind)
        assertFalse(index.get("t")?.pinned == true)
    }

    @Test
    fun statsSplitByKind() {
        val index = CacheIndex()
        index.put(sample("p", CacheKind.PERMANENT, bytes = 10, access = 1, pinned = true))
        index.put(sample("r", CacheKind.ROLLING, bytes = 20, access = 2))
        index.put(sample("b", CacheKind.PLAYBACK, bytes = 5, access = 3))
        val stats = index.stats()
        assertEquals(10L, stats.permanentBytes)
        assertEquals(20L, stats.rollingBytes)
        assertEquals(5L, stats.playbackBytes)
        assertEquals(3, stats.totalCount)
    }

    @Test
    fun escapesPipesInTitles() {
        val index = CacheIndex()
        index.put(
            sample("id", CacheKind.ROLLING, bytes = 1, access = 1).copy(
                title = "A|B",
                artistName = "C\\|D",
            ),
        )
        val restored = CacheIndex.deserialize(index.serialize())
        assertEquals("A|B", restored.get("id")?.title)
    }

    private fun sample(
        id: String,
        kind: CacheKind,
        bytes: Long,
        access: Long,
        pinned: Boolean = kind == CacheKind.PERMANENT,
    ) = CacheEntry(
        trackId = id,
        title = "Title $id",
        artistName = "Artist",
        sourceUrl = "https://example.com/$id.mp3",
        fileName = "$id.mp3",
        bytes = bytes,
        kind = kind,
        createdAt = access,
        lastAccessAt = access,
        pinned = pinned,
    )
}
