package com.frameglass.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.File
import okhttp3.Call

object Net {
    /** Clients built here all share the one HTTP client in [SharedHttp]. */
    fun client(config: NetConfig, context: Context): MetadataClient {
        val app = context.applicationContext
        val http = SharedHttp.bind(config)
        val mappings = AnimeMappings(File(app.noBackupFilesDir, "anibridge"), http, config, { isOnWifi(app) })
        return FrameMetadataClient(config, http, mappings)
    }

    /** F-S9, on the same one HTTP client. Payloads wait in the cache until they reach `targetDir`. */
    fun subtitles(config: NetConfig, context: Context): SubtitleClient {
        val scratch = File(context.applicationContext.cacheDir, "subtitle-downloads")
        return FrameSubtitleClient(config, SharedHttp.bind(config), scratch)
    }

    /**
     * For Coil: `OkHttpNetworkFetcherFactory(callFactory = { Net.callFactory() })`. Left alone, Coil
     * builds its own client, outside the allow-list and the Online features switch. Calls are
     * refused until [client] has run.
     */
    fun callFactory(): Call.Factory = SharedHttp.callFactory

    /** A Wi-Fi the owner marked as metered counts as phone data. */
    private fun isOnWifi(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
