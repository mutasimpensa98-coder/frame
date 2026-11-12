package com.frameglass.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.frameglass.data.DataServices
import com.frameglass.data.api.AutoBackupFailure
import com.frameglass.data.api.AutoBackupMode
import com.frameglass.data.api.AutoBackupSource
import com.frameglass.data.api.AutoBackupState
import com.frameglass.data.logic.AutoBackupRules
import com.frameglass.data.logic.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Its own file, so a restore, which rewrites the settings, never changes the folder or the schedule. */
private val Context.autoBackupStore by preferencesDataStore(
    name = "auto-backup",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

private object AutoPrefs {
    val mode = stringPreferencesKey("mode")
    val tree = stringPreferencesKey("tree")
    val folderName = stringPreferencesKey("folderName")
    val lastOk = longPreferencesKey("lastBackupAt")
    val lastFailAt = longPreferencesKey("lastFailureAt")
    val failure = stringPreferencesKey("failure")
}

private class AutoBackupFailed(val reason: AutoBackupFailure, cause: Throwable? = null) : Exception(reason.name, cause)

/**
 * F-L5 on a schedule. A WorkManager job with battery-not-low writes the same zip as a manual backup into the folder
 * the owner picked, through the Storage Access Framework, then deletes only its own older files. One UI may never
 * run the job (section 4.7), so every app open also checks whether one is due.
 */
internal class AutoBackupManager(
    private val context: Context,
    private val backup: BackupManager,
    private val clock: Clock,
    private val scope: CoroutineScope,
) : AutoBackupSource {
    private val store get() = context.autoBackupStore
    private val resolver get() = context.contentResolver
    private val running = Mutex()

    private fun modeOf(p: Preferences): AutoBackupMode =
        AutoBackupMode.entries.firstOrNull { it.name == p[AutoPrefs.mode] } ?: AutoBackupMode.OFF

    override val autoBackup: Flow<AutoBackupState> = store.data.map { p ->
        AutoBackupState(
            mode = modeOf(p),
            folderName = p[AutoPrefs.tree]?.let { p[AutoPrefs.folderName] ?: "" },
            lastBackupAt = p[AutoPrefs.lastOk],
            lastFailureAt = p[AutoPrefs.lastFailAt],
            failure = AutoBackupFailure.entries.firstOrNull { it.name == p[AutoPrefs.failure] },
        )
    }.distinctUntilChanged()

    override suspend fun setAutoBackupFolder(tree: Uri): Boolean = withContext(Dispatchers.IO) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val taken = runCatching { resolver.takePersistableUriPermission(tree, flags) }.isSuccess
        if (!taken) return@withContext false
        val name = runCatching { displayName(tree) }.getOrNull()
            ?: runCatching { DocumentsContract.getTreeDocumentId(tree).substringAfter(':').substringAfterLast('/') }.getOrNull()
                ?.ifEmpty { null }
        val old = store.data.first()[AutoPrefs.tree]
        store.edit { p ->
            p[AutoPrefs.tree] = tree.toString()
            if (name == null) p.remove(AutoPrefs.folderName) else p[AutoPrefs.folderName] = name
            // A new folder starts with a clean line, so an old failure does not stay under it.
            p.remove(AutoPrefs.failure)
            p.remove(AutoPrefs.lastFailAt)
        }
        if (old != null && old != tree.toString()) {
            runCatching { resolver.releasePersistableUriPermission(Uri.parse(old), flags) }
        }
        true
    }

    override suspend fun setAutoBackupMode(mode: AutoBackupMode) {
        store.edit { it[AutoPrefs.mode] = mode.name }
        schedule(mode, ExistingPeriodicWorkPolicy.UPDATE)
    }

    override suspend fun backUpNow(): Boolean = withContext(Dispatchers.IO) { run(onlyIfDue = false) }

    /** The job's work: back up only when one is due, so the catch-up and the periodic job cannot both write. */
    suspend fun runScheduled() {
        withContext(Dispatchers.IO) { run(onlyIfDue = true) }
    }

    /** Called on every app open. Returns at once. */
    fun onAppOpen() {
        scope.launch {
            val p = store.data.first()
            val mode = modeOf(p)
            schedule(mode, ExistingPeriodicWorkPolicy.KEEP)
            if (AutoBackupRules.due(mode, p[AutoPrefs.lastOk], clock.now())) {
                val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
                    .setConstraints(Constraints(requiresBatteryNotLow = true))
                    .setInitialDelay(CATCH_UP_DELAY_S, TimeUnit.SECONDS)
                    .build()
                runCatching { WorkManager.getInstance(context).enqueueUniqueWork(CATCH_UP, ExistingWorkPolicy.KEEP, request) }
            }
        }
    }

    private fun schedule(mode: AutoBackupMode, policy: ExistingPeriodicWorkPolicy) {
        val manager = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        val period = AutoBackupRules.periodMs(mode)
        if (period == null) {
            manager.cancelUniqueWork(PERIODIC)
            manager.cancelUniqueWork(CATCH_UP)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(period, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints(requiresBatteryNotLow = true))
            .build()
        manager.enqueueUniquePeriodicWork(PERIODIC, policy, request)
    }

    /** Due is checked under the lock, so two jobs that start together write one file. */
    private suspend fun run(onlyIfDue: Boolean): Boolean = running.withLock {
        val now = clock.now()
        if (onlyIfDue) {
            val p = store.data.first()
            if (!AutoBackupRules.due(modeOf(p), p[AutoPrefs.lastOk], now)) return@withLock false
        }
        val outcome = runCatching { write(now) }
        val failure = (outcome.exceptionOrNull() as? AutoBackupFailed)?.reason
            ?: outcome.exceptionOrNull()?.let { AutoBackupFailure.WRITE_FAILED }
        outcome.exceptionOrNull()?.let { Log.w(TAG, "automatic backup failed: ${failure?.name}") }
        store.edit { p ->
            if (failure == null) {
                p[AutoPrefs.lastOk] = now
                p.remove(AutoPrefs.failure)
                p.remove(AutoPrefs.lastFailAt)
            } else {
                p[AutoPrefs.failure] = failure.name
                p[AutoPrefs.lastFailAt] = now
            }
        }
        failure == null
    }

    private suspend fun write(now: Long) {
        val tree = store.data.first()[AutoPrefs.tree]?.let(Uri::parse) ?: throw AutoBackupFailed(AutoBackupFailure.NO_FOLDER)
        val held = resolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }
        if (!held) throw AutoBackupFailed(AutoBackupFailure.FOLDER_LOST)
        val parentId = try {
            DocumentsContract.getTreeDocumentId(tree)
        } catch (e: IllegalArgumentException) {
            throw AutoBackupFailed(AutoBackupFailure.FOLDER_LOST, e)
        }
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, parentId)
        val name = AutoBackupRules.fileName(now, ZoneId.systemDefault())
        val doc = try {
            DocumentsContract.createDocument(resolver, parent, ZIP, name)
        } catch (e: FileNotFoundException) {
            throw AutoBackupFailed(AutoBackupFailure.FOLDER_LOST, e)
        } catch (e: SecurityException) {
            throw AutoBackupFailed(AutoBackupFailure.FOLDER_LOST, e)
        } ?: throw AutoBackupFailed(AutoBackupFailure.FOLDER_LOST)
        try {
            val out = resolver.openOutputStream(doc, "wt") ?: throw AutoBackupFailed(AutoBackupFailure.WRITE_FAILED)
            out.use { backup.writeTo(it) }
        } catch (e: Exception) {
            // A half-written file of our pattern would push a good one out, so it goes.
            runCatching { DocumentsContract.deleteDocument(resolver, doc) }
            throw (e as? AutoBackupFailed) ?: AutoBackupFailed(AutoBackupFailure.WRITE_FAILED, e)
        }
        prune(tree, parentId)
    }

    /** Deletes our own files beyond the newest [AutoBackupSource.KEEP]. Nothing of another name is touched. */
    private fun prune(tree: Uri, parentId: String) {
        val children = runCatching { children(tree, parentId) }.getOrElse { return }
        val gone = AutoBackupRules.toDelete(children.map { it.second }, AutoBackupSource.KEEP).toSet()
        children.filter { (_, name) -> name in gone }.forEach { (id, _) ->
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
    }

    /** Plain files in the folder, as document id and display name. */
    private fun children(tree: Uri, parentId: String): List<Pair<String, String>> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val found = ArrayList<Pair<String, String>>()
        resolver.query(uri, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                found += id to name
            }
        }
        return found
    }

    private fun displayName(tree: Uri): String? {
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return resolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    private companion object {
        const val TAG = "FrameData"
        const val ZIP = "application/zip"
        const val PERIODIC = "frameglass-auto-backup"
        const val CATCH_UP = "frameglass-auto-backup-catch-up"
        const val CATCH_UP_DELAY_S = 30L
    }
}

/** Short, and a stop loses nothing: the next due check writes again. */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        DataServices.of(applicationContext).autoBackup.runScheduled()
        return Result.success()
    }
}
