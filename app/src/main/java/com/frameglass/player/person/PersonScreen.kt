package com.frameglass.player.person

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameglass.data.api.PersonView
import com.frameglass.data.api.frameServices
import com.frameglass.glass.CoverImage
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameSizes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameType
import com.frameglass.glass.TopGlassBar
import com.frameglass.glass.glassScrolled
import com.frameglass.player.R
import com.frameglass.player.library.BackButton
import com.frameglass.player.ui.TitleGrid
import com.frameglass.player.ui.fullLine
import com.frameglass.player.ui.navigationInset
import com.frameglass.player.ui.statusInset

private val PhotoSize = 112.dp

/** F-U8: the titles of one person in this library, joined on the person id. */
@Composable
internal fun PersonScreen(personId: Long) {
    val library = LocalContext.current.frameServices.library
    val person by remember(personId) { library.person(personId) }.collectAsStateWithLifecycle(null as PersonView?)
    val grid = rememberLazyGridState()
    val current = person

    Box(Modifier.fillMaxSize().background(FrameColors.Canvas)) {
        TitleGrid(
            titles = current?.titles.orEmpty(),
            source = "person:$personId",
            state = grid,
            top = statusInset() + FrameSizes.TopBarHeight,
            bottom = navigationInset() + FrameSpacing.S24,
            header = {
                if (current != null) {
                    fullLine("person") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(bottom = FrameSpacing.S8),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
                        ) {
                            if (current.photoPath != null) {
                                CoverImage(current.photoPath, null, Modifier.size(PhotoSize), CircleShape)
                            } else {
                                Box(
                                    Modifier.size(PhotoSize).background(FrameColors.Elevated, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Rounded.Person,
                                        null,
                                        Modifier.size(48.dp),
                                        tint = FrameColors.SecondaryText,
                                    )
                                }
                            }
                            val count = current.titles.size
                            Text(
                                text = pluralStringResource(R.plurals.app_titles_in_library, count, count),
                                style = FrameType.Label,
                                color = FrameColors.SecondaryText,
                            )
                        }
                    }
                }
            },
        )
        TopGlassBar(
            title = current?.name.orEmpty(),
            scrolled = { grid.glassScrolled() },
            navigation = { BackButton() },
        )
    }
}
