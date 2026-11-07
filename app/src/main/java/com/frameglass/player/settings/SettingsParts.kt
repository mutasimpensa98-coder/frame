package com.frameglass.player.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameShapes
import com.frameglass.glass.FrameSizes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameType
import com.frameglass.glass.GlassMenu
import com.frameglass.glass.GlassRow
import com.frameglass.glass.GlassSource
import com.frameglass.glass.TopGlassBar
import com.frameglass.glass.bidiIsolate
import com.frameglass.glass.glassScrolled
import com.frameglass.player.library.BackButton
import com.frameglass.player.ui.navigationInset
import com.frameglass.player.ui.statusInset

private val RowMinHeight = 64.dp
private const val ValueMaxShare = 0.45f
private val RowTitle = FrameType.Body.copy(fontWeight = FontWeight.W500)
private val IconTile = 40.dp
private val IconTileShape = RoundedCornerShape(12.dp)
private val TileIcon = 22.dp
private val ChevronSize = 24.dp

/** The divider inset of a [SettingsLink], which starts under its text. */
internal val LinkDividerInset = FrameSpacing.S16 + IconTile + FrameSpacing.S12

/** One page opened from the main Settings list, with its title in the top bar and a back arrow. */
@Composable
internal fun SettingsPage(title: String, content: LazyListScope.() -> Unit) {
    val list = rememberLazyListState()
    Box(Modifier.fillMaxSize().background(FrameColors.Canvas)) {
        GlassSource(Modifier.fillMaxSize()) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = FrameSpacing.S16,
                    end = FrameSpacing.S16,
                    top = statusInset() + FrameSizes.TopBarHeight,
                    bottom = navigationInset() + FrameSpacing.S32,
                ),
                verticalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
                content = content,
            )
        }
        TopGlassBar(
            title = title,
            scrolled = { list.glassScrolled() },
            navigation = { BackButton() },
        )
    }
}

/** A small label over one grouped dark card, as in image 05. */
@Composable
internal fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.padding(top = FrameSpacing.S16),
        verticalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
    ) {
        SectionLabel(title)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(FrameShapes.Card)
                .background(FrameColors.Card),
            content = content,
        )
    }
}

@Composable
internal fun SectionLabel(title: String) {
    Text(
        text = bidiIsolate(title),
        style = FrameType.Label,
        color = FrameColors.SecondaryText,
        modifier = Modifier.padding(horizontal = FrameSpacing.S8),
    )
}

@Composable
internal fun SettingsDivider(start: Dp = FrameSpacing.S24) {
    HorizontalDivider(Modifier.padding(start = start), 0.5.dp, FrameColors.Divider)
}

/** A row of the main list: icon, title, short description, the value if there is one, and a chevron. */
@Composable
internal fun SettingsLink(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    value: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = RowMinHeight)
            .padding(start = FrameSpacing.S16, end = FrameSpacing.S8, top = FrameSpacing.S12, bottom = FrameSpacing.S12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
    ) {
        Box(
            modifier = Modifier.size(IconTile).background(FrameColors.Elevated, IconTileShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(TileIcon), tint = FrameColors.White)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = bidiIsolate(title),
                style = RowTitle,
                color = FrameColors.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(description, style = FrameType.Label, color = FrameColors.SecondaryText)
        }
        if (value != null) {
            Text(
                text = bidiIsolate(value),
                style = FrameType.Label,
                color = FrameColors.Accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(ChevronSize),
            tint = FrameColors.SecondaryText,
        )
    }
}

/** Title and description on the start side, the value on the end side. */
@Composable
internal fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = RowMinHeight)
            .padding(horizontal = FrameSpacing.S24, vertical = FrameSpacing.S12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = bidiIsolate(title),
                style = RowTitle,
                color = FrameColors.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (description != null) {
                Text(description, style = FrameType.Label, color = FrameColors.SecondaryText)
            }
        }
        if (value != null) {
            Text(
                text = bidiIsolate(value),
                style = FrameType.Body,
                color = FrameColors.Accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(ValueMaxShare, fill = false),
            )
        }
        trailing?.invoke()
    }
}

@Composable
internal fun ToggleRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    description: String? = null,
) {
    SettingRow(
        title = title,
        description = description,
        onClick = { onChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}

/** A value picked from a short glass menu that opens on the row. */
@Composable
internal fun <T> ChoiceRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    description: String? = null,
    shown: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        SettingRow(
            title = title,
            description = description,
            value = shown ?: options.firstOrNull { it.first == selected }?.second,
            onClick = { open = true },
        )
        GlassMenu(expanded = open, onDismiss = { open = false }) {
            options.forEach { (option, label) ->
                GlassRow(
                    text = label,
                    selected = option == selected,
                    onClick = {
                        open = false
                        if (option != selected) onSelect(option)
                    },
                )
            }
        }
    }
}
