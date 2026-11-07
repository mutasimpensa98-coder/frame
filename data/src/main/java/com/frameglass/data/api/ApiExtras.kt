package com.frameglass.data.api

import android.content.IntentSender
import com.frameglass.data.logic.VaultRefs
import com.frameglass.net.NetResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/*
 * Additions beside the fixed contract in Api.kt. EpisodeRow carries one PlayRef, so the version picker of
 * F-L9 needs its own call, and PlaybackItem is built before online skip times can arrive. TitleCard carries no
 * PlayRef, so a private card needs one here, and DeletePlan has no way to ask Android for a confirmation.
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
