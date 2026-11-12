package com.frameglass.player.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameglass.data.api.AutoBackupFailure
import com.frameglass.data.api.AutoBackupMode
import com.frameglass.data.api.AutoBackupState
import com.frameglass.data.api.autoBackups
import com.frameglass.data.api.frameServices
import com.frameglass.player.R
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

@Composable
internal fun BackupSettingsScreen() {
    val library = LocalContext.current.frameServices.library
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<Int?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            scope.launch {
                val result = library.exportBackup(uri)
                message = if (result.isSuccess) R.string.app_backup_done else R.string.app_backup_failed
            }
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = library.importBackup(uri)
                message = if (result.isSuccess) R.string.app_restore_done else R.string.app_restore_failed
            }
        }
    }
    val done = message

    SettingsPage(stringResource(R.string.app_page_backup)) {
        item(key = "backup") {
            SettingsSection(stringResource(R.string.app_settings_backup)) {
                SettingRow(
                    title = stringResource(R.string.app_backup),
                    description = done?.let { stringResource(it) } ?: stringResource(R.string.app_backup_hint),
                    onClick = { save.launch("frame-glass-backup-${LocalDate.now()}.zip") },
                )
                SettingsDivider()
                SettingRow(
                    title = stringResource(R.string.app_restore),
                    description = stringResource(R.string.app_restore_hint),
                    onClick = { restore.launch(arrayOf("application/zip", "application/octet-stream")) },
                )
            }
        }
        item(key = "auto") { AutoBackupSection() }
    }
}

/**
 * Automatic backup: off, weekly or daily into a folder picked once through the system picker. Choosing a schedule
 * with no folder opens the picker first, and the schedule starts only when a folder was picked.
 */
@Composable
private fun AutoBackupSection() {
    val backups = LocalContext.current.frameServices.library.autoBackups
    val scope = rememberCoroutineScope()
    val state by remember(backups) { backups.autoBackup }.collectAsStateWithLifecycle(null as AutoBackupState?)
    var folderFailed by remember { mutableStateOf(false) }
    var pendingMode by remember { mutableStateOf<AutoBackupMode?>(null) }
    var running by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
        val wanted = pendingMode
        pendingMode = null
        if (tree != null) {
            scope.launch {
                val ok = backups.setAutoBackupFolder(tree)
                folderFailed = !ok
                if (ok && wanted != null) backups.setAutoBackupMode(wanted)
            }
        }
    }
    val current = state ?: return
    val hasFolder = current.folderName != null

    SettingsSection(stringResource(R.string.app_auto_backup)) {
        ChoiceRow(
            title = stringResource(R.string.app_auto_backup),
            description = stringResource(R.string.app_auto_backup_hint),
            options = listOf(
                AutoBackupMode.OFF to stringResource(R.string.app_auto_backup_off),
                AutoBackupMode.WEEKLY to stringResource(R.string.app_auto_backup_weekly),
                AutoBackupMode.DAILY to stringResource(R.string.app_auto_backup_daily),
            ),
            selected = current.mode,
            onSelect = { mode ->
                if (mode != AutoBackupMode.OFF && !hasFolder) {
                    pendingMode = mode
                    pick.launch(null)
                } else {
                    scope.launch { backups.setAutoBackupMode(mode) }
                }
            },
        )
        SettingsDivider()
        SettingRow(
            title = stringResource(R.string.app_auto_backup_folder),
            description = if (folderFailed) stringResource(R.string.app_auto_backup_folder_failed) else null,
            value = current.folderName?.ifEmpty { null } ?: stringResource(R.string.app_auto_backup_folder_none),
            onClick = { pick.launch(null) },
        )
        if (hasFolder) {
            SettingsDivider()
            SettingRow(
                title = stringResource(R.string.app_auto_backup_now),
                description = backupLine(current),
                notes = listOfNotNull(failureLine(current)),
                onClick = if (running) null else {
                    {
                        running = true
                        scope.launch {
                            backups.backUpNow()
                            running = false
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun backupLine(state: AutoBackupState): String =
    state.lastBackupAt?.let { stringResource(R.string.app_auto_backup_last, dateTimeLabel(it)) }
        ?: stringResource(R.string.app_auto_backup_never)

@Composable
private fun failureLine(state: AutoBackupState): String? {
    val at = state.lastFailureAt ?: return null
    val text = when (state.failure ?: return null) {
        AutoBackupFailure.NO_FOLDER -> R.string.app_auto_backup_failed_no_folder
        AutoBackupFailure.FOLDER_LOST -> R.string.app_auto_backup_failed_lost
        AutoBackupFailure.WRITE_FAILED -> R.string.app_auto_backup_failed_write
    }
    return stringResource(text, dateTimeLabel(at))
}

@Composable
private fun dateTimeLabel(epochMs: Long): String {
    val locale = LocalLocale.current.platformLocale
    return remember(epochMs, locale) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(epochMs))
    }
}
