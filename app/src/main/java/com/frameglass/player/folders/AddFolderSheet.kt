package com.frameglass.player.folders

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameglass.data.api.frameServices
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameType
import com.frameglass.glass.GlassIconButton
import com.frameglass.glass.GlassRow
import com.frameglass.glass.GlassSheet
import com.frameglass.glass.GlassSheetState
import com.frameglass.glass.WhitePill
import com.frameglass.glass.bidiIsolate
import com.frameglass.glass.glassSheetOrigin
import com.frameglass.glass.rememberGlassSheetState
import com.frameglass.player.R
import com.frameglass.player.ui.rememberUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Collator
import java.util.Locale

private data class StorageRoot(val path: String, val label: String)

private data class Directory(val path: String, val name: String)

private fun storageRoots(context: Context): List<StorageRoot> =
    context.getSystemService(StorageManager::class.java).storageVolumes.mapNotNull { volume ->
        val mounted = volume.state == Environment.MEDIA_MOUNTED || volume.state == Environment.MEDIA_MOUNTED_READ_ONLY
        volume.directory?.takeIf { mounted }?.let { StorageRoot(it.absolutePath, volume.getDescription(context)) }
    }

// Direct paths per section 4.6. Hidden folders stay out of the list, as the scanner skips them by default.
private fun subfolders(path: String, locale: Locale): List<Directory> {
    val order = Collator.getInstance(locale)
    return File(path).listFiles().orEmpty()
        .filter { it.isDirectory && !it.name.startsWith(".") }
        .sortedWith { a, b -> order.compare(a.name, b.name) }
        .map { Directory(it.path, it.name) }
}

/** The + button of F-U11, for places the phone search does not reach. Its browser morphs out of it. */
@Composable
internal fun AddFolderButton(modifier: Modifier = Modifier) {
    val sheet = rememberGlassSheetState()
    GlassIconButton(
        icon = Icons.Rounded.Add,
        contentDescription = stringResource(R.string.app_add_place),
        onClick = sheet::open,
        modifier = modifier.glassSheetOrigin(sheet),
    )
    AddFolderSheet(sheet)
}

@Composable
internal fun AddFolderSheet(state: GlassSheetState) {
    val model = rememberUiModel()
    val library = LocalContext.current.frameServices.library
    val scope = rememberCoroutineScope()
    val settings by model.settings.collectAsStateWithLifecycle()
    val folders by model.folders.collectAsStateWithLifecycle()
    GlassSheet(state, title = stringResource(R.string.app_add_place)) {
        if (settings?.scanWholePhone != false) {
            Text(
                text = stringResource(R.string.app_add_place_hint),
                style = FrameType.Body,
                color = FrameColors.SecondaryText,
                modifier = Modifier.padding(horizontal = FrameSpacing.S12),
            )
        }
        FolderBrowser(
            pickText = stringResource(R.string.app_add_this_folder),
            pickedText = stringResource(R.string.app_folder_added),
            isPicked = { path -> folders.orEmpty().any { it.path == path } },
            onPick = { path ->
                scope.launch {
                    library.addFolder(path)
                    state.close()
                }
            },
        )
    }
}

/**
 * The direct-path browser, with one pill that picks the folder shown. Needs All Files access, and asks for
 * it in place when it is missing.
 */
@Composable
internal fun FolderBrowser(
    pickText: String,
    pickedText: String,
    isPicked: (String) -> Boolean,
    onPick: (String) -> Unit,
) {
    val context = LocalContext.current
    if (!rememberAllFilesAccess()) {
        Text(
            text = stringResource(R.string.app_files_needed),
            style = FrameType.Body,
            color = FrameColors.SecondaryText,
            modifier = Modifier.padding(horizontal = FrameSpacing.S12),
        )
        WhitePill(
            text = stringResource(R.string.app_files_allow),
            onClick = { context.openAllFilesAccess() },
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    val locale = LocalLocale.current.platformLocale
    val roots = remember(context) { storageRoots(context) }
    var path by remember { mutableStateOf(roots.singleOrNull()?.path) }
    val root = roots.firstOrNull { it.path == path }
    val here = path

    if (here == null) {
        roots.forEach { volume ->
            GlassRow(text = volume.label, onClick = { path = volume.path }, icon = Icons.Rounded.Storage)
        }
        return
    }

    val shown = remember(here, roots) {
        val volume = roots.firstOrNull { here == it.path || here.startsWith(it.path + "/") }
        val relative = volume?.let { here.removePrefix(it.path) }.orEmpty()
        (volume?.label.orEmpty() + relative).ifEmpty { here }
    }
    Text(
        text = bidiIsolate(shown),
        style = FrameType.Label,
        color = FrameColors.SecondaryText,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = FrameSpacing.S12),
    )
    val picked = isPicked(here)
    WhitePill(
        text = if (picked) pickedText else pickText,
        onClick = { onPick(here) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !picked,
    )
    if (root == null) {
        GlassRow(
            text = stringResource(R.string.app_folder_up),
            onClick = { path = File(here).parent },
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
        )
    } else if (roots.size > 1) {
        GlassRow(
            text = stringResource(R.string.app_folder_storage),
            onClick = { path = null },
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
        )
    }

    val children by produceState<List<Directory>?>(null, here) {
        value = withContext(Dispatchers.IO) { subfolders(here, locale) }
    }
    val listed = children
    if (listed != null && listed.isEmpty()) {
        Text(
            text = stringResource(R.string.app_folder_no_subfolders),
            style = FrameType.Body,
            color = FrameColors.SecondaryText,
            modifier = Modifier.padding(FrameSpacing.S12),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(FrameSpacing.S8)) {
        listed.orEmpty().forEach { folder ->
            GlassRow(text = folder.name, onClick = { path = folder.path }, icon = Icons.Rounded.Folder)
        }
    }
}

/** Whether All Files access is on, checked again each time the owner comes back from Settings. */
@Composable
internal fun rememberAllFilesAccess(): Boolean {
    var granted by remember { mutableStateOf(Environment.isExternalStorageManager()) }
    LifecycleResumeEffect(Unit) {
        granted = Environment.isExternalStorageManager()
        onPauseOrDispose { }
    }
    return granted
}

internal fun Context.openAllFilesAccess() {
    startActivity(
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.fromParts("package", packageName, null)),
    )
}
