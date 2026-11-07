package com.frameglass.player.vault

import android.content.Context
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Output
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameglass.data.api.TitleCard
import com.frameglass.data.api.VaultProgress
import com.frameglass.data.api.VaultState
import com.frameglass.data.api.frameServices
import com.frameglass.data.api.privatePlayRef
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameShapes
import com.frameglass.glass.FrameSizes
import com.frameglass.glass.FrameSpacing
import com.frameglass.glass.FrameType
import com.frameglass.glass.GlassIconButton
import com.frameglass.glass.GlassRow
import com.frameglass.glass.GlassSheet
import com.frameglass.glass.GlassSheetState
import com.frameglass.glass.GlassSource
import com.frameglass.glass.OutlinePill
import com.frameglass.glass.PosterCard
import com.frameglass.glass.TopGlassBar
import com.frameglass.glass.WhitePill
import com.frameglass.glass.bidiIsolate
import com.frameglass.glass.glassScrolled
import com.frameglass.glass.rememberGlassSheetState
import com.frameglass.player.R
import com.frameglass.player.library.BackButton
import com.frameglass.player.model.VaultFlow
import com.frameglass.player.nav.LocalNavigator
import com.frameglass.player.settings.ChoiceRow
import com.frameglass.player.settings.SettingRow
import com.frameglass.player.settings.SettingsDivider
import com.frameglass.player.settings.SettingsSection
import com.frameglass.player.settings.ToggleRow
import com.frameglass.player.ui.FrameTextField
import com.frameglass.player.ui.GridColumnGap
import com.frameglass.player.ui.GridLineGap
import com.frameglass.player.ui.fileSizeLabel
import com.frameglass.player.ui.fullLine
import com.frameglass.player.ui.gridPadding
import com.frameglass.player.ui.libraryColumns
import com.frameglass.player.ui.longPress
import com.frameglass.player.ui.metaLine
import com.frameglass.player.ui.navigationInset
import com.frameglass.player.ui.rememberUiModel
import com.frameglass.player.ui.statusInset
import kotlinx.coroutines.launch

private const val MinPassword = 4
private val LockChoices = listOf(0, 30, 60, 300)
private const val SecondsPerMinute = 60
private val CodeStyle = FrameType.Title.copy(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.W700)

private fun canUseFingerprint(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
        BiometricManager.BIOMETRIC_SUCCESS

/** Shows BiometricPrompt with the password as the way out. Cancelling leaves the page as it was. */
private fun FragmentActivity.askFingerprint(title: String, negative: String, onSuccess: () -> Unit) {
    val prompt = BiometricPrompt(
        this,
        mainExecutor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText(negative)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build(),
    )
}

/** Keeps every step of this page out of screenshots and the recent apps list. */
@Composable
private fun SecureWindow() {
    val window = LocalActivity.current?.window ?: return
    DisposableEffect(window) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

/** The private folder of the 2026-11-07 waiver: first use, the lock, and the videos behind it. */
@Composable
internal fun PrivateFolderScreen() {
    val model = rememberUiModel()
    val vault = LocalContext.current.frameServices.vault
    val flow = model.vault
    val state by remember(vault) { vault.state }.collectAsStateWithLifecycle(null as VaultState?)
    SecureWindow()
    DisposableEffect(flow) {
        flow.pageEntered()
        onDispose { flow.pageLeft(model.settings.value?.vaultLockSeconds ?: 0) }
    }

    // Locking by hand should not raise the fingerprint prompt the moment the lock closes.
    var lockedByHand by remember { mutableStateOf(false) }
    val current = state
    val code = flow.recoveryCode
    LaunchedEffect(current?.unlocked) {
        if (current?.unlocked == true) lockedByHand = false
    }
    Box(Modifier.fillMaxSize().background(FrameColors.Canvas)) {
        when {
            code != null -> RecoveryStep(code, flow)
            current == null -> Unit
            !current.configured || flow.settingUp -> SetupStep(flow)
            !current.unlocked -> UnlockStep(current, askAtOnce = !lockedByHand)
            else -> OpenFolder(
                state = current,
                flow = flow,
                onLock = {
                    lockedByHand = true
                    vault.lock()
                },
            )
        }
    }
}

/** A plain scrolling page under the top bar, for the steps in front of the folder. */
@Composable
private fun StepPage(showBack: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        GlassSource(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(scroll)
                    .padding(horizontal = FrameSpacing.S16)
                    .padding(
                        top = statusInset() + FrameSizes.TopBarHeight + FrameSpacing.S16,
                        bottom = navigationInset() + FrameSpacing.S32,
                    ),
                verticalArrangement = Arrangement.spacedBy(FrameSpacing.S16),
                content = content,
            )
        }
        TopGlassBar(
            title = stringResource(R.string.app_settings_private),
            scrolled = { scroll.value.toFloat() },
            navigation = if (showBack) ({ BackButton() }) else null,
        )
    }
}

@Composable
private fun Notice(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FrameShapes.Card)
            .background(FrameColors.Card)
            .padding(FrameSpacing.S16),
        verticalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
        content = content,
    )
}

@Composable
private fun PasswordField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    imeAction: ImeAction = ImeAction.Next,
    onSubmit: (() -> Unit)? = null,
) {
    FrameTextField(
        value = value,
        onValueChange = onChange,
        placeholder = placeholder,
        secret = true,
        autoCorrect = false,
        keyboardType = KeyboardType.Password,
        imeAction = imeAction,
        onSubmit = onSubmit,
    )
}

@Composable
private fun StepError(text: String?) {
    if (text != null) {
        Text(
            text = text,
            style = FrameType.Label,
            color = FrameColors.Error,
            modifier = Modifier.padding(horizontal = FrameSpacing.S8),
        )
    }
}

/** Two fields that must agree, for any new password. Returns the password once it is good, else null. */
@Composable
private fun NewPasswordFields(onReady: (String?) -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val short = first.isNotEmpty() && first.length < MinPassword
    val differ = second.isNotEmpty() && first != second
    LaunchedEffect(first, second) {
        onReady(first.takeIf { it.length >= MinPassword && it == second })
    }
    PasswordField(first, { first = it }, stringResource(R.string.app_private_new_password))
    PasswordField(second, { second = it }, stringResource(R.string.app_private_repeat_password), ImeAction.Done)
    StepError(
        when {
            short -> pluralStringResource(R.plurals.app_private_password_short, MinPassword, MinPassword)
            differ -> stringResource(R.string.app_private_password_differ)
            else -> null
        },
    )
}

@Composable
private fun SetupStep(flow: VaultFlow) {
    var password by remember { mutableStateOf<String?>(null) }
    StepPage {
        Text(
            text = stringResource(R.string.app_private_intro),
            style = FrameType.Body,
            color = FrameColors.White,
        )
        Notice {
            Text(
                text = stringResource(R.string.app_private_uninstall_warning),
                style = FrameType.Body.copy(fontWeight = FontWeight.W600),
                color = FrameColors.Warning,
            )
            Text(
                text = stringResource(R.string.app_private_not_encrypted),
                style = FrameType.Body,
                color = FrameColors.SecondaryText,
            )
        }
        NewPasswordFields { password = it }
        val ready = password
        WhitePill(
            text = stringResource(R.string.app_private_create),
            onClick = { ready?.let(flow::setup) },
            modifier = Modifier.fillMaxWidth(),
            enabled = ready != null && !flow.settingUp,
        )
    }
}

/** Shown once, with no way back until the owner says the code is saved. */
@Composable
private fun RecoveryStep(code: String, flow: VaultFlow) {
    var saved by remember { mutableStateOf(false) }
    BackHandler { }
    StepPage(showBack = false) {
        Text(
            text = stringResource(R.string.app_private_code_title),
            style = FrameType.TitleLarge,
            color = FrameColors.White,
        )
        Notice {
            Text(
                text = bidiIsolate(code),
                style = CodeStyle,
                color = FrameColors.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            text = stringResource(R.string.app_private_code_warning),
            style = FrameType.Body.copy(fontWeight = FontWeight.W600),
            color = FrameColors.Warning,
        )
        Text(
            text = stringResource(R.string.app_private_code_hint),
            style = FrameType.Body,
            color = FrameColors.SecondaryText,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = null, indication = null) { saved = !saved },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FrameSpacing.S8),
        ) {
            Checkbox(checked = saved, onCheckedChange = { saved = it })
            Text(
                text = stringResource(R.string.app_private_code_saved),
                style = FrameType.Body,
                color = FrameColors.White,
            )
        }
        WhitePill(
            text = stringResource(R.string.app_private_continue),
            onClick = flow::codeSaved,
            modifier = Modifier.fillMaxWidth(),
            enabled = saved,
        )
    }
}

@Composable
private fun UnlockStep(state: VaultState, askAtOnce: Boolean) {
    val context = LocalContext.current
    val vault = context.frameServices.vault
    val activity = LocalActivity.current as? FragmentActivity
    val scope = rememberCoroutineScope()
    val reset = rememberGlassSheetState()
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var wrong by remember { mutableStateOf(false) }
    val available = remember(context) { canUseFingerprint(context) }
    val fingerprint = state.biometricEnabled && activity != null && available
    val promptTitle = stringResource(R.string.app_private_fingerprint_title)
    val promptNegative = stringResource(R.string.app_private_use_password)
    val askFingerprint = {
        activity?.askFingerprint(promptTitle, promptNegative) { vault.unlockAfterBiometric() }
    }
    LaunchedEffect(fingerprint) {
        if (fingerprint && askAtOnce) askFingerprint()
    }
    val unlock = {
        if (password.isNotEmpty() && !busy) {
            busy = true
            wrong = false
            scope.launch {
                wrong = !vault.unlockWithPassword(password)
                password = ""
                busy = false
            }
        }
    }

    StepPage {
        Text(
            text = stringResource(R.string.app_private_locked),
            style = FrameType.Body,
            color = FrameColors.SecondaryText,
        )
        PasswordField(
            value = password,
            onChange = {
                password = it
                wrong = false
            },
            placeholder = stringResource(R.string.app_private_password),
            imeAction = ImeAction.Done,
            onSubmit = { unlock() },
        )
        StepError(if (wrong) stringResource(R.string.app_private_wrong_password) else null)
        WhitePill(
            text = stringResource(if (busy) R.string.app_private_checking else R.string.app_private_unlock),
            onClick = { unlock() },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Lock,
            enabled = password.isNotEmpty() && !busy,
        )
        if (fingerprint) {
            OutlinePill(
                text = stringResource(R.string.app_private_use_fingerprint),
                onClick = { askFingerprint() },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Fingerprint,
            )
        }
        Text(
            text = stringResource(R.string.app_private_forgot),
            style = FrameType.Body,
            color = FrameColors.SecondaryText,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = null, indication = null, onClick = reset::open)
                .padding(FrameSpacing.S8),
            textAlign = TextAlign.Center,
        )
    }
    ResetSheet(reset)
}

@Composable
private fun OpenFolder(state: VaultState, flow: VaultFlow, onLock: () -> Unit) {
    val services = LocalContext.current.frameServices
    val vault = services.vault
    val navigator = LocalNavigator.current
    val items by remember(vault) { vault.items }.collectAsStateWithLifecycle(emptyList())
    val progress by remember(vault) { vault.progress }.collectAsStateWithLifecycle(null as VaultProgress?)
    val grid = rememberLazyGridState()
    val itemSheet = rememberGlassSheetState()
    var picked by remember { mutableStateOf<TitleCard?>(null) }
    val top = statusInset() + FrameSizes.TopBarHeight
    val bottom = navigationInset() + FrameSpacing.S32
    val moreLabel = stringResource(R.string.app_more)

    Box(Modifier.fillMaxSize()) {
        GlassSource(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = libraryColumns(asList = false),
                state = grid,
                modifier = Modifier.fillMaxSize(),
                contentPadding = gridPadding(top, bottom),
                horizontalArrangement = Arrangement.spacedBy(GridColumnGap),
                verticalArrangement = Arrangement.spacedBy(GridLineGap),
            ) {
                fullLine("summary") {
                    Text(
                        text = metaLine(
                            listOf(
                                pluralStringResource(R.plurals.app_videos, state.itemCount, state.itemCount),
                                fileSizeLabel(state.totalBytes),
                            ),
                        ),
                        style = FrameType.Label,
                        color = FrameColors.SecondaryText,
                    )
                }
                progress?.let { now -> fullLine("moving") { MoveProgress(now) } }
                items(items, key = { it.id }, contentType = { "poster" }) { card ->
                    PosterCard(
                        title = card.name,
                        coverPath = card.coverPath,
                        onClick = { navigator.play(card.privatePlayRef()) },
                        modifier = Modifier.longPress(moreLabel) {
                            picked = card
                            itemSheet.open()
                        },
                        year = card.year,
                        progress = card.progress,
                        watched = card.watched,
                        unavailable = card.unavailable,
                    )
                }
                fullLine("hint") {
                    Text(
                        text = stringResource(if (items.isEmpty()) R.string.app_private_empty else R.string.app_private_hint),
                        style = FrameType.Body,
                        color = FrameColors.SecondaryText,
                    )
                }
                fullLine("security") { SecuritySection(state) }
            }
        }
        TopGlassBar(
            title = stringResource(R.string.app_settings_private),
            scrolled = { grid.glassScrolled() },
            navigation = { BackButton() },
            actions = {
                GlassIconButton(
                    icon = Icons.Rounded.Lock,
                    contentDescription = stringResource(R.string.app_private_lock_now),
                    onClick = onLock,
                )
            },
        )
    }
    GlassSheet(itemSheet, title = picked?.name) {
        val card = picked
        if (card != null) {
            GlassRow(
                text = stringResource(R.string.app_play),
                icon = Icons.Rounded.PlayArrow,
                onClick = {
                    itemSheet.close()
                    navigator.play(card.privatePlayRef())
                },
            )
            GlassRow(
                text = stringResource(R.string.app_private_move_out),
                supporting = stringResource(R.string.app_private_move_out_hint),
                icon = Icons.Rounded.Output,
                onClick = {
                    itemSheet.close()
                    flow.moveOut(card.id)
                },
            )
        }
    }
}

@Composable
private fun SecuritySection(state: VaultState) {
    val model = rememberUiModel()
    val context = LocalContext.current
    val vault = context.frameServices.vault
    val activity = LocalActivity.current as? FragmentActivity
    val scope = rememberCoroutineScope()
    val settings by model.settings.collectAsStateWithLifecycle()
    val change = rememberGlassSheetState()
    val reset = rememberGlassSheetState()
    val available = remember { canUseFingerprint(context) }
    val promptTitle = stringResource(R.string.app_private_fingerprint_title)
    val promptNegative = stringResource(R.string.app_cancel)

    SettingsSection(stringResource(R.string.app_private_security)) {
        if (available && activity != null) {
            ToggleRow(
                title = stringResource(R.string.app_private_fingerprint),
                description = stringResource(R.string.app_private_fingerprint_hint),
                checked = state.biometricEnabled,
                onChange = { on ->
                    if (on) {
                        activity.askFingerprint(promptTitle, promptNegative) {
                            scope.launch { vault.setBiometricEnabled(true) }
                        }
                    } else {
                        scope.launch { vault.setBiometricEnabled(false) }
                    }
                },
            )
        } else {
            SettingRow(
                title = stringResource(R.string.app_private_fingerprint),
                description = stringResource(R.string.app_private_fingerprint_missing),
            )
        }
        SettingsDivider()
        ChoiceRow(
            title = stringResource(R.string.app_private_lock_after),
            description = stringResource(R.string.app_private_lock_after_hint),
            options = LockChoices.map { it to lockLabel(it) },
            selected = settings?.vaultLockSeconds ?: 0,
            onSelect = { value -> model.updateSettings { it.copy(vaultLockSeconds = value) } },
        )
        SettingsDivider()
        SettingRow(title = stringResource(R.string.app_private_change_password), onClick = change::open)
        SettingsDivider()
        SettingRow(title = stringResource(R.string.app_private_reset), onClick = reset::open)
    }
    ChangePasswordSheet(change)
    ResetSheet(reset)
}

@Composable
private fun lockLabel(seconds: Int): String = when {
    seconds == 0 -> stringResource(R.string.app_private_lock_at_once)
    seconds < SecondsPerMinute -> pluralStringResource(R.plurals.app_seconds, seconds, seconds)
    else -> (seconds / SecondsPerMinute).let { pluralStringResource(R.plurals.app_minutes, it, it) }
}

@Composable
private fun ChangePasswordSheet(state: GlassSheetState) {
    val vault = LocalContext.current.frameServices.vault
    val scope = rememberCoroutineScope()
    GlassSheet(state, title = stringResource(R.string.app_private_change_password)) {
        var old by remember { mutableStateOf("") }
        var new by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        var wrong by remember { mutableStateOf(false) }
        LaunchedEffect(state.isOpen) {
            if (!state.isOpen) {
                old = ""
                wrong = false
            }
        }
        PasswordField(
            value = old,
            onChange = {
                old = it
                wrong = false
            },
            placeholder = stringResource(R.string.app_private_old_password),
        )
        NewPasswordFields { new = it }
        StepError(if (wrong) stringResource(R.string.app_private_wrong_password) else null)
        val ready = new
        WhitePill(
            text = stringResource(R.string.app_save),
            onClick = {
                if (ready != null) {
                    busy = true
                    scope.launch {
                        val changed = vault.changePassword(old, ready)
                        wrong = !changed
                        busy = false
                        if (changed) state.close()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = ready != null && old.isNotEmpty() && !busy,
        )
    }
}

/** Opens the folder with a new password, so it works from the lock screen too. Videos stay where they are. */
@Composable
private fun ResetSheet(state: GlassSheetState) {
    val vault = LocalContext.current.frameServices.vault
    val scope = rememberCoroutineScope()
    GlassSheet(state, title = stringResource(R.string.app_private_reset)) {
        var code by remember { mutableStateOf("") }
        var new by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        var wrong by remember { mutableStateOf(false) }
        LaunchedEffect(state.isOpen) {
            if (!state.isOpen) {
                code = ""
                wrong = false
            }
        }
        Text(
            text = stringResource(R.string.app_private_reset_hint),
            style = FrameType.Body,
            color = FrameColors.SecondaryText,
            modifier = Modifier.padding(horizontal = FrameSpacing.S12),
        )
        FrameTextField(
            value = code,
            onValueChange = {
                code = it
                wrong = false
            },
            placeholder = stringResource(R.string.app_private_code_field),
            autoCorrect = false,
            capitalization = KeyboardCapitalization.Characters,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Next,
        )
        NewPasswordFields { new = it }
        StepError(if (wrong) stringResource(R.string.app_private_wrong_code) else null)
        val ready = new
        WhitePill(
            text = stringResource(R.string.app_private_reset_confirm),
            onClick = {
                if (ready != null) {
                    busy = true
                    scope.launch {
                        val done = vault.resetWithRecoveryCode(code, ready)
                        wrong = !done
                        busy = false
                        if (done) state.close()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = ready != null && code.isNotBlank() && !busy,
        )
    }
}
