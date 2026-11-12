package com.frameglass.player.media

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameglass.data.api.CleanupItem
import com.frameglass.data.api.CleanupSuggestions
import com.frameglass.data.api.LibraryRepository
import com.frameglass.data.api.cleanupSuggestions
import com.frameglass.data.api.MediaEntry
import com.frameglass.data.api.MediaEntryKind
import com.frameglass.data.api.MediaGroup
import com.frameglass.data.api.StorageSummary
import com.frameglass.data.api.frameServices
import com.frameglass.glass.CoverImage
import com.frameglass.glass.FilterChips
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameShapes
import com.frameglass.glass.FrameSizes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameType
import com.frameglass.glass.GlassIconButton
import com.frameglass.glass.GlassKind
import com.frameglass.glass.GlassSource
import com.frameglass.glass.GlassSurface
import com.frameglass.glass.OutlinePill
import com.frameglass.glass.PressableGlass
import com.frameglass.glass.Skeleton
import com.frameglass.glass.TopGlassBar
import com.frameglass.glass.bidiIsolate
import com.frameglass.glass.glassScrolled
import com.frameglass.player.R
import com.frameglass.player.library.BackButton
import com.frameglass.player.nav.LocalNavigator
import com.frameglass.player.nav.Navigator
import com.frameglass.player.nav.Route
import com.frameglass.player.ui.DangerPill
import com.frameglass.player.ui.bleed
import com.frameglass.player.ui.clockLabel
import com.frameglass.player.ui.durationLabel
import com.frameglass.player.ui.fileSizeLabel
import com.frameglass.player.ui.localNumber
import com.frameglass.player.ui.metaLine
import com.frameglass.player.ui.navigationInset
import com.frameglass.player.ui.rememberUiModel
import com.frameglass.player.ui.statusInset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private val Groups = listOf(MediaGroup.FOLDERS, MediaGroup.LARGEST_VIDEOS, MediaGroup.TITLES, MediaGroup.CLIPS)
private val ThumbWidth = 96.dp
private val BarHeight = 8.dp
private val LegendDot = 8.dp
private val RowIcon = 28.dp
private val SelectionClearance = 96.dp
private val HeaderSkeletonHeight = 124.dp
private const val OtherFill = 0.32f
private const val MillisPerMinute = 60_000L
private const val RowSkeletons = 6
private val RowSkeletonHeight = 64.dp

/** Which rows are picked. Ids stay valid across list updates, so a scan cannot drop a selection. */
@Stable
private class Selection {
    val picked = mutableStateMapOf<String, MediaEntry>()
    var active by mutableStateOf(false)
        private set

    fun start(entry: MediaEntry) {
        active = true
        picked[entry.id] = entry
    }

    fun toggle(entry: MediaEntry) {
        if (picked.remove(entry.id) == null) picked[entry.id] = entry
        if (picked.isEmpty()) active = false
    }

    fun enter() {
        active = true
    }

    fun selectAll(entries: List<MediaEntry>) {
        if (entries.isEmpty()) return
        active = true
        entries.forEach { picked[it.id] = it }
    }

    fun clear() {
        picked.clear()
        active = false
    }
}

/** The waiver of 2026-11-07: what takes up space, with delete behind a confirmation. */
@Composable
internal fun MediaManagerScreen() {
    val media = LocalContext.current.frameServices.media
    val summary by remember(media) { media.summary }.collectAsStateWithLifecycle(null as StorageSummary?)
    // The groups, then Suggestions as the last chip.
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val group = Groups.getOrNull(tab)
    val entries by remember(media, group) { group?.let(media::entries) ?: flowOf(emptyList()) }
        .collectAsStateWithLifecycle(null as List<MediaEntry>?)
    val suggestions by remember(media) { media.cleanupSuggestions }.collectAsStateWithLifecycle(null as CleanupSuggestions?)
    val selection = remember { Selection() }
    val list = rememberLazyListState()
    val labels = listOf(
        stringResource(R.string.app_filter_folders),
        stringResource(R.string.app_media_tab_largest),
        stringResource(R.string.app_search_titles),
        stringResource(R.string.app_media_tab_clips),
        stringResource(R.string.app_media_tab_suggestions),
    )
    LaunchedEffect(tab) { selection.clear() }

    MediaPage(
        title = stringResource(R.string.app_settings_media),
        list = list,
        selection = selection,
    ) {
        item(key = "summary") { StorageHeader(summary) }
        item(key = "tabs") {
            FilterChips(
                labels = labels,
                selectedIndex = tab,
                onSelect = { tab = it },
                modifier = Modifier.bleed(FrameSpacing.S16),
            )
        }
        if (group != null) mediaRows(entries, selection) else suggestionRows(suggestions, selection)
    }
}

/**
 * The cleanup assistant. Every row is a plain file entry, so a delete from here goes through the same confirmation
 * sheet, trash path and checks as any other. Private-folder videos are never in the list.
 */
private fun LazyListScope.suggestionRows(suggestions: CleanupSuggestions?, selection: Selection) {
    if (suggestions == null) {
        items(RowSkeletons, key = { "skeleton$it" }) { Skeleton(Modifier.fillMaxWidth().height(RowSkeletonHeight)) }
        return
    }
    item(key = "cleanup-head") {
        CleanupHeader(suggestions, onSelectAll = { selection.selectAll(suggestions.all.map { it.entry }) })
    }
    if (suggestions.versions.isNotEmpty()) {
        item(key = "cleanup-versions") { SuggestionLabel(stringResource(R.string.app_cleanup_versions)) }
        suggestionItems(suggestions.versions, selection)
    }
    if (suggestions.watched.isNotEmpty()) {
        item(key = "cleanup-watched") { SuggestionLabel(stringResource(R.string.app_cleanup_watched)) }
        suggestionItems(suggestions.watched, selection)
    }
}

private fun LazyListScope.suggestionItems(items: List<CleanupItem>, selection: Selection) {
    items(items, key = { "cleanup:" + it.entry.id }, contentType = { "media" }) { item ->
        val navigator = LocalNavigator.current
        val library = LocalContext.current.frameServices.library
        val scope = rememberCoroutineScope()
        val entry = item.entry
        MediaRow(
            entry = entry,
            selecting = selection.active,
            selected = entry.id in selection.picked,
            note = item.keptName?.let { stringResource(R.string.app_cleanup_keeps, bidiIsolate(it)) },
            onClick = {
                if (selection.active) {
                    selection.toggle(entry)
                } else {
                    scope.launch { navigator.openFile(entry, library.hasDetails(entry.titleId)) }
                }
            },
            onLongClick = { if (selection.active) selection.toggle(entry) else selection.start(entry) },
        )
    }
}

@Composable
private fun CleanupHeader(suggestions: CleanupSuggestions, onSelectAll: () -> Unit) {
    val count = suggestions.all.size
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FrameShapes.Card)
            .background(FrameColors.Card)
            .padding(FrameSpacing.S16),
        verticalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
    ) {
        if (count == 0) {
            Text(stringResource(R.string.app_cleanup_none), style = FrameType.Title, color = FrameColors.White)
        } else {
            Text(
                text = stringResource(R.string.app_cleanup_total, fileSizeLabel(suggestions.totalBytes)),
                style = FrameType.Title,
                color = FrameColors.White,
            )
            Text(
                text = pluralStringResource(R.plurals.app_videos, count, count),
                style = FrameType.Label,
                color = FrameColors.SecondaryText,
            )
        }
        Text(stringResource(R.string.app_cleanup_hint), style = FrameType.Label, color = FrameColors.SecondaryText)
        if (count > 0) {
            OutlinePill(
                text = stringResource(R.string.app_cleanup_select_all),
                onClick = onSelectAll,
                icon = Icons.Rounded.Checklist,
            )
        }
    }
}

@Composable
private fun SuggestionLabel(text: String) {
    Text(
        text = bidiIsolate(text),
        style = FrameType.Label,
        color = FrameColors.SecondaryText,
        modifier = Modifier.padding(start = FrameSpacing.S8, top = FrameSpacing.S8),
    )
}

/** The contents of one folder, title or clip group, biggest first. */
@Composable
internal fun MediaItemsScreen(route: Route.MediaItems) {
    val services = LocalContext.current.frameServices
    val media = services.media
    val navigator = LocalNavigator.current
    val entry = remember(route) { route.entry() }
    val entries by remember(media, entry) { media.children(entry) }.collectAsStateWithLifecycle(null as List<MediaEntry>?)
    val selection = remember { Selection() }
    val list = rememberLazyListState()
    val details by produceState(false, route.titleId) { value = services.library.hasDetails(route.titleId) }

    MediaPage(
        title = route.name,
        list = list,
        selection = selection,
        extraAction = {
            val titleId = route.titleId
            val folder = route.folderPath
            when {
                titleId != null && details -> GlassIconButton(
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = stringResource(R.string.app_media_open_title),
                    onClick = { navigator.details(titleId) },
                )
                folder != null -> GlassIconButton(
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = stringResource(R.string.app_media_open_folder),
                    onClick = { navigator.push(Route.Folder(folder)) },
                )
            }
        },
    ) {
        mediaRows(entries, selection)
    }
}

@Composable
private fun MediaPage(
    title: String,
    list: LazyListState,
    selection: Selection,
    extraAction: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val deletes = rememberUiModel().deletes
    val top = statusInset() + FrameSizes.TopBarHeight
    val bottom = navigationInset() + if (selection.active) SelectionClearance else FrameSpacing.S32
    BackHandler(enabled = selection.active, onBack = selection::clear)

    Box(Modifier.fillMaxSize().background(FrameColors.Canvas)) {
        GlassSource(Modifier.fillMaxSize()) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = FrameSpacing.S16,
                    end = FrameSpacing.S16,
                    top = top,
                    bottom = bottom,
                ),
                verticalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
                content = content,
            )
        }
        TopGlassBar(
            title = title,
            scrolled = { list.glassScrolled() },
            navigation = { BackButton() },
            actions = {
                if (selection.active) {
                    GlassIconButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.app_media_select_done),
                        onClick = selection::clear,
                    )
                } else {
                    extraAction?.invoke()
                    GlassIconButton(
                        icon = Icons.Rounded.Checklist,
                        contentDescription = stringResource(R.string.app_media_select),
                        onClick = selection::enter,
                    )
                }
            },
        )
        if (selection.active) {
            SelectionBar(
                entries = selection.picked.values.toList(),
                onDelete = { deletes.ask(selection.picked.values.toList(), onDeleted = selection::clear) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun SelectionBar(entries: List<MediaEntry>, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val count = entries.size
    GlassSurface(
        kind = GlassKind.TabBar,
        shape = FrameShapes.Chip,
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = FrameSpacing.S16, vertical = FrameSpacing.S8)
            .fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = FrameSpacing.S16, vertical = FrameSpacing.S8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
        ) {
            Text(
                text = metaLine(
                    listOf(
                        pluralStringResource(R.plurals.app_media_selected, count, count),
                        fileSizeLabel(entries.sumOf { it.sizeBytes }),
                    ),
                ),
                style = FrameType.Label,
                color = FrameColors.White,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
            DangerPill(
                text = stringResource(R.string.app_menu_delete),
                onClick = onDelete,
                enabled = count > 0,
            )
        }
    }
}

/** A used and free bar. Videos are the share this app can do something about, so they take the accent. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StorageHeader(summary: StorageSummary?) {
    if (summary == null) {
        Skeleton(Modifier.fillMaxWidth().height(HeaderSkeletonHeight), FrameShapes.Card)
        return
    }
    val used = (summary.deviceTotalBytes - summary.deviceFreeBytes).coerceAtLeast(0)
    val others = (used - summary.videoBytes).coerceAtLeast(0)
    val videoColor = FrameColors.Accent
    val otherColor = FrameColors.White.copy(alpha = OtherFill)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FrameShapes.Card)
            .background(FrameColors.Card)
            .padding(FrameSpacing.S16),
        verticalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
    ) {
        Text(
            text = stringResource(
                R.string.app_media_used_free,
                fileSizeLabel(used),
                fileSizeLabel(summary.deviceFreeBytes),
            ),
            style = FrameType.Title,
            color = FrameColors.White,
        )
        StorageBar(summary.videoBytes, others, summary.deviceTotalBytes, videoColor, otherColor)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S16),
            verticalArrangement = Arrangement.spacedBy(FrameSpacing.S4),
        ) {
            Legend(videoColor, stringResource(R.string.app_media_legend_videos), fileSizeLabel(summary.videoBytes))
            Legend(otherColor, stringResource(R.string.app_media_legend_other), fileSizeLabel(others))
            Legend(FrameColors.Elevated, stringResource(R.string.app_media_legend_free), fileSizeLabel(summary.deviceFreeBytes))
        }
        Text(
            text = metaLine(
                listOf(
                    pluralStringResource(R.plurals.app_videos, summary.videoCount, summary.videoCount),
                    pluralStringResource(R.plurals.app_media_folders, summary.folderCount, summary.folderCount),
                ),
            ),
            style = FrameType.Label,
            color = FrameColors.SecondaryText,
        )
    }
}

// Storage is not a time line, so unlike a progress line it fills from the start edge in both languages.
@Composable
private fun StorageBar(videos: Long, others: Long, total: Long, videoColor: Color, otherColor: Color) {
    val track = FrameColors.Elevated
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(BarHeight)
            .clip(FrameShapes.Chip)
            .background(track),
    ) {
        if (total <= 0) return@Canvas
        val rtl = layoutDirection == LayoutDirection.Rtl
        fun segment(from: Float, to: Float, color: Color) {
            val left = if (rtl) size.width * (1f - to) else size.width * from
            drawRect(color, Offset(left, 0f), Size(size.width * (to - from), size.height))
        }
        val videoEnd = (videos.toFloat() / total).coerceIn(0f, 1f)
        val usedEnd = ((videos + others).toFloat() / total).coerceIn(videoEnd, 1f)
        segment(0f, videoEnd, videoColor)
        segment(videoEnd, usedEnd, otherColor)
    }
}

@Composable
private fun Legend(color: Color, label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S4),
    ) {
        Box(Modifier.size(LegendDot).background(color, CircleShape))
        Text(
            text = metaLine(listOf(label, value)),
            style = FrameType.Caption,
            color = FrameColors.SecondaryText,
            maxLines = 1,
        )
    }
}

private fun LazyListScope.mediaRows(entries: List<MediaEntry>?, selection: Selection) {
    when {
        entries == null -> items(RowSkeletons, key = { "skeleton$it" }) {
            Skeleton(Modifier.fillMaxWidth().height(RowSkeletonHeight))
        }
        entries.isEmpty() -> item(key = "empty") {
            Text(
                text = stringResource(R.string.app_media_empty),
                style = FrameType.Body,
                color = FrameColors.SecondaryText,
                modifier = Modifier.padding(vertical = FrameSpacing.S24),
            )
        }
        else -> items(entries, key = { it.id }, contentType = { "media" }) { entry ->
            val navigator = LocalNavigator.current
            val library = LocalContext.current.frameServices.library
            val scope = rememberCoroutineScope()
            MediaRow(
                entry = entry,
                selecting = selection.active,
                selected = entry.id in selection.picked,
                onClick = {
                    if (selection.active) {
                        selection.toggle(entry)
                    } else if (entry.kind != MediaEntryKind.FILE) {
                        navigator.push(Route.MediaItems(entry.kind, entry.id, entry.name, entry.titleId, entry.folderPath))
                    } else {
                        scope.launch { navigator.openFile(entry, library.hasDetails(entry.titleId)) }
                    }
                },
                onLongClick = {
                    if (selection.active) selection.toggle(entry) else selection.start(entry)
                },
            )
        }
    }
}

/** A hidden title has no Details page, so its file opens at its folder. */
private suspend fun LibraryRepository.hasDetails(titleId: Long?): Boolean =
    titleId != null && title(titleId).first() != null

private fun Navigator.openFile(entry: MediaEntry, hasDetails: Boolean) {
    val titleId = entry.titleId
    val folder = entry.folderPath
    when {
        titleId != null && hasDetails -> details(titleId)
        folder != null -> push(Route.Folder(folder))
    }
}

@Composable
private fun MediaRow(
    entry: MediaEntry,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    note: String? = null,
) {
    PressableGlass(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = FrameShapes.Poster,
        onLongClick = onLongClick,
        highlight = false,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Thumbnail(entry)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = bidiIsolate(entry.name),
                    style = FrameType.Body.copy(fontWeight = FontWeight.W500),
                    color = FrameColors.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = metaLine(entryFacts(entry)),
                    style = FrameType.Caption,
                    color = FrameColors.SecondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                entry.detail?.let { path ->
                    Text(
                        text = bidiIsolate(path),
                        style = FrameType.Caption,
                        color = FrameColors.SecondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                note?.let {
                    Text(
                        text = it,
                        style = FrameType.Caption,
                        color = FrameColors.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when {
                selecting -> Checkbox(checked = selected, onCheckedChange = null)
                entry.kind != MediaEntryKind.FILE -> Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    null,
                    tint = FrameColors.SecondaryText,
                )
            }
        }
    }
}

@Composable
private fun entryFacts(entry: MediaEntry): List<String> = buildList {
    add(fileSizeLabel(entry.sizeBytes))
    if (entry.kind != MediaEntryKind.FILE) {
        add(pluralStringResource(R.plurals.app_videos, entry.fileCount, entry.fileCount))
    }
    entry.durationMs?.let { duration ->
        val minutes = (duration / MillisPerMinute).toInt()
        add(if (entry.kind == MediaEntryKind.FILE || minutes == 0) clockLabel(duration) else durationLabel(minutes))
    }
    entry.height?.let { add(stringResource(R.string.app_resolution, localNumber(it))) }
}

@Composable
private fun Thumbnail(entry: MediaEntry) {
    Box(
        modifier = Modifier
            .width(ThumbWidth)
            .aspectRatio(FrameSizes.LandscapeRatio)
            .clip(FrameShapes.Poster)
            .background(FrameColors.Elevated),
        contentAlignment = Alignment.Center,
    ) {
        if (entry.coverPath != null) {
            CoverImage(entry.coverPath, null, Modifier.matchParentSize())
        } else {
            Icon(
                imageVector = when (entry.kind) {
                    MediaEntryKind.FOLDER -> Icons.Rounded.Folder
                    MediaEntryKind.CLIP_GROUP -> Icons.Rounded.VideoLibrary
                    else -> Icons.Rounded.Movie
                },
                contentDescription = null,
                modifier = Modifier.size(RowIcon),
                tint = FrameColors.SecondaryText,
            )
        }
    }
}
