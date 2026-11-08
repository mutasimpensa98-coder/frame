package com.frameglass.data.api

import android.content.IntentSender
import com.frameglass.data.logic.VaultRefs
import com.frameglass.net.NetResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/*
 * Additions beside the fixed contract in Api.kt. EpisodeRow carries one PlayRef, so the version picker of
 * F-L9 needs its own call, and PlaybackItem is built before online skip times can arrive. TitleCard carries no
 * PlayRef, so a private card needs one here, and DeletePlan has no way to ask Android for a confirmation.
 * LibraryRepository.refreshAll returns nothing, so a swipe's progress needs a flow of its own.
 */

/** One file of a playable. Play it with PlayRef(fileRowId = fileRowId). */
data class FileVersion(
    val fileRowId: Long,
    val name: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val videoCodec: String?,
    val hdrType: String?,
    val audioSummary: String?,
    val available: Boolean,
    /** The highest resolution available, which plays when no version is picked. */
    val isDefault: Boolean,
    /** F-L10: never played and added in the last 7 days. */
    val isNew: Boolean = false,
    /** F-L10: a sidecar or embedded subtitle exists. */
    val hasSubtitle: Boolean = false,
)

/** Implemented by the object behind [LibraryRepository]. */
interface VersionSource {
    suspend fun versions(playableKey: String): List<FileVersion>
}

/** F-L9. Returns more than one entry only when several files hold the same episode. */
suspend fun LibraryRepository.versions(playableKey: String): List<FileVersion> =
    (this as? VersionSource)?.versions(playableKey).orEmpty()

/** Implemented by the object behind [PlaybackRepository]. */
interface SegmentSource {
    suspend fun segmentsAfterFetch(item: PlaybackItem): List<SkipSegment>
}

/** F-S4. Waits for the online lookup that opening [item] started, then reads its skip times again. */
suspend fun PlaybackRepository.segmentsAfterFetch(item: PlaybackItem): List<SkipSegment> =
    (this as? SegmentSource)?.segmentsAfterFetch(item) ?: item.segments

/**
 * Plays a card from [PrivateVault.items]: the resume point of that title, and PlaybackItem.next walks its episodes.
 * PlaybackRepository.open returns null for this while the folder is locked.
 */
fun TitleCard.privatePlayRef(): PlayRef = PlayRef(playableKey = VaultRefs.group(id))

/** moveIn when no password is set, and moveOut while the folder is locked. */
class VaultClosedException : IllegalStateException()

/** Why a tapped subtitle search or download failed, for the message at that point. */
class SubtitleFailure(val reason: NetResult.Reason) : Exception(reason.name)

/** Implemented by the object behind [MediaManager]. */
interface TrashConsentSource {
    val trashConsents: Flow<IntentSender>
    fun answerTrashConsent(confirmed: Boolean)
}

/**
 * Android's own trash confirmation, needed only if the direct MediaStore write is refused. Launch each one with
 * startIntentSenderForResult, then call [answerTrashConsent]. With nothing collecting this, those files stay on
 * storage and come back in DeleteResult.failed.
 */
val MediaManager.trashConsents: Flow<IntentSender>
    get() = (this as? TrashConsentSource)?.trashConsents ?: emptyFlow()

fun MediaManager.answerTrashConsent(confirmed: Boolean) {
    (this as? TrashConsentSource)?.answerTrashConsent(confirmed)
}

/**
 * One subtitle site, as the sheet names it. [key] is null for a site that needs no key. [tapOnly] marks a site whose
 * small quota only the owner's tap may spend, so the automatic download never uses it.
 */
data class SubtitleSiteInfo(val name: String, val key: ApiKeyKind?, val seriesOnly: Boolean, val tapOnly: Boolean = false)

/**
 * Where a tapped search looks, in the default order, whatever the Online features switch says: OnlineSubtitles.available
 * already covers that. [active] are searched now: every site that needs no key, and every site whose key is saved.
 * [withFreeKey] are the sites a free key from the site itself would add. A [SubtitleSiteInfo.seriesOnly] site is asked
 * only for an episode, so a film with no key saved finds nothing and the search fails with NO_KEY.
 */
data class SubtitleSites(val active: List<SubtitleSiteInfo>, val withFreeKey: List<SubtitleSiteInfo>)

/** Implemented by the object behind [OnlineSubtitles]. */
interface SubtitleSiteSource {
    val sites: Flow<SubtitleSites>
}

/** What the automatic download did for one file. Only [Loaded] is ever shown; the rest stay silent (brief 4.9). */
sealed interface AutoSubtitleOutcome {
    /** Saved per file like a tapped download. [result] says which site and language it came from. */
    data class Loaded(val sidecar: SidecarSubtitle, val result: SubtitleResult) : AutoSubtitleOutcome

    /** The sites answered and nothing fitted. Remembered, so this file is not searched by itself again. */
    data object NothingFound : AutoSubtitleOutcome

    /** Not tried: tried before, private, a clip, online off, no site could be asked, or the network failed. */
    data object Skipped : AutoSubtitleOutcome
}

/**
 * Implemented by the object behind [OnlineSubtitles]. The waiver of 2026-11-08: one search per file that the owner
 * did not tap, sending only title, year, season, episode and IDs, never to a [SubtitleSiteInfo.tapOnly] site.
 */
interface AutoSubtitleSource {
    /** [languages] in order of preference, such as `ar` then `en`. Never throws. */
    suspend fun autoFetch(item: PlaybackItem, languages: List<String>): AutoSubtitleOutcome
}

suspend fun OnlineSubtitles.autoFetch(item: PlaybackItem, languages: List<String>): AutoSubtitleOutcome =
    (this as? AutoSubtitleSource)?.autoFetch(item, languages) ?: AutoSubtitleOutcome.Skipped

/** For the subtitle sheet: which sites search now, and which a free key would add. Reading it sends nothing. */
val OnlineSubtitles.sites: Flow<SubtitleSites>
    get() = (this as? SubtitleSiteSource)?.sites ?: flowOf(SubtitleSites(emptyList(), emptyList()))

enum class RefreshStage { SCANNING, FETCHING, DONE }

/** Why a swipe fetched no details. Its videos were still scanned, and its titles wait in the queue for later. */
enum class RefreshSkip { ONLINE_OFF, NO_SERVICE, NO_NETWORK }

/**
 * One swipe to refresh as it runs. [filesChecked], [newVideos] and [titlesUpdated] only grow. [titlesLeft] falls as
 * titles are done, and rises when a run queues more. The last value of the flow has stage DONE and is the summary.
 */
data class RefreshProgress(
    val stage: RefreshStage,
    /** Videos the scan has looked at, new or known. */
    val filesChecked: Int = 0,
    /** Videos this scan added to the library. A moved or renamed file keeps its row and is not new. */
    val newVideos: Int = 0,
    /** Matched titles that got fresh art, text, episodes or ratings while the swipe ran. */
    val titlesUpdated: Int = 0,
    /** Titles waiting for details now. What is left at DONE carries on in the background. */
    val titlesLeft: Int = 0,
    /** Set at DONE when details were not fetched at all. */
    val skipped: RefreshSkip? = null,
)

/** Implemented by the object behind [LibraryRepository]. */
interface RefreshSource {
    fun refreshProgress(): Flow<RefreshProgress>
}

/**
 * A swipe to refresh, with live progress. Collecting starts it, and cancelling the collection stops it. The flow ends
 * when the swipe's own work ends: the scan, then up to about 8 s of details, so it never waits for the whole queue.
 * Values are conflated and come at most about 5 times a second, so collecting it is cheap.
 */
fun LibraryRepository.refreshWithProgress(): Flow<RefreshProgress> =
    (this as? RefreshSource)?.refreshProgress() ?: flow {
        emit(RefreshProgress(RefreshStage.SCANNING))
        refreshAll()
        emit(RefreshProgress(RefreshStage.DONE))
    }
