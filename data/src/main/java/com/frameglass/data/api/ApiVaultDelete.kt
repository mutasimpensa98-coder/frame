package com.frameglass.data.api

/*
 * Delete inside the private folder (owner request of 2026-11-21). Private videos live in app storage, which has no
 * trash, so this delete is for good. It is planned first and runs only on a plan the owner confirmed, as every
 * delete in the app does. It reaches the screens through extensions, as in ApiExtras.kt, so PrivateVault and its
 * fakes stay as they are.
 */

/**
 * What a delete inside the private folder would remove, for the confirmation sheet. [titleIds] are the cards of
 * PrivateVault.items it covers, in the order asked, and [names] their names in that order. [itemIds] are the stored
 * videos behind them: only those are removed, so a video that entered the folder after the plan was made stays.
 * [totalBytes] counts the videos and the subtitles that moved in with them.
 */
data class VaultDeletePlan(
    val titleIds: List<Long>,
    val itemIds: List<Long>,
    val fileCount: Int,
    val subtitleCount: Int,
    val totalBytes: Long,
    val names: List<String>,
) {
    val isEmpty: Boolean get() = itemIds.isEmpty()
}

/** How a confirmed delete ended. [failed] videos are still in the folder. Nothing deleted can be brought back. */
data class VaultDeleteResult(val deleted: Int, val failed: Int, val freedBytes: Long)

/** Implemented by the object behind [PrivateVault]. Both calls throw [VaultClosedException] while the folder is locked. */
interface VaultDeleteSource {
    suspend fun planDelete(titleIds: List<Long>): VaultDeletePlan

    /**
     * Runs only a plan the owner confirmed. It removes each stored video with its subtitles, its rows and its saved
     * progress, and everything the app kept about a title that has no other file. A video that is playing closes.
     */
    suspend fun delete(plan: VaultDeletePlan): VaultDeleteResult
}

suspend fun PrivateVault.planDelete(titleIds: List<Long>): VaultDeletePlan =
    (this as? VaultDeleteSource)?.planDelete(titleIds) ?: throw VaultClosedException()

suspend fun PrivateVault.deleteForGood(plan: VaultDeletePlan): VaultDeleteResult =
    (this as? VaultDeleteSource)?.delete(plan) ?: throw VaultClosedException()
