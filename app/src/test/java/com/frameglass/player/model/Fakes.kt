package com.frameglass.player.model

import com.frameglass.data.api.DeletePlan
import com.frameglass.data.api.DeleteResult
import com.frameglass.data.api.FrameServices
import com.frameglass.data.api.LibraryRepository
import com.frameglass.data.api.MediaEntry
import com.frameglass.data.api.MediaEntryKind
import com.frameglass.data.api.MediaGroup
import com.frameglass.data.api.MediaManager
import com.frameglass.data.api.OnlineSubtitles
import com.frameglass.data.api.PlaybackRepository
import com.frameglass.data.api.PrivateVault
import com.frameglass.data.api.SettingsRepository
import com.frameglass.data.api.StorageSummary
import com.frameglass.data.api.TitleCard
import com.frameglass.data.api.VaultClosedException
import com.frameglass.data.api.VaultProgress
import com.frameglass.data.api.VaultState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow

internal class FakeServices(
    override val media: MediaManager = FakeMedia(),
    override val vault: PrivateVault = FakeVault(),
) : FrameServices {
    override val library: LibraryRepository get() = error("not used")
    override val playback: PlaybackRepository get() = error("not used")
    override val settings: SettingsRepository get() = error("not used")
    override val subtitles: OnlineSubtitles get() = error("not used")
}

internal fun entry(name: String) = MediaEntry(
    id = "file|$name",
    kind = MediaEntryKind.FILE,
    name = name,
    detail = null,
    sizeBytes = 100,
    fileCount = 1,
    durationMs = null,
    width = null,
    height = null,
    coverPath = null,
    titleId = null,
    folderPath = null,
    fileRowId = 1,
)

internal fun plan(entries: List<MediaEntry>, sidecars: Boolean) = DeletePlan(
    entries = entries,
    fileCount = entries.size,
    totalBytes = entries.sumOf { it.sizeBytes },
    sidecarCount = 1,
    sidecarBytes = 5,
    includeSidecars = sidecars,
    recoverableCount = entries.size,
    permanentCount = 0,
    sampleNames = entries.map { it.name },
)

internal class FakeMedia : MediaManager {
    var planFails = false
    var deleteFails = false
    var deleted: DeletePlan? = null
    var plans = 0

    /** While set, delete() waits here, so a test can look at the stage in between. */
    var gate: CompletableDeferred<Unit>? = null

    override val summary: Flow<StorageSummary> = emptyFlow()
    override fun entries(group: MediaGroup): Flow<List<MediaEntry>> = emptyFlow()
    override fun children(entry: MediaEntry): Flow<List<MediaEntry>> = emptyFlow()

    override suspend fun planDelete(entries: List<MediaEntry>, includeSidecars: Boolean): DeletePlan {
        plans++
        if (planFails) throw IllegalStateException("no")
        return plan(entries, includeSidecars)
    }

    override suspend fun delete(plan: DeletePlan): DeleteResult {
        gate?.await()
        if (deleteFails) throw IllegalStateException("no")
        deleted = plan
        return DeleteResult(trashed = plan.fileCount, deleted = 0, failed = emptyList(), freedBytes = 0)
    }
}

internal class FakeVault : PrivateVault {
    val current = MutableStateFlow(VaultState(false, false, false, 0, 0))
    val moved = ArrayList<Pair<List<Long>, List<Long>>>()
    var locks = 0
    var moveResult: Result<Int> = Result.success(1)

    override val state: Flow<VaultState> = current
    override val progress: Flow<VaultProgress?> = emptyFlow()
    override val items: Flow<List<TitleCard>> = emptyFlow()

    override suspend fun setup(password: String): String {
        current.value = current.value.copy(configured = true, unlocked = true)
        return "ABCD-EFGH-JKMN-PQRS"
    }

    override suspend fun unlockWithPassword(password: String) = true
    override fun unlockAfterBiometric() = Unit
    override suspend fun resetWithRecoveryCode(code: String, newPassword: String) = true
    override suspend fun changePassword(old: String, new: String) = true
    override suspend fun setBiometricEnabled(enabled: Boolean) = Unit

    override fun lock() {
        locks++
    }

    override suspend fun moveIn(titleIds: List<Long>, fileRowIds: List<Long>): Result<Int> {
        if (!current.value.configured) return Result.failure(VaultClosedException())
        moved += titleIds to fileRowIds
        return moveResult
    }

    override suspend fun moveOut(titleId: Long): Result<Int> = moveResult
}
