package com.frameglass.player

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.serviceLoaderEnabled
import com.frameglass.data.FrameData
import com.frameglass.data.api.FrameServices
import com.frameglass.data.api.FrameServicesOwner
import com.frameglass.net.Net
import com.frameglass.player.model.UiModel
import com.frameglass.player.model.decodesArt
import com.frameglass.player.quick.QuickResume
import com.frameglass.player.nav.AppLinks
import com.frameglass.playerui.PlayerHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private const val MaxImageMemoryBytes = 256L * 1024 * 1024

class FrameApp : Application(), FrameServicesOwner, SingletonImageLoader.Factory, PlayerHost {
    override lateinit var services: FrameServices
        private set

    internal val ui: UiModel by lazy(LazyThreadSafetyMode.NONE) {
        UiModel(services, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate), artDecodes = { decodesArt(it) })
    }

    override fun onCreate() {
        super.onCreate()
        services = FrameData.create(this)
        registerActivityLifecycleCallbacks(CatchUpOnOpen { services.library.startCatchUp() })
        // Launcher shortcuts and the widget follow progress and the library, after each quiet moment.
        QuickResume.start(this, services.library, CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }

    /** The sites line under the online subtitle results opens the keys page through this. */
    override fun subtitleKeysIntent(context: Context): Intent = AppLinks.subtitleKeys(context)

    // The service loader stays off, so Coil cannot build its own client outside the allow-list. Only the
    // Fix match choices load over the network, and Coil keeps nothing from TMDB on disk (F-M11).
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .serviceLoaderEnabled(false)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { Net.callFactory() })) }
            .memoryCache { MemoryCache.Builder().maxSizeBytes(MaxImageMemoryBytes).build() }
            .diskCache { null }
            .build()
}

/** Runs [open] each time the app comes to the foreground, the primary path of brief 4.7. */
private class CatchUpOnOpen(private val open: () -> Unit) : Application.ActivityLifecycleCallbacks {
    private var started = 0

    override fun onActivityStarted(activity: Activity) {
        if (started++ == 0) open()
    }

    override fun onActivityStopped(activity: Activity) {
        started = (started - 1).coerceAtLeast(0)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
