package com.frameglass.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val PillHeight = FrameSpacing.S48
private val PillIconSize = 24.dp
private val ChipMinHeight = 36.dp
private val DropdownMinHeight = 40.dp
private val DropdownShape = RoundedCornerShape(12.dp)
private const val ChipSelectedFill = 0.12f
private const val ChipSelectedRim = 0.56f
private val PillLabel = FrameType.Body.copy(fontWeight = FontWeight.W600)
private const val DisabledAlpha = 0.4f

/**
 * The primary action, and the one strong element on a screen. The name comes from the dark look: it
 * takes the accent, and turns dark on the light theme.
 */
@Composable
fun WhitePill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = FrameTheme.colors
    val fill = if (enabled) colors.accentFill else colors.accentFill.copy(alpha = DisabledAlpha)
    PressableGlass(
        onClick = onClick,
        modifier = modifier,
        shape = FrameShapes.Chip,
        enabled = enabled,
        highlight = false,
    ) {
        PillContent(text, icon, colors.onAccent, Modifier.background(fill, FrameShapes.Chip))
    }
}

/** A secondary action with a thin outline. */
@Composable
fun OutlinePill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = FrameTheme.colors
    val content = if (enabled) colors.content else colors.content.copy(alpha = DisabledAlpha)
    PressableGlass(
        onClick = onClick,
        modifier = modifier,
        shape = FrameShapes.Chip,
        enabled = enabled,
    ) {
        PillContent(text, icon, content, Modifier.border(1.dp, colors.outline, FrameShapes.Chip))
    }
}

@Composable
private fun PillContent(text: String, icon: ImageVector?, color: Color, modifier: Modifier) {
    Row(
        modifier = modifier
            .heightIn(min = PillHeight)
            .padding(horizontal = FrameSpacing.S24, vertical = FrameSpacing.S8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S8, Alignment.CenterHorizontally),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(PillIconSize), tint = color)
        }
        Text(
            text = bidiIsolate(text),
            style = PillLabel,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A round glass button. Pass an auto-mirrored icon for arrows that flip in RTL. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    size: Dp = 44.dp,
) {
    PressableGlass(
        onClick = onClick,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(size),
        onLongClick = onLongClick,
    ) {
        GlassSurface(GlassKind.Control, CircleShape) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.align(Alignment.Center).size(PillIconSize),
                tint = FrameTheme.colors.content,
            )
        }
    }
}

/** A row of single-choice chips, also used for seasons. */
@Composable
fun FilterChips(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = FrameSpacing.S16),
) {
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex) {
        val layout = listState.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
        val hidden = item == null ||
            item.offset < layout.viewportStartOffset ||
            item.offset + item.size > layout.viewportEndOffset
        if (hidden && selectedIndex in labels.indices) listState.animateScrollToItem(selectedIndex)
    }
    LazyRow(
        modifier = modifier.selectableGroup(),
        state = listState,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
    ) {
        itemsIndexed(labels) { index, label ->
            FilterChip(label, index == selectedIndex) {
                if (index != selectedIndex) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSelect(index)
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = FrameTheme.colors
    val fill by animateColorAsState(
        if (selected) colors.accent.copy(alpha = ChipSelectedFill) else colors.elevated,
        FrameMotion.effects(),
        label = "chipFill",
    )
    val rim by animateColorAsState(
        if (selected) colors.accent.copy(alpha = ChipSelectedRim) else Color.Transparent,
        FrameMotion.effects(),
        label = "chipRim",
    )
    val text by animateColorAsState(
        if (selected) colors.accent else colors.secondaryContent,
        FrameMotion.effects(),
        label = "chipText",
    )
    PressableGlass(
        onClick = onClick,
        modifier = Modifier.semantics { this.selected = selected },
        shape = FrameShapes.Chip,
        role = Role.Tab,
        highlight = false,
    ) {
        Box(
            modifier = Modifier
                .background(fill, FrameShapes.Chip)
                .border(1.dp, rim, FrameShapes.Chip)
                .heightIn(min = ChipMinHeight)
                .padding(horizontal = FrameSpacing.S16),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = bidiIsolate(label),
                style = FrameType.Label,
                color = text,
                maxLines = 1,
            )
        }
    }
}

/** A sort button that opens a [GlassMenu] of [options]. */
@Composable
fun SortDropdown(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var open by remember { mutableStateOf(false) }
    val turn by animateFloatAsState(
        if (open) 180f else 0f,
        FrameMotion.effects(),
        label = "dropdownChevron",
    )
    val label = options.getOrElse(selectedIndex) { "" }
    val description = stringResource(R.string.glass_sort_by, label)
    val colors = FrameTheme.colors

    Box(modifier) {
        PressableGlass(
            onClick = { open = true },
            modifier = Modifier.semantics { contentDescription = description },
            shape = DropdownShape,
            role = Role.DropdownList,
            highlight = false,
        ) {
            Row(
                modifier = Modifier
                    .background(colors.elevated, DropdownShape)
                    .heightIn(min = DropdownMinHeight)
                    .padding(start = FrameSpacing.S16, end = FrameSpacing.S8),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S4),
            ) {
                Text(
                    text = bidiIsolate(label),
                    style = FrameType.Label,
                    color = colors.content,
                    maxLines = 1,
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).rotate(turn),
                    tint = colors.content,
                )
            }
        }
        GlassMenu(expanded = open, onDismiss = { open = false }) {
            options.forEachIndexed { index, option ->
                GlassRow(
                    text = option,
                    selected = index == selectedIndex,
                    onClick = {
                        open = false
                        if (index != selectedIndex) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(index)
                        }
                    },
                )
            }
        }
    }
}
