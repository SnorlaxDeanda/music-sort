# Harmonium

Symfonium-inspired Android music player — open source, no ads, multi-provider.

**Not affiliated with [Symfonium](https://symfonium.app).** This is an independent MVP that explores the same product space: one player for local libraries, self-hosted servers, audiobooks, EQ, and offline-first listening.

## What’s in 0.2.0

- **Material 3 UI** — Home, Library (albums/artists/tracks/playlists/genres), Search, Providers, Settings
- **Demo library** with music + audiobooks, smart playlists, personal mixes, lyrics
- **Media3 / ExoPlayer** playback service with mini player + full Now Playing (queue + lyrics)
- **Multiple media queues** (music vs audiobooks) with per-queue speed / shuffle / repeat
- **Offline cache**
  - Playback lookahead cache while listening
  - Rolling LRU cache with configurable size (256 MB–4 GB)
  - Pinned permanent downloads (album/track Download actions)
  - Auto-cache favorites (4.5★+) and frequently played tracks
  - Wi‑Fi-only download option; local file playback when cached
  - Manage screen: progress, pin/unpin, clear rolling / clear all
- **Provider architecture**
  - Demo / local seed library
  - Jellyfin connection probe (`/System/Info/Public`)
  - Subsonic / OpenSubsonic / Navidrome ping (token auth)
  - Scaffolding for Plex, Audiobookshelf, SMB, WebDAV
- **Equalizer** — 10-band GEQ + AutoEQ-style headphone presets
- **Settings** for gapless, ReplayGain, smart fades, and offline cache entry point

## Build

Requirements: JDK 17+, Android SDK 35.

```bash
export ANDROID_HOME=~/android-sdk   # or your SDK path
cd harmonium
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Install the debug APK:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Roadmap (Symfonium parity targets)

1. Full Jellyfin / OpenSubsonic library sync + streaming URLs  
2. Local MediaStore scanner + custom tag parser  
3. Chromecast / DLNA / Sonos casting  
4. Android Auto, Wear OS, widgets  
5. Parametric EQ + real AutoEQ/APO import  

## License

Apache-2.0 for this MVP code. Symfonium name, branding, and assets remain their owners’.
