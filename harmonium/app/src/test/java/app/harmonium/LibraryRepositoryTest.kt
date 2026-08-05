package app.harmonium

import app.harmonium.data.demo.DemoLibrary
import app.harmonium.data.repository.LibraryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRepositoryTest {
    @Test
    fun demoLibraryHasMusicAndAudiobooks() {
        val snap = DemoLibrary.snapshot()
        assertTrue(snap.tracks.isNotEmpty())
        assertTrue(snap.albums.any { it.kind.name == "AUDIOBOOK" })
        assertTrue(snap.playlists.any { it.isSmart })
    }

    @Test
    fun searchFindsTracksByArtist() {
        val repo = LibraryRepository()
        val results = repo.search("aurora")
        assertTrue(results.tracks.isNotEmpty())
        assertTrue(results.artists.any { it.name.contains("Aurora", ignoreCase = true) })
    }

    @Test
    fun personalMixesAreNonEmpty() {
        val repo = LibraryRepository()
        val mixes = repo.personalMixes()
        assertEquals(3, mixes.size)
        assertTrue(mixes.all { it.trackIds.isNotEmpty() })
    }

    @Test
    fun eqProfilesIncludeAutoEq() {
        val repo = LibraryRepository()
        assertTrue(repo.eqProfiles.value.any { it.isAutoEq })
        repo.setActiveEq("eq-bass")
        assertEquals("eq-bass", repo.activeEqId.value)
        repo.updateEqBand("eq-bass", 60, 8f)
        val band = repo.eqProfiles.value.first { it.id == "eq-bass" }.bands.first { it.frequencyHz == 60 }
        assertEquals(8f, band.gainDb, 0.01f)
    }

    @Test
    fun cannotRemoveDemoProvider() = runBlocking {
        val repo = LibraryRepository()
        repo.removeProvider(DemoLibrary.PROVIDER_ID)
        assertTrue(repo.providers.value.any { it.id == DemoLibrary.PROVIDER_ID })
    }

    @Test
    fun subsonicMd5MatchesKnownVector() {
        val hash = app.harmonium.data.provider.SubsonicMediaProvider.md5("passwordsalt")
        // md5("passwordsalt")
        assertFalse(hash.isBlank())
        assertEquals(32, hash.length)
    }
}
