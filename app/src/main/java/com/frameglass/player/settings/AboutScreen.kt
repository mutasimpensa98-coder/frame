package com.frameglass.player.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameShapes
import com.frameglass.glass.FrameSizes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameType
import com.frameglass.glass.GlassSheet
import com.frameglass.glass.GlassSource
import com.frameglass.glass.TopGlassBar
import com.frameglass.glass.bidiIsolate
import com.frameglass.glass.glassSheetOrigin
import com.frameglass.glass.rememberGlassSheetState
import com.frameglass.player.R
import com.frameglass.player.library.BackButton
import com.frameglass.player.ui.navigationInset
import com.frameglass.player.ui.statusInset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val AppMarkSize = 112.dp

// F-M12: well under the app mark and the 24 sp app name.
private val TmdbLogoHeight = 14.dp

private const val GplText = "licenses/GPL-3.0.txt"
private const val MitText = "licenses/MIT-ass-media.txt"
private const val ApacheText = "licenses/Apache-2.0.txt"
private val OflTexts = listOf(
    "licenses/OFL-alexandria.txt",
    "licenses/OFL-ibmplexsansarabic.txt",
    "fonts/OFL-NotoNaskhArabic.txt",
)

/** F-M12: the app logo first, then the TMDB notice, the IMDb statement and the licences. */
@Composable
internal fun AboutScreen() {
    val context = LocalContext.current
    val scroll = rememberScrollState()
    val version = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }

    Box(Modifier.fillMaxSize().background(FrameColors.Canvas)) {
        GlassSource(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = FrameSpacing.S16)
                    .padding(
                        top = statusInset() + FrameSizes.TopBarHeight,
                        bottom = navigationInset() + FrameSpacing.S32,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(FrameSpacing.S16),
            ) {
                // The mark is drawn white for the launcher, so it takes the content colour of the theme here.
                Image(
                    painter = painterResource(R.drawable.app_icon_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(AppMarkSize),
                    colorFilter = ColorFilter.tint(FrameColors.White),
                )
                Text(stringResource(R.string.app_name), style = FrameType.TitleLarge, color = FrameColors.White)
                Text(
                    text = bidiIsolate(stringResource(R.string.app_version, version)),
                    style = FrameType.Label,
                    color = FrameColors.SecondaryText,
                )
                AboutCard {
                    TmdbLogo(stringResource(R.string.app_tmdb_logo), TmdbLogoHeight)
                    Text(
                        text = bidiIsolate(stringResource(R.string.app_tmdb_notice)),
                        style = FrameType.Body,
                        color = FrameColors.White,
                    )
                    val translated = stringResource(R.string.app_tmdb_notice_translation)
                    if (translated.isNotEmpty()) {
                        Text(translated, style = FrameType.Label, color = FrameColors.SecondaryText)
                    }
                    Text(
                        text = stringResource(R.string.app_imdb_statement),
                        style = FrameType.Body,
                        color = FrameColors.White,
                    )
                }
                AboutCard {
                    Text(
                        text = stringResource(R.string.app_licences),
                        style = FrameType.Title,
                        color = FrameColors.White,
                    )
                    Licence("GPL-3.0", stringResource(R.string.app_licence_gpl), listOf(GplText))
                    Licence("MIT", stringResource(R.string.app_licence_mit), listOf(MitText))
                    Licence("Apache-2.0", stringResource(R.string.app_licence_apache), listOf(ApacheText))
                    Licence("OFL-1.1", stringResource(R.string.app_licence_ofl), OflTexts)
                }
            }
        }
        TopGlassBar(
            title = stringResource(R.string.app_about_title),
            scrolled = { scroll.value.toFloat() },
            navigation = { BackButton() },
        )
    }
}

@Composable
private fun AboutCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FrameShapes.Card)
            .background(FrameColors.Card)
            .padding(FrameSpacing.S24),
        verticalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
    ) {
        content()
    }
}

/** A licence and what it covers. Tapping opens its full text (D2). */
@Composable
private fun Licence(name: String, covers: String, assets: List<String>) {
    val sheet = rememberGlassSheetState()
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = null, indication = null, onClick = sheet::open)
            .glassSheetOrigin(sheet),
    ) {
        Text(bidiIsolate(name), style = FrameType.Label, color = FrameColors.White)
        Text(covers, style = FrameType.Label, color = FrameColors.SecondaryText)
    }
    GlassSheet(sheet, title = name) {
        val context = LocalContext.current
        val text by produceState("", assets) {
            value = withContext(Dispatchers.IO) {
                assets.joinToString("\n\n") { path ->
                    context.assets.open(path).bufferedReader().use { it.readText() }
                }
            }
        }
        Text(
            text = bidiIsolate(text),
            style = FrameType.Caption,
            color = FrameColors.SecondaryText,
            modifier = Modifier.padding(horizontal = FrameSpacing.S12),
        )
    }
}
