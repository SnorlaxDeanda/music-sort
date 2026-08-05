package app.harmonium

import android.app.Application
import app.harmonium.data.cache.OfflineCacheRepository
import app.harmonium.data.repository.LibraryRepository
import app.harmonium.playback.PlayerController

class HarmoniumApp : Application() {
    lateinit var libraryRepository: LibraryRepository
        private set
    lateinit var offlineCache: OfflineCacheRepository
        private set
    lateinit var playerController: PlayerController
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        libraryRepository = LibraryRepository()
        offlineCache = OfflineCacheRepository(this)
        playerController = PlayerController(this, libraryRepository, offlineCache)
    }

    companion object {
        lateinit var instance: HarmoniumApp
            private set
    }
}
