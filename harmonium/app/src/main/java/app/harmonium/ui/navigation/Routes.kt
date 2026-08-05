package app.harmonium.ui.navigation

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val PROVIDERS = "providers"
    const val SETTINGS = "settings"
    const val NOW_PLAYING = "now_playing"
    const val EQUALIZER = "equalizer"
    const val QUEUES = "queues"
    const val AUDIOBOOKS = "audiobooks"
    const val ALBUM = "album/{albumId}"
    const val ARTIST = "artist/{artistId}"

    fun album(albumId: String) = "album/$albumId"
    fun artist(artistId: String) = "artist/$artistId"
}
