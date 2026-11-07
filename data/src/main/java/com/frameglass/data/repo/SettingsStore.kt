package com.frameglass.data.repo

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.frameglass.data.api.AccentColor
import com.frameglass.data.api.ApiKeyKind
import com.frameglass.data.api.AppSettings
import com.frameglass.data.api.SettingsRepository
import com.frameglass.data.api.ThemeMode
import com.frameglass.data.secure.KeyVault
import com.frameglass.net.ApiKey
import com.frameglass.net.NetConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.settingsDataStore by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

private inline fun <reified T : Enum<T>> enumOf(name: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: fallback

private object Prefs {
    val online = booleanPreferencesKey("onlineFeatures")
    val doubleTap = intPreferencesKey("doubleTapSeconds")
    val backgroundAudio = booleanPreferencesKey("backgroundAudio")
    val autoPlayNext = booleanPreferencesKey("autoPlayNext")
    val nightAudio = booleanPreferencesKey("nightAudio")
    val subtitleScale = floatPreferencesKey("subtitleScale")
    val subtitleColor = intPreferencesKey("subtitleColor")
    val subtitleBackground = booleanPreferencesKey("subtitleBackground")
    val subtitleFont = stringPreferencesKey("subtitleFontPath")
    val subtitleOffset = floatPreferencesKey("subtitleBottomOffset")
    val scanWholePhone = booleanPreferencesKey("scanWholePhone")
    val scanHidden = booleanPreferencesKey("scanHiddenFolders")
    val scanNomedia = booleanPreferencesKey("scanNomediaFolders")
    val showUnavailable = booleanPreferencesKey("showUnavailable")
    val tmdbBaseUrl = stringPreferencesKey("tmdbBaseUrl")
    val themeMode = stringPreferencesKey("themeMode")
    val accent = stringPreferencesKey("accent")
    val coverTint = booleanPreferencesKey("coverTint")
    val sendReleaseName = booleanPreferencesKey("sendReleaseName")
    val sendFileHash = booleanPreferencesKey("sendFileHash")
    val vaultLockSeconds = intPreferencesKey("vaultLockSeconds")
    val neverSleeping = booleanPreferencesKey("neverSleepingHintDone")
    val onboarding = booleanPreferencesKey("onboardingDone")
    val mediaSignal = stringPreferencesKey("internal.mediaSignal")
    val lastSweepAt = longPreferencesKey("internal.lastSweepAt")
    val clipPassDone = booleanPreferencesKey("internal.clipPassDone")
}

internal class SettingsStore(
    private val context: Context,
    private val vault: KeyVault,
) : SettingsRepository {
    private val store get() = context.settingsDataStore

    /** Set by the wiring: a new key or Online turned on should start matching. */
    @Volatile
    var onMetadataEnabled: () -> Unit = {}

    /** Set by the wiring: a setting that changes where the search goes needs a scan. */
    @Volatile
    var onScanScopeChanged: () -> Unit = {}

    override val settings: Flow<AppSettings> = store.data.map(::read).distinctUntilChanged()

    private fun read(p: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            onlineFeatures = p[Prefs.online] ?: d.onlineFeatures,
            doubleTapSeconds = p[Prefs.doubleTap] ?: d.doubleTapSeconds,
            backgroundAudio = p[Prefs.backgroundAudio] ?: d.backgroundAudio,
            autoPlayNext = p[Prefs.autoPlayNext] ?: d.autoPlayNext,
            nightAudio = p[Prefs.nightAudio] ?: d.nightAudio,
            subtitleScale = p[Prefs.subtitleScale] ?: d.subtitleScale,
            subtitleColor = p[Prefs.subtitleColor] ?: d.subtitleColor,
            subtitleBackground = p[Prefs.subtitleBackground] ?: d.subtitleBackground,
            subtitleFontPath = p[Prefs.subtitleFont],
            subtitleBottomOffset = p[Prefs.subtitleOffset] ?: d.subtitleBottomOffset,
            scanWholePhone = p[Prefs.scanWholePhone] ?: d.scanWholePhone,
            scanHiddenFolders = p[Prefs.scanHidden] ?: d.scanHiddenFolders,
            scanNomediaFolders = p[Prefs.scanNomedia] ?: d.scanNomediaFolders,
            showUnavailable = p[Prefs.showUnavailable] ?: d.showUnavailable,
            tmdbBaseUrl = p[Prefs.tmdbBaseUrl] ?: d.tmdbBaseUrl,
            themeMode = enumOf(p[Prefs.themeMode], d.themeMode),
            accent = enumOf(p[Prefs.accent], d.accent),
            coverTint = p[Prefs.coverTint] ?: d.coverTint,
            sendReleaseName = p[Prefs.sendReleaseName] ?: d.sendReleaseName,
            sendFileHash = p[Prefs.sendFileHash] ?: d.sendFileHash,
            vaultLockSeconds = p[Prefs.vaultLockSeconds] ?: d.vaultLockSeconds,
            neverSleepingHintDone = p[Prefs.neverSleeping] ?: d.neverSleepingHintDone,
            onboardingDone = p[Prefs.onboarding] ?: d.onboardingDone,
        )
    }

    private fun write(p: androidx.datastore.preferences.core.MutablePreferences, s: AppSettings) {
        p[Prefs.online] = s.onlineFeatures
        p[Prefs.doubleTap] = s.doubleTapSeconds
        p[Prefs.backgroundAudio] = s.backgroundAudio
        p[Prefs.autoPlayNext] = s.autoPlayNext
        p[Prefs.nightAudio] = s.nightAudio
        p[Prefs.subtitleScale] = s.subtitleScale
        p[Prefs.subtitleColor] = s.subtitleColor
        p[Prefs.subtitleBackground] = s.subtitleBackground
        val font = s.subtitleFontPath
        if (font == null) p.remove(Prefs.subtitleFont) else p[Prefs.subtitleFont] = font
        p[Prefs.subtitleOffset] = s.subtitleBottomOffset
        p[Prefs.scanWholePhone] = s.scanWholePhone
        p[Prefs.scanHidden] = s.scanHiddenFolders
        p[Prefs.scanNomedia] = s.scanNomediaFolders
        p[Prefs.showUnavailable] = s.showUnavailable
        p[Prefs.tmdbBaseUrl] = s.tmdbBaseUrl
        p[Prefs.themeMode] = s.themeMode.name
        p[Prefs.accent] = s.accent.name
        p[Prefs.coverTint] = s.coverTint
        p[Prefs.sendReleaseName] = s.sendReleaseName
        p[Prefs.sendFileHash] = s.sendFileHash
        p[Prefs.vaultLockSeconds] = s.vaultLockSeconds
        p[Prefs.neverSleeping] = s.neverSleepingHintDone
        p[Prefs.onboarding] = s.onboardingDone
    }

    suspend fun current(): AppSettings = settings.first()

    override suspend fun update(change: (AppSettings) -> AppSettings) {
        var enabled = false
        var scopeChanged = false
        store.edit { p ->
            val old = read(p)
            val new = change(old)
            enabled = new.onlineFeatures && !old.onlineFeatures
            scopeChanged = new.scanWholePhone != old.scanWholePhone || new.scanHiddenFolders != old.scanHiddenFolders ||
                new.scanNomediaFolders != old.scanNomediaFolders
            write(p, new)
        }
        if (enabled) onMetadataEnabled()
        if (scopeChanged) onScanScopeChanged()
    }

    override fun hasKey(kind: ApiKeyKind): Flow<Boolean> = vault.has(kind.toNet())

    override suspend fun setKey(kind: ApiKeyKind, value: String?) {
        vault.write(kind.toNet(), value)
        // Subtitle keys work on the next tap, and nothing in the metadata queue waits for them.
        if (!value.isNullOrBlank() && (kind == ApiKeyKind.TMDB || kind == ApiKeyKind.OMDB)) onMetadataEnabled()
    }

    suspend fun mediaSignal(): String? = store.data.first()[Prefs.mediaSignal]

    suspend fun setMediaSignal(value: String) {
        store.edit { it[Prefs.mediaSignal] = value }
    }

    suspend fun lastSweepAt(): Long = store.data.first()[Prefs.lastSweepAt] ?: 0

    suspend fun setLastSweepAt(value: Long) {
        store.edit { it[Prefs.lastSweepAt] = value }
    }

    suspend fun clipPassDone(): Boolean = store.data.first()[Prefs.clipPassDone] == true

    suspend fun setClipPassDone() {
        store.edit { it[Prefs.clipPassDone] = true }
    }

    private fun ApiKeyKind.toNet(): ApiKey = ApiKey.valueOf(name)
}

/** Reads are synchronous for :net, so this keeps the last settings in memory. It stays offline until they load. */
internal class NetConfigImpl(
    scope: CoroutineScope,
    settings: SettingsStore,
    private val vault: KeyVault,
) : NetConfig {
    private val loaded = CompletableDeferred<Unit>()

    @Volatile
    private var current: AppSettings? = null

    init {
        scope.launch {
            settings.settings.collect {
                current = it
                loaded.complete(Unit)
            }
        }
    }

    suspend fun awaitLoaded() {
        loaded.await()
        vault.awaitLoaded()
    }

    override fun key(kind: ApiKey): String? = vault.read(kind)
    override val tmdbBaseUrl: String get() = current?.tmdbBaseUrl ?: AppSettings().tmdbBaseUrl
    override val onlineEnabled: Boolean get() = current?.onlineFeatures == true
}
