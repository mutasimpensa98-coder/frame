package com.frameglass.player.onboarding

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.frameglass.data.api.ApiKeyKind
import com.frameglass.data.api.frameServices
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameMotion
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameType
import com.frameglass.glass.OutlinePill
import com.frameglass.glass.WhitePill
import com.frameglass.player.R
import com.frameglass.player.folders.openAllFilesAccess
import com.frameglass.player.folders.rememberAllFilesAccess
import com.frameglass.player.nav.LocalNavigator
import com.frameglass.player.ui.FrameTextField
import kotlinx.coroutines.launch

private const val FilesStep = 0
private const val KeyStep = 1
private const val SleepStep = 2
private const val StepCount = 2
private val StepDot = 8.dp

/**
 * F-U15: All Files access, an optional key, then the Never sleeping hint. Coming back from the access
 * screen counts as an app open, and that is what starts the search of the phone (waiver of 2026-11-07).
 */
@Composable
internal fun OnboardingScreen() {
    val navigator = LocalNavigator.current
    val store = LocalContext.current.frameServices.settings
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(FilesStep) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Box(
        Modifier
            .fillMaxSize()
            .background(FrameColors.Canvas)
            .safeDrawingPadding()
            .padding(horizontal = FrameSpacing.S24, vertical = FrameSpacing.S16),
    ) {
        if (step < StepCount) {
            StepDots(step, Modifier.align(Alignment.TopCenter).padding(top = FrameSpacing.S16))
        }
        AnimatedContent(
            targetState = step,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { fadeIn(FrameMotion.effects()) togetherWith fadeOut(FrameMotion.effects()) },
            label = "onboarding",
        ) { current ->
            when (current) {
                FilesStep -> FilesStep(onNext = { step = KeyStep })
                KeyStep -> KeyStep(onNext = { step = SleepStep })
                else -> SleepStep(
                    onDone = {
                        notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        scope.launch {
                            store.update { it.copy(onboardingDone = true, neverSleepingHintDone = true) }
                            navigator.finishOnboarding()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun StepDots(step: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S8)) {
        repeat(StepCount) { index ->
            Box(
                Modifier
                    .size(StepDot)
                    .alpha(if (index == step) 1f else 0.3f)
                    .background(FrameColors.White, CircleShape),
            )
        }
    }
}

/** One step: a large title and a short body in the middle, the actions at the bottom. */
@Composable
private fun StepLayout(
    title: String,
    body: String,
    actions: @Composable ColumnScope.() -> Unit,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
        ) {
            Text(title, style = FrameType.Display, color = FrameColors.White)
            Text(body, style = FrameType.Body, color = FrameColors.SecondaryText)
            content?.invoke(this)
        }
        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(FrameSpacing.S12),
            content = actions,
        )
    }
}

@Composable
private fun FilesStep(onNext: () -> Unit) {
    val context = LocalContext.current
    val granted = rememberAllFilesAccess()
    LaunchedEffect(granted) {
        if (granted) onNext()
    }
    StepLayout(
        title = stringResource(R.string.app_onboarding_files_title),
        body = stringResource(R.string.app_onboarding_files_body),
        actions = {
            WhitePill(
                text = stringResource(R.string.app_files_allow),
                onClick = { context.openAllFilesAccess() },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinePill(
                text = stringResource(R.string.app_onboarding_later),
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun KeyStep(onNext: () -> Unit) {
    val store = LocalContext.current.frameServices.settings
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    StepLayout(
        title = stringResource(R.string.app_onboarding_key_title),
        body = stringResource(R.string.app_onboarding_key_body),
        content = {
            Spacer(Modifier.height(FrameSpacing.S4))
            FrameTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = stringResource(R.string.app_key_placeholder),
                secret = true,
                autoCorrect = false,
                keyboardType = KeyboardType.Password,
            )
        },
        actions = {
            WhitePill(
                text = stringResource(R.string.app_save),
                onClick = {
                    val typed = text.trim()
                    scope.launch {
                        store.setKey(ApiKeyKind.TMDB, typed)
                        onNext()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = text.isNotBlank(),
            )
            OutlinePill(
                text = stringResource(R.string.app_onboarding_skip),
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

/**
 * Samsung has no public link to its Never sleeping list, so this opens Android's battery list and the
 * text gives the path. Whether One UI lands somewhere useful has not been checked on the phone.
 */
@Composable
private fun SleepStep(onDone: () -> Unit) {
    val context = LocalContext.current
    StepLayout(
        title = stringResource(R.string.app_onboarding_sleep_title),
        body = stringResource(R.string.app_onboarding_sleep_body),
        actions = {
            WhitePill(
                text = stringResource(R.string.app_onboarding_sleep_open),
                onClick = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinePill(
                text = stringResource(R.string.app_onboarding_finish),
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}
