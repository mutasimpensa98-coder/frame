package com.frameglass.data.api

import kotlinx.coroutines.flow.Flow

/*
 * Contract for the owner's improvement package: appearance, file facts, Media Manager,
 * deletion with confirmation, the private folder and online subtitles.
 */

enum class ThemeMode { SYSTEM, DARK, LIGHT }

enum class AccentColor { WHITE, BLUE, RED, GREEN, AMBER, VIOLET }

data class FileFacts(
    val fileRowId: Long,
    val path: String,
    val folder: String,
    val name: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val videoCodec: String?,
    val hdrType: String?,
    val audioSummary: String?,
    val modifiedAtMs: Long,
    val hasSubtitle: Boolean,
    val available: Boolean,
)

data class StorageSummary(
    val videoBytes: Long,
    val videoCount: Int,
    val folderCount: Int,
    val deviceTotalBytes: Long,
    val deviceFreeBytes: Long,
)

enum class MediaGroup { FOLDERS, LARGEST_VIDEOS, TITLES, CLIPS }

enum class MediaEntryKind { FOLDER, FILE, TITLE, CLIP_GROUP }

data class MediaEntry(
    val id: String,
    val kind: MediaEntryKind,
    val name: String,
    /** Folder path or a short note. */
    val detail: String?,
    val sizeBytes: Long,
    val fileCount: Int,
    val durationMs: Long?,
    val width: Int?,
    val height: Int?,
    val coverPath: String?,
    val titleId: Long?,
    val folderPath: String?,
    val fileRowId: Long?,
)

/** What a delete would do. The screen shows all of this, and asks before every delete. */
data class DeletePlan(
    val entries: List<MediaEntry>,
    val fileCount: Int,
    val totalBytes: Long,
    val sidecarCount: Int,
    val sidecarBytes: Long,
    val includeSidecars: Boolean,
    /** Files the system trash accepts, so the owner can restore them. */
    val recoverableCount: Int,
    /** Files that would be gone for good. The dialog must say so plainly. */
    val permanentCount: Int,
    val sampleNames: List<String>,
)

data class DeleteResult(val trashed: Int, val deleted: Int, val failed: List<String>, val freedBytes: Long)

interface MediaManager {
    val summary: Flow<StorageSummary>
    fun entries(group: MediaGroup): Flow<List<MediaEntry>>
    /** Contents of one folder, title or clip group, biggest first. */
    fun children(entry: MediaEntry): Flow<List<MediaEntry>>
    suspend fun planDelete(entries: List<MediaEntry>, includeSidecars: Boolean): DeletePlan
    /** Runs only a plan the owner confirmed. Watch progress and matches stay, as for any missing file. */
    suspend fun delete(plan: DeletePlan): DeleteResult
}

data class VaultState(
    val configured: Boolean,
    val unlocked: Boolean,
    val biometricEnabled: Boolean,
    val itemCount: Int,
    val totalBytes: Long,
)

data class VaultProgress(val name: String, val doneBytes: Long, val totalBytes: Long, val filesLeft: Int)

/**
 * The private folder. Files live in app-private storage. Move in copies, verifies by hash, then
 * removes the original. Items never appear in Home, Library, search, Media Manager or backups.
 */
interface PrivateVault {
    val state: Flow<VaultState>
    val progress: Flow<VaultProgress?>
    /** Empty unless unlocked. */
    val items: Flow<List<TitleCard>>

    /** Sets the password and returns the recovery code, which is shown once and never stored in clear. */
    suspend fun setup(password: String): String
    suspend fun unlockWithPassword(password: String): Boolean
    /** Called by the screen after BiometricPrompt succeeds. */
    fun unlockAfterBiometric()
    suspend fun resetWithRecoveryCode(code: String, newPassword: String): Boolean
    suspend fun changePassword(old: String, new: String): Boolean
    suspend fun setBiometricEnabled(enabled: Boolean)
    fun lock()
    suspend fun moveIn(titleIds: List<Long>, fileRowIds: List<Long>): Result<Int>
    /** Back to the original folder, or Movies/Frame Glass if that folder is gone. */
    suspend fun moveOut(titleId: Long): Result<Int>
}

data class SubtitleResult(
    val token: String,
    val provider: String,
    val language: String,
    val release: String,
    val hashMatch: Boolean,
    val downloads: Int?,
    val format: String?,
    val seasonPack: Boolean,
)

interface OnlineSubtitles {
    /** True while online features are on. A site that needs no key searches episodes, so no key is required. */
    val available: Flow<Boolean>
    suspend fun search(item: PlaybackItem): Result<List<SubtitleResult>>
    /** Saves under app storage for this file and returns it ready to load. */
    suspend fun download(item: PlaybackItem, result: SubtitleResult): Result<SidecarSubtitle>
}
