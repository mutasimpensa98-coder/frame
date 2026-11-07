package com.frameglass.player.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.frameglass.data.api.frameServices
import com.frameglass.player.R
import kotlinx.coroutines.launch
import java.time.LocalDate

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
    }
}
