package com.frameglass.data

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.frameglass.data.api.FrameServices
import com.frameglass.data.api.LibraryRepository
import com.frameglass.data.api.MediaManager
import com.frameglass.data.api.OnlineSubtitles
import com.frameglass.data.api.PlaybackRepository
import com.frameglass.data.api.PrivateVault
import com.frameglass.data.api.SettingsRepository
import com.frameglass.data.backup.AutoBackupManager
import com.frameglass.data.backup.BackupManager
import com.frameglass.data.db.FrameDb
import com.frameglass.data.logic.Clock
import com.frameglass.data.logic.SystemClock
import com.frameglass.data.meta.AppLanguage
import com.frameglass.data.meta.CacheSweeper
import com.frameglass.data.meta.MetadataPipeline
import com.frameglass.data.meta.NetworkCheck
import com.frameglass.data.meta.SearchIndexer
import com.frameglass.data.meta.TitleOps
import com.frameglass.data.meta.TitleResolver
import com.frameglass.data.repo.AutoTried
import com.frameglass.data.repo.LibraryRepositoryImpl
import com.frameglass.data.repo.MediaManagerImpl
import com.frameglass.data.repo.NetConfigImpl
import com.frameglass.data.repo.OnlineSubtitlesImpl
import com.frameglass.data.repo.PlaybackRepositoryImpl
import com.frameglass.data.repo.PrivateVaultImpl
import com.frameglass.data.repo.ResourceLabels
import com.frameglass.data.repo.SettingsStore
import com.frameglass.data.repo.SidecarLabels
import com.frameglass.data.repo.SnapshotSource
import com.frameglass.data.repo.WatchLogger
import com.frameglass.data.scan.CatchUp
import com.frameglass.data.scan.ChangeWatcher
import com.frameglass.data.scan.ClipPass
import com.frameglass.data.scan.Enricher
import com.frameglass.data.scan.ImageStore
import com.frameglass.data.scan.MediaIndex
import com.frameglass.data.scan.MediaSignal
import com.frameglass.data.scan.MediaStoreTrash
import com.frameglass.data.scan.ScanProgress
import com.frameglass.data.scan.Scanner
import com.frameglass.data.scan.Volumes
import com.frameglass.data.scan.WorkScheduler
import com.frameglass.data.secure.KeyVault
import com.frameglass.data.secure.VaultAuth
import com.frameglass.net.Net
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

object FrameData {
    @Volatile
    private var instance: DataServices? = null

    /** Safe on the main thread. Nothing here touches disk or network until first use. */
    fun create(context: Context): FrameServices = services(context)

    internal fun services(context: Context): DataServices =
        instance ?: synchronized(this) {
            instance ?: DataServices(context.applicationContext, SystemClock).also { instance = it }
        }
}

/** The manual container per section 4.2: no Hilt. Workers reach it through [of]. */
internal class DataServices(context: Context, clock: Clock) : FrameServices {
    /** Offline playback never depends on background work, so a failure here is logged and must not crash the app. */
    val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineName("frame-data") +
            CoroutineExceptionHandler { _, error -> Log.w("FrameData", "background job failed", error) },
    )

    private val db = FrameDb.open(context)
    private val keys = KeyVault(context, scope)
    private val settingsStore = SettingsStore(context, keys)
    private val config = NetConfigImpl(scope, settingsStore, keys)
    private val client = lazy { Net.client(config, context) }
    private val subtitleClient = lazy { Net.subtitles(config, context) }
    private val volumes = Volumes(context)
    private val images = ImageStore(context)
    private val progress = ScanProgress()
    private val index = SearchIndexer(db)
    private val resolver = TitleResolver(db, index, clock)
    private val ops = TitleOps(db, resolver, index, clock)
    private val labels = ResourceLabels(context)
    private val sidecarLabels = SidecarLabels(context)
    private val work = WorkScheduler(context)

    val pipeline: MetadataPipeline by lazy {
        MetadataPipeline(
            db, client, config, settingsStore, images, ops, index, clock, NetworkCheck(context), AppLanguage(context),
        )
    }

    private val clipPass = ClipPass(db, ops, settingsStore)

    val scanner: Scanner = Scanner(
        db, volumes, MediaIndex(context), settingsStore::current, resolver, clock, progress,
        beforeScan = {
            clipPass.run()
            privateFolder.value.recover()
        },
        onNewRows = { catchUp.onNewRows() },
    )

    val catchUp: CatchUp by lazy {
        CatchUp(
            scope, db, scanner, Enricher(db, volumes, images), pipeline, CacheSweeper(db, images, index, clock),
            MediaSignal(context), settingsStore, work, clock,
        )
    }

    private val privateFolder: Lazy<PrivateVaultImpl> = lazy {
        PrivateVaultImpl(
            context, db, VaultAuth(keys, clock), volumes, scanner, { catchUp.onFoldersChanged() }, resolver, labels,
            sidecarLabels, settingsStore, scope, clock,
        )
    }

    private val snapshots = SnapshotSource(db, settingsStore, volumes, clock, scope)

    private val backup: BackupManager = BackupManager(context, db, settingsStore, images, ops, index, clock)

    val autoBackup: AutoBackupManager = AutoBackupManager(context, backup, clock, scope)

    private val watchLogger = WatchLogger(db, clock)

    override val library: LibraryRepository by lazy {
        LibraryRepositoryImpl(
            context, db, snapshots, labels, settingsStore, volumes, scanner, progress, catchUp, pipeline, images, backup, clock,
            privateFolder, autoBackup,
        )
    }

    override val playback: PlaybackRepository by lazy {
        PlaybackRepositoryImpl(context, db, volumes, labels, pipeline, images, scope, clock, sidecarLabels, privateFolder, watchLogger)
    }

    override val settings: SettingsRepository get() = settingsStore

    override val media: MediaManager by lazy {
        MediaManagerImpl(context, db, snapshots, volumes, MediaStoreTrash(context)) { catchUp }
    }

    override val vault: PrivateVault get() = privateFolder.value

    override val subtitles: OnlineSubtitles by lazy {
        OnlineSubtitlesImpl(db, settingsStore, config, subtitleClient, sidecarLabels, AutoTried(File(context.filesDir, "subs-auto")))
    }

    init {
        settingsStore.onMetadataEnabled = { catchUp.onMetadataEnabled() }
        settingsStore.onServicesChanged = { tmdbOn, imdbOn -> catchUp.onServicesChanged(tmdbOn, imdbOn) }
        settingsStore.onScanScopeChanged = { catchUp.onFoldersChanged() }
        watchChanges(context)
    }

    private fun watchChanges(context: Context) {
        val watcher = ChangeWatcher(
            context,
            volumes,
            onMediaChanged = { catchUp.onMediaChanged() },
            onMounted = { catchUp.onMounted() },
            onUnmounted = { catchUp.onUnmounted(it) },
        )
        scope.launch { runCatching { watcher.startVolumeCallback() } }
        (context as? Application)?.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var started = 0

            override fun onActivityStarted(activity: Activity) {
                started++
                watcher.setVisible(true)
                catchUp.onLanguageCheck()
                if (privateFolder.isInitialized()) privateFolder.value.onAppVisible()
            }

            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                if (started == 0) watcher.setVisible(false)
                if (started == 0 && !activity.isChangingConfigurations && privateFolder.isInitialized()) {
                    privateFolder.value.onAppHidden()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    companion object {
        fun of(context: Context): DataServices = FrameData.services(context)
    }
}
