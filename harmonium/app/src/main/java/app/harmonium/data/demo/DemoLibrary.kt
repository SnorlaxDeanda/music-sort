package app.harmonium.data.demo

import app.harmonium.data.model.Album
import app.harmonium.data.model.Artist
import app.harmonium.data.model.LibrarySnapshot
import app.harmonium.data.model.MediaKind
import app.harmonium.data.model.MediaProviderConfig
import app.harmonium.data.model.Playlist
import app.harmonium.data.model.ProviderType
import app.harmonium.data.model.SmartRule
import app.harmonium.data.model.Track

/**
 * Seed library used for offline demo / first-run experience.
 * Stream URLs point at short public-domain samples when available.
 */
object DemoLibrary {
    const val PROVIDER_ID = "demo"

    private const val SAMPLE_1 =
        "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
    private const val SAMPLE_2 =
        "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"
    private const val SAMPLE_3 =
        "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3"
    private const val SAMPLE_4 =
        "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3"
    private const val SAMPLE_5 =
        "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-13.mp3"

    fun snapshot(): LibrarySnapshot {
        val provider = MediaProviderConfig(
            id = PROVIDER_ID,
            type = ProviderType.DEMO,
            name = "Demo Library",
            enabled = true,
            lastSyncAt = System.currentTimeMillis(),
        )

        val artists = listOf(
            Artist("ar-aurora", "Aurora Circuit", PROVIDER_ID, albumCount = 2, biography = "Synthwave and late-night neon."),
            Artist("ar-harbor", "Harbor Lights", PROVIDER_ID, albumCount = 1, biography = "Warm indie folk from the coast."),
            Artist("ar-velvet", "Velvet Room", PROVIDER_ID, albumCount = 1, biography = "Jazz-adjacent night lounge."),
            Artist("ar-novella", "Novella", PROVIDER_ID, albumCount = 1, biography = "Narrated fiction for long drives."),
        )

        val albums = listOf(
            Album("al-neon", "Neon Atlas", "ar-aurora", "Aurora Circuit", PROVIDER_ID, 2024, "Synthwave", trackCount = 4),
            Album("al-afterglow", "Afterglow Protocols", "ar-aurora", "Aurora Circuit", PROVIDER_ID, 2025, "Electronic", trackCount = 3),
            Album("al-tide", "Tide Charts", "ar-harbor", "Harbor Lights", PROVIDER_ID, 2023, "Folk", trackCount = 3),
            Album("al-midnight", "Midnight Seating", "ar-velvet", "Velvet Room", PROVIDER_ID, 2022, "Jazz", trackCount = 2),
            Album(
                id = "al-novella-1",
                title = "The Quiet Station",
                artistId = "ar-novella",
                artistName = "Novella",
                providerId = PROVIDER_ID,
                year = 2024,
                genre = "Audiobook",
                trackCount = 2,
                kind = MediaKind.AUDIOBOOK,
            ),
        )

        val tracks = listOf(
            track("t1", "Gridlines", "al-neon", "Neon Atlas", "ar-aurora", "Aurora Circuit", 1, 2024, "Synthwave", SAMPLE_1, 5f, 12),
            track("t2", "Signal Bloom", "al-neon", "Neon Atlas", "ar-aurora", "Aurora Circuit", 2, 2024, "Synthwave", SAMPLE_2, 4.5f, 8),
            track("t3", "Glass Freeway", "al-neon", "Neon Atlas", "ar-aurora", "Aurora Circuit", 3, 2024, "Synthwave", SAMPLE_3, 4f, 5),
            track("t4", "Skyline Echo", "al-neon", "Neon Atlas", "ar-aurora", "Aurora Circuit", 4, 2024, "Synthwave", SAMPLE_4, 5f, 20),
            track("t5", "Soft Reboot", "al-afterglow", "Afterglow Protocols", "ar-aurora", "Aurora Circuit", 1, 2025, "Electronic", SAMPLE_5, 4f, 3),
            track("t6", "Latency Dreams", "al-afterglow", "Afterglow Protocols", "ar-aurora", "Aurora Circuit", 2, 2025, "Electronic", SAMPLE_1, 3.5f, 2),
            track("t7", "Cold Cache", "al-afterglow", "Afterglow Protocols", "ar-aurora", "Aurora Circuit", 3, 2025, "Electronic", SAMPLE_2, 4f, 1),
            track("t8", "Pier Thirteen", "al-tide", "Tide Charts", "ar-harbor", "Harbor Lights", 1, 2023, "Folk", SAMPLE_3, 5f, 15),
            track("t9", "Salt & Cedar", "al-tide", "Tide Charts", "ar-harbor", "Harbor Lights", 2, 2023, "Folk", SAMPLE_4, 4f, 7),
            track("t10", "Low Tide Letters", "al-tide", "Tide Charts", "ar-harbor", "Harbor Lights", 3, 2023, "Folk", SAMPLE_5, 4.5f, 9),
            track("t11", "Blue Hour", "al-midnight", "Midnight Seating", "ar-velvet", "Velvet Room", 1, 2022, "Jazz", SAMPLE_1, 5f, 18),
            track("t12", "Last Call Brass", "al-midnight", "Midnight Seating", "ar-velvet", "Velvet Room", 2, 2022, "Jazz", SAMPLE_2, 4f, 6),
            Track(
                id = "t13",
                title = "Chapter 1 — Arrival",
                albumId = "al-novella-1",
                albumTitle = "The Quiet Station",
                artistId = "ar-novella",
                artistName = "Novella",
                providerId = PROVIDER_ID,
                durationMs = 372_000,
                trackNumber = 1,
                year = 2024,
                genre = "Audiobook",
                rating = 5f,
                playCount = 2,
                streamUrl = SAMPLE_3,
                lyrics = "The train arrived late, as if the night itself had overslept.",
                kind = MediaKind.AUDIOBOOK,
                format = "mp3",
            ),
            Track(
                id = "t14",
                title = "Chapter 2 — Platform B",
                albumId = "al-novella-1",
                albumTitle = "The Quiet Station",
                artistId = "ar-novella",
                artistName = "Novella",
                providerId = PROVIDER_ID,
                durationMs = 401_000,
                trackNumber = 2,
                year = 2024,
                genre = "Audiobook",
                rating = 4.5f,
                playCount = 1,
                streamUrl = SAMPLE_4,
                lyrics = "Tickets fluttered like moths under the sodium lamps.",
                kind = MediaKind.AUDIOBOOK,
                format = "mp3",
            ),
        ).mapIndexed { index, t ->
            t.copy(
                lyrics = t.lyrics ?: defaultLyrics(t.title, t.artistName),
                syncedLyrics = syncedStub(t.title),
                coverUrl = coverFor(index),
            )
        }

        val albumsWithCovers = albums.mapIndexed { index, album ->
            album.copy(coverUrl = coverFor(index + 3))
        }

        val playlists = listOf(
            Playlist(
                id = "pl-favorites",
                name = "Favorites",
                providerId = PROVIDER_ID,
                trackIds = tracks.filter { it.rating >= 4.5f }.map { it.id },
            ),
            Playlist(
                id = "pl-night",
                name = "Night Drive",
                providerId = PROVIDER_ID,
                trackIds = listOf("t1", "t2", "t4", "t11", "t5"),
            ),
            Playlist(
                id = "pl-smart-stars",
                name = "Smart · 4★+",
                providerId = PROVIDER_ID,
                trackIds = tracks.filter { it.rating >= 4f && it.kind == MediaKind.MUSIC }.map { it.id },
                isSmart = true,
                smartRules = listOf(SmartRule("rating", ">=", "4"), SmartRule("kind", "=", "MUSIC")),
            ),
            Playlist(
                id = "pl-smart-unplayed",
                name = "Smart · Rediscover",
                providerId = PROVIDER_ID,
                trackIds = tracks.filter { it.playCount <= 3 && it.kind == MediaKind.MUSIC }.map { it.id },
                isSmart = true,
                smartRules = listOf(SmartRule("playCount", "<=", "3")),
            ),
        )

        return LibrarySnapshot(
            providers = listOf(provider),
            artists = artists,
            albums = albumsWithCovers,
            tracks = tracks,
            playlists = playlists,
        )
    }

    private fun track(
        id: String,
        title: String,
        albumId: String,
        albumTitle: String,
        artistId: String,
        artistName: String,
        number: Int,
        year: Int,
        genre: String,
        url: String,
        rating: Float,
        playCount: Int,
    ) = Track(
        id = id,
        title = title,
        albumId = albumId,
        albumTitle = albumTitle,
        artistId = artistId,
        artistName = artistName,
        providerId = PROVIDER_ID,
        durationMs = (210_000L..340_000L).random(),
        trackNumber = number,
        year = year,
        genre = genre,
        rating = rating,
        playCount = playCount,
        streamUrl = url,
        format = "mp3",
        bitrate = 320,
    )

    private fun coverFor(seed: Int): String {
        // Deterministic abstract cover placeholders
        val hues = listOf("0E1A24", "1F3A45", "244A3A", "3A2A1F", "2A2445", "1A2F3A")
        val accent = listOf("7EC8C8", "E8C27A", "D9846A", "8FB7E0", "C9A0DC", "A8D5A2")
        val bg = hues[seed % hues.size]
        val fg = accent[seed % accent.size]
        return "https://placehold.co/600x600/$bg/$fg/png?text=H"
    }

    private fun defaultLyrics(title: String, artist: String): String =
        """
        $title
        — $artist

        Soft neon over wet asphalt
        Your chorus finds the empty lane
        Keep the volume just past midnight
        Let the city hum remain
        """.trimIndent()

    private fun syncedStub(title: String): String =
        """
        [00:00.00] $title
        [00:08.00] Soft neon over wet asphalt
        [00:16.00] Your chorus finds the empty lane
        [00:24.00] Keep the volume just past midnight
        [00:32.00] Let the city hum remain
        """.trimIndent()
}
