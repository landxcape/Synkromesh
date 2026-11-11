package com.landxcape.synkromesh

import android.app.Application
import com.landxcape.synkromesh.BuildConfig
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration
import java.io.File

@HiltAndroidApp
class SynkromeshApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize OpenStreetMap configuration for offline tile caching
        val osmConfig = Configuration.getInstance()
        osmConfig.userAgentValue = "Synkromesh/${BuildConfig.VERSION_NAME}"
        val tileCacheDir = File(filesDir, "osmdroid/tiles")
        if (!tileCacheDir.exists()) {
            tileCacheDir.mkdirs()
        }
        osmConfig.osmdroidTileCache = tileCacheDir
    }
}
