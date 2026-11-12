package com.github.damontecres.wholphin.ui.preferences

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import coil3.SingletonImageLoader
import coil3.imageLoader
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.AppSwitchPreference
import com.github.damontecres.wholphin.preferences.ExoPlayerPreferences
import com.github.damontecres.wholphin.preferences.ExperimentalPreference
import com.github.damontecres.wholphin.preferences.HdrFormatPreferences
import com.github.damontecres.wholphin.preferences.MpvPreferences
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.preferences.ScreensaverPreference
import com.github.damontecres.wholphin.preferences.SkipSegmentPreferences
import com.github.damontecres.wholphin.preferences.advancedPreferences
import com.github.damontecres.wholphin.preferences.basicPreferences
import com.github.damontecres.wholphin.preferences.experimentalPreferences
import com.github.damontecres.wholphin.preferences.lazyListWrapScrolling
import com.github.damontecres.wholphin.preferences.screensaverPreferences
import com.github.damontecres.wholphin.preferences.updatePlaybackPreferences
import com.github.damontecres.wholphin.services.Release
import com.github.damontecres.wholphin.services.SeerrConnectionStatus
import com.github.damontecres.wholphin.services.UpdateChecker
import com.github.damontecres.wholphin.ui.components.ConfirmDialog
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.ScrollableDialog
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.indexOfFirstOrNull
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.playOnClickSound
import com.github.damontecres.wholphin.ui.playSoundOnFocus
import com.github.damontecres.wholphin.ui.preferences.subtitle.SubtitleSettings
import com.github.damontecres.wholphin.ui.serverSupportedMessage
import com.github.damontecres.wholphin.ui.setup.ReleaseNotes
import com.github.damontecres.wholphin.ui.setup.UpdateViewModel
import com.github.damontecres.wholphin.ui.setup.seerr.SeerrDialog
import com.github.damontecres.wholphin.ui.setup.seerr.SeerrDialogMode
import com.github.damontecres.wholphin.ui.setup.seerr.SwitchSeerrViewModel
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.theme.GlassTokens
import com.github.damontecres.wholphin.ui.theme.GlassType
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.github.damontecres.wholphin.ui.theme.glassSurface
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale

@Composable
fun PreferencesContent(
    initialPreferences: AppPreferences,
    preferenceScreenOption: PreferenceScreenOption,
    modifier: Modifier = Modifier,
    viewModel: PreferencesViewModel = hiltViewModel(),
    updateVM: UpdateViewModel = hiltViewModel(),
    seerrVm: SwitchSeerrViewModel = hiltViewModel(),
    onFocus: (Int, Int) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val firstFocusRequester = remember { FocusRequester() }
    val lastFocusRequester = remember { FocusRequester() }

    var focusedIndex by rememberSaveable { mutableStateOf(Pair(0, 0)) }
    val state = rememberLazyListState()
    var preferences by remember { mutableStateOf(initialPreferences) }
    val currentUser by viewModel.currentUser.collectAsState()
    val accountPreferences by viewModel.accountPreferences.collectAsState()
    // The settings list is drawn from one preference object, but two rows come from the
    // account overlay. This resolved copy is what the conditional groups and those two rows
    // read, so turning Liquid Glass on for this profile reveals the top-bar controls even when
    // the device switch is off.
    val effectivePreferences =
        remember(preferences, accountPreferences) {
            preferences.toBuilder().apply {
                val accountInterface =
                    preferences.interfacePreferences.toBuilder().apply {
                        accountPreferences.liquidGlass?.let { liquidGlass = it }
                        accountPreferences.tmdbApiKey?.let { tmdbApiKey = it }
                    }.build()
                interfacePreferences = accountInterface
            }.build()
        }
    var showPinFlow by remember { mutableStateOf(false) }
    var showVersionDialog by remember { mutableStateOf(false) }
    val players by viewModel.externalPlayers.collectAsState()
    val serverSupported by viewModel.serverSupported.collectAsState()
    val serverVersionMessage =
        remember(resources, serverSupported) {
            resources.serverSupportedMessage(serverSupported.second, serverSupported.first)
        }

    var cacheUsage by remember { mutableStateOf(CacheUsage(0, 0, 0)) }
    val seerrConnection by viewModel.seerrConnection.collectAsState()
    var seerrDialogMode by remember { mutableStateOf<SeerrDialogMode>(SeerrDialogMode.None) }
    var showQuickConnectDialog by remember { mutableStateOf(false) }
    var showLocaleChoiceDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.preferenceDataStore.data.collect {
            preferences = it
        }
    }
    var updateCache by remember { mutableStateOf(false) }
    LaunchedEffect(updateCache) {
        val imageUsedMemory = context.imageLoader.memoryCache?.size ?: 0L
        val imageMaxMemory = context.imageLoader.memoryCache?.maxSize ?: 0L
        val imageDisk = context.imageLoader.diskCache?.size ?: 0L
        cacheUsage = CacheUsage(imageUsedMemory, imageMaxMemory, imageDisk)
        updateCache = false
    }

    val updateState by updateVM.state.collectAsState()
    val release = updateState.release
    LaunchedEffect(preferences.updateUrl, preferences.autoCheckForUpdates) {
        if (UpdateChecker.ACTIVE && preferences.autoCheckForUpdates) {
            updateVM.init()
        }
    }

    val movementSounds = false
    val installedVersion = updateVM.currentVersion
    val updateAvailable =
        remember(updateState.release) {
            updateState.release?.version?.isGreaterThan(installedVersion) ?: false
        }

    val prefList =
        when (preferenceScreenOption) {
            PreferenceScreenOption.BASIC -> basicPreferences
            PreferenceScreenOption.ADVANCED -> advancedPreferences
            PreferenceScreenOption.EXO_PLAYER -> ExoPlayerPreferences
            PreferenceScreenOption.MPV -> MpvPreferences
            PreferenceScreenOption.SCREENSAVER -> screensaverPreferences
            PreferenceScreenOption.SKIP_SEGMENTS -> SkipSegmentPreferences
            PreferenceScreenOption.EXPERIMENTAL -> experimentalPreferences
            PreferenceScreenOption.HDR_FORMATS -> HdrFormatPreferences
        }
    val screenTitle =
        when (preferenceScreenOption) {
            PreferenceScreenOption.BASIC -> R.string.settings
            PreferenceScreenOption.ADVANCED -> R.string.advanced_settings
            PreferenceScreenOption.EXO_PLAYER -> R.string.exoplayer_options
            PreferenceScreenOption.MPV -> R.string.mpv_options
            PreferenceScreenOption.SCREENSAVER -> R.string.screensaver_settings
            PreferenceScreenOption.SKIP_SEGMENTS -> R.string.skip_behavior
            PreferenceScreenOption.EXPERIMENTAL -> R.string.experimental_settings
            PreferenceScreenOption.HDR_FORMATS -> R.string.hdr_formats
        }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Forces the animated to trigger
        visible = true
    }

    LaunchedEffect(preferences.playbackPreferences.playerBackend) {
        if (preferences.playbackPreferences.playerBackend == PlayerBackend.MPV) {
            Timber.d("Checking for libmpv")
            try {
                System.loadLibrary("mpv")
                System.loadLibrary("player")
            } catch (ex: UnsatisfiedLinkError) {
                Timber.w(ex, "Could not load libmpv")
                showToast(context, "MPV is not supported on this device")
                viewModel.preferenceDataStore.updateData {
                    it.updatePlaybackPreferences { playerBackend = PlayerBackend.EXO_PLAYER }
                }
            }
        }
    }

    val showUpdate =
        UpdateChecker.ACTIVE &&
            preferenceScreenOption == PreferenceScreenOption.BASIC &&
            preferences.autoCheckForUpdates &&
            updateAvailable

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInHorizontally { it / 2 },
        exit = fadeOut() + slideOutHorizontally { it / 2 },
        modifier = modifier,
    ) {
        LaunchedEffect(Unit) {
            focusRequester.tryRequestFocus()
        }
        val glass = LocalLiquidGlass.current
        Column(
            // In glass mode the sheet itself is the surface, so painting a theme colour here
            // would cover it with an opaque slab.
            modifier =
                if (glass) {
                    Modifier
                } else {
                    Modifier.background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                },
        ) {
            Text(
                text = stringResource(screenTitle),
                style = if (glass) GlassType.pageTitle else MaterialTheme.typography.headlineSmall,
                color = if (glass) Color.White else MaterialTheme.colorScheme.onSurface,
                textAlign = if (glass) TextAlign.Start else TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = if (glass) 26.dp else 0.dp,
                            top = if (glass) 18.dp else 8.dp,
                            bottom = if (glass) 14.dp else 8.dp,
                        ),
            )
            LazyColumn(
                state = state,
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding =
                    if (glass) {
                        PaddingValues(start = 18.dp, end = 22.dp, top = 4.dp, bottom = 24.dp)
                    } else {
                        PaddingValues(16.dp)
                    },
                // Weight, not fillMaxSize: the list is the *remainder* of the sheet under the
                // title. `fillMaxSize` measured it against the whole column, so the sheet's last
                // rows were painted past the pane's rounded bottom and cut off by the screen
                // edge — a settings page that looks like it is missing its final switch.
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                serverVersionMessage?.let { message ->
                    item {
                        // A server warning is not a setting. It gets its own tinted card above
                        // the list instead of sitting in the group as though it were a row the
                        // viewer could open.
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                                    .ifElse(
                                        glass,
                                        Modifier
                                            .clip(RoundedCornerShape(GlassTokens.rowRadius))
                                            .background(Color(0xFF3A2410).copy(alpha = 0.72f)),
                                    ),
                        ) {
                            Text(
                                text = message,
                                style =
                                    if (glass) {
                                        GlassType.settingsSummary.copy(color = Color(0xFFFFC46B))
                                    } else {
                                        MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error)
                                    },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
                if (showUpdate) {
                    item {
                        val interactionSource = remember { MutableInteractionSource() }
                        val focused = interactionSource.collectIsFocusedAsState().value
                        val updateFocusRequester = remember { FocusRequester() }
                        LaunchedEffect(Unit) {
                            if (focusedIndex.first == 0 && focusedIndex.second == 0) {
                                // Only re-focus if the user hasn't moved
                                updateFocusRequester.tryRequestFocus()
                            }
                        }
                        ClickPreference(
                            title = stringResource(R.string.install_update),
                            onClick = {
                                if (movementSounds) playOnClickSound(context)
                                viewModel.navigationManager.navigateTo(Destination.UpdateApp)
                            },
                            summary = release?.version?.toString(),
                            interactionSource = interactionSource,
                            modifier =
                                Modifier
                                    .glassPreferenceRow(first = true, last = true)
                                    .focusRequester(updateFocusRequester)
                                    .focusRequester(firstFocusRequester)
                                    .playSoundOnFocus(movementSounds)
                                    .lazyListWrapScrolling(
                                        state = state,
                                        focused = focused,
                                        isFirst = true,
                                        isLast = false,
                                        firstFocusRequester = firstFocusRequester,
                                        lastFocusRequester = lastFocusRequester,
                                    ),
                        )
                    }
                }

                prefList.forEachIndexed { groupIndex, group ->
                    val headerContent: @Composable () -> Unit = {
                        Text(
                            text = stringResource(group.title).uppercase(),
                            style =
                                if (glass) GlassType.sectionLabel else MaterialTheme.typography.titleMedium,
                            color = if (glass) Color.White.copy(alpha = 0.54f) else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Start,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .ifElse(
                                        glass,
                                        Modifier.background(
                                            Brush.verticalGradient(
                                                0f to Color.Black.copy(alpha = 0.55f),
                                                0.72f to Color.Black.copy(alpha = 0.28f),
                                                1f to Color.Transparent,
                                            ),
                                        ),
                                    ).padding(
                                        start = if (glass) GlassTokens.rowInset else 0.dp,
                                        top = if (glass) 26.dp else 8.dp,
                                        bottom = if (glass) 7.dp else 4.dp,
                                    ),
                        )
                    }
                    // Deliberately not a sticky header on glass. A pinned group label is pushed
                    // up over the last row of its own group as the next group arrives, and because
                    // the sheet's header wash is translucent, the row's title stayed readable
                    // underneath: two labels drawn on top of each other at every group boundary.
                    // The group name scrolls with its rows instead; the sheet title stays pinned.
                    item(key = "group_$groupIndex") { headerContent() }
                    val groupPreferences =
                        group.preferences +
                            group.conditionalPreferences
                                .filter { it.condition.invoke(effectivePreferences) }
                                .map { it.preferences }
                                .flatten()
                    groupPreferences.forEachIndexed { prefIndex, pref ->
                        pref as AppPreference<AppPreferences, Any>
                        val isFirst = groupIndex == 0 && prefIndex == 0 && !showUpdate
                        val isLast =
                            groupIndex == prefList.lastIndex && prefIndex == groupPreferences.lastIndex
                        item(key = "pref_${groupIndex}_$prefIndex") {
                            val interactionSource = remember { MutableInteractionSource() }
                            val focused = interactionSource.collectIsFocusedAsState().value
                            val focusModifier =
                                Modifier
                                    .glassPreferenceRow(
                                        first = prefIndex == 0,
                                        last = prefIndex == groupPreferences.lastIndex,
                                    ).ifElse(
                                        groupIndex == focusedIndex.first && prefIndex == focusedIndex.second,
                                        Modifier.focusRequester(focusRequester),
                                    ).lazyListWrapScrolling(
                                        state,
                                        focused,
                                        isFirst,
                                        isLast,
                                        firstFocusRequester,
                                        lastFocusRequester,
                                    )
                            LaunchedEffect(focused) {
                                if (focused) {
                                    focusedIndex = Pair(groupIndex, prefIndex)
                                    if (movementSounds) playOnClickSound(context)
                                    onFocus.invoke(groupIndex, prefIndex)
                                }
                            }
                            when (pref) {
                                AppPreference.InstalledVersion -> {
                                    ClickPreference(
                                        title = stringResource(R.string.installed_version),
                                        onClick = {
                                            showVersionDialog = true
                                        },
                                        onLongClick = {
                                            viewModel.navigationManager.navigateTo(Destination.Debug)
                                        },
                                        summary = installedVersion.toString(),
                                        interactionSource = interactionSource,
                                        modifier = focusModifier,
                                    )
                                }

                                AppPreference.Update -> {
                                    ClickPreference(
                                        title =
                                            if (release != null && updateAvailable) {
                                                stringResource(R.string.install_update)
                                            } else if (!preferences.autoCheckForUpdates && release == null) {
                                                stringResource(R.string.check_for_updates)
                                            } else {
                                                stringResource(R.string.no_update_available)
                                            },
                                        onClick = {
                                            if (movementSounds) playOnClickSound(context)
                                            if (release != null && updateAvailable) {
                                                release?.let {
                                                    viewModel.navigationManager.navigateTo(
                                                        Destination.UpdateApp,
                                                    )
                                                }
                                            } else {
                                                updateVM.init()
                                            }
                                        },
                                        onLongClick = {
                                            if (movementSounds) playOnClickSound(context)
                                            viewModel.navigationManager.navigateTo(Destination.UpdateApp)
                                        },
                                        summary =
                                            if (updateAvailable) {
                                                release?.version?.toString()
                                            } else {
                                                null
                                            },
                                        interactionSource = interactionSource,
                                        modifier = focusModifier,
                                    )
                                }

                                AppPreference.ClearImageCache -> {
                                    val summary =
                                        remember(cacheUsage) {
                                            cacheUsage.let {
                                                val diskMB =
                                                    it.imageDiskUsed / AppPreference.MEGA_BIT
                                                val memoryUsedMB =
                                                    it.imageMemoryUsed / AppPreference.MEGA_BIT
                                                val memoryMaxMB =
                                                    it.imageMemoryMax / AppPreference.MEGA_BIT
                                                "Disk: ${diskMB}mb, Memory: ${memoryUsedMB}mb/${memoryMaxMB}mb"
                                            }
                                        }
                                    ClickPreference(
                                        title = stringResource(pref.title),
                                        onClick = {
                                            SingletonImageLoader.get(context).let {
                                                it.memoryCache?.clear()
                                                it.diskCache?.clear()
                                                updateCache = true
                                            }
                                        },
                                        modifier = focusModifier,
                                        summary = summary,
                                        onLongClick = {},
                                        interactionSource = interactionSource,
                                    )
                                }

                                AppPreference.UserPinnedNavDrawerItems -> {
                                    NavDrawerPreference(
                                        title = stringResource(pref.title),
                                        summary = pref.summary(context, null),
                                        modifier = focusModifier,
                                        interactionSource = interactionSource,
                                    )
                                }

                                AppPreference.SendAppLogs -> {
                                    ClickPreference(
                                        title = stringResource(pref.title),
                                        onClick = {
                                            viewModel.sendAppLogs()
                                        },
                                        modifier = focusModifier,
                                        summary = pref.summary(context, null),
                                        onLongClick = {},
                                        interactionSource = interactionSource,
                                    )
                                }

                                SubtitleSettings.Reset -> {
                                    ClickPreference(
                                        title = stringResource(pref.title),
                                        onClick = {
                                            viewModel.resetSubtitleSettings()
                                        },
                                        modifier = focusModifier,
                                        summary = pref.summary(context, null),
                                        onLongClick = {},
                                        interactionSource = interactionSource,
                                    )
                                }

                                AppPreference.ProtectProfilePreference -> {
                                    val summary =
                                        when {
                                            currentUser?.requireLogin == true -> R.string.require_login
                                            currentUser?.hasPin == true -> R.string.require_pin_code
                                            else -> R.string.none
                                        }
                                    ChoicePreference(
                                        title = stringResource(pref.title),
                                        summary = stringResource(summary),
                                        interactionSource = interactionSource,
                                        possibleValues =
                                            listOf(
                                                stringResource(R.string.none),
                                                stringResource(R.string.require_pin_code),
                                                stringResource(R.string.require_login),
                                            ),
                                        selectedIndex =
                                            when {
                                                currentUser?.requireLogin == true -> ProfileProtection.LOGIN
                                                currentUser?.hasPin == true -> ProfileProtection.PIN
                                                else -> ProfileProtection.NONE
                                            }.ordinal,
                                        onValueChange = {
                                            currentUser?.let { user ->
                                                when (ProfileProtection.entries[it]) {
                                                    ProfileProtection.NONE -> {
                                                        viewModel.removeLoginAndPin(user)
                                                    }

                                                    ProfileProtection.PIN -> {
                                                        showPinFlow = true
                                                    }

                                                    ProfileProtection.LOGIN -> {
                                                        viewModel.setRequireLogin(user)
                                                    }
                                                }
                                            }
                                        },
                                        modifier = focusModifier,
                                    )
                                }

                                AppPreference.SeerrIntegration -> {
                                    ClickPreference(
                                        title = stringResource(pref.title),
                                        onClick = {
                                            seerrDialogMode =
                                                when (val conn = seerrConnection.connection) {
                                                    is SeerrConnectionStatus.Error -> {
                                                        SeerrDialogMode.Error(
                                                            conn.server,
                                                            conn.user,
                                                            conn.ex,
                                                        )
                                                    }

                                                    SeerrConnectionStatus.NotConfigured -> {
                                                        SeerrDialogMode.Add
                                                    }

                                                    is SeerrConnectionStatus.Success -> {
                                                        SeerrDialogMode.Remove(
                                                            conn.current,
                                                        )
                                                    }
                                                }
                                        },
                                        modifier = focusModifier,
                                        summary =
                                            when (seerrConnection.connection) {
                                                is SeerrConnectionStatus.Error -> {
                                                    stringResource(R.string.voice_error_server)
                                                }

                                                SeerrConnectionStatus.NotConfigured -> {
                                                    stringResource(R.string.add_server)
                                                }

                                                is SeerrConnectionStatus.Success -> {
                                                    stringResource(R.string.enabled)
                                                }
                                            },
                                        onLongClick = {},
                                        interactionSource = interactionSource,
                                    )
                                }

                                AppPreference.QuickConnect -> {
                                    ClickPreference(
                                        title = stringResource(pref.title),
                                        onClick = {
                                            if (currentUser != null) {
                                                viewModel.resetQuickConnectStatus()
                                                showQuickConnectDialog = true
                                            }
                                        },
                                        modifier = focusModifier,
                                        summary = pref.summary(context, null),
                                        onLongClick = {},
                                        interactionSource = interactionSource,
                                    )
                                }

                                ScreensaverPreference.Start -> {
                                    ClickPreference(
                                        title = stringResource(pref.title),
                                        onClick = {
                                            viewModel.screensaverService.start()
                                        },
                                        modifier = focusModifier,
                                        summary = pref.summary(context, null),
                                        onLongClick = {},
                                        interactionSource = interactionSource,
                                    )
                                }

                                AppPreference.ExternalPlayerApp -> {
                                    val value = pref.getter.invoke(preferences).toString()
                                    val selectedIndex =
                                        remember(value, players) {
                                            players.indexOfFirstOrNull { it.identifier == value }
                                        } ?: 0
                                    ChoicePreference(
                                        title = stringResource(pref.title),
                                        summary = players[selectedIndex].name,
                                        interactionSource = interactionSource,
                                        possibleValues = players,
                                        selectedIndex = selectedIndex,
                                        onValueChange = { index ->
                                            scope.launch(ExceptionHandler()) {
                                                val newValue =
                                                    players.getOrNull(index)?.identifier ?: ""
                                                preferences =
                                                    viewModel.preferenceDataStore.updateData { prefs ->
                                                        pref.setter.invoke(prefs, newValue)
                                                    }
                                            }
                                        },
                                        valueDisplay = { index, item ->
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                if (item.icon != null) {
                                                    Image(
                                                        bitmap = item.icon,
                                                        contentDescription = null,
                                                        modifier = Modifier.width(40.dp),
                                                    )
                                                }
                                                Text(item.name)
                                            }
                                        },
                                        modifier = focusModifier,
                                    )
                                }

                                AppPreference.UserInterfaceLanguage -> {
                                    val locale =
                                        remember(currentUser?.uiLanguage) {
                                            currentUser?.uiLanguage?.let { Locale.forLanguageTag(it) }
                                                ?: Locale.getDefault()
                                        }
                                    ClickPreference(
                                        title = stringResource(pref.title),
                                        onClick = {
                                            showLocaleChoiceDialog = true
                                        },
                                        modifier = focusModifier,
                                        summary = locale.getDisplayName(locale),
                                        onLongClick = null,
                                        interactionSource = interactionSource,
                                    )
                                }

                                ExperimentalPreference.Enable -> {
                                    var showConfirm by remember { mutableStateOf(false) }
                                    pref as AppSwitchPreference<AppPreferences>
                                    val value = pref.getter.invoke(preferences)
                                    SwitchPreference(
                                        title = stringResource(pref.title),
                                        value = value,
                                        onClick = {
                                            if (value) {
                                                scope.launch(ExceptionHandler()) {
                                                    preferences =
                                                        viewModel.preferenceDataStore.updateData { prefs ->
                                                            pref.setter.invoke(prefs, false)
                                                        }
                                                }
                                            } else {
                                                showConfirm = true
                                            }
                                        },
                                        summaryOn = pref.summaryOn?.let { stringResource(pref.summaryOn) },
                                        summaryOff = pref.summaryOff?.let { stringResource(pref.summaryOff) },
                                    )
                                    if (showConfirm) {
                                        ConfirmDialog(
                                            title = stringResource(R.string.confirm_enable_experimental_title),
                                            body = stringResource(R.string.confirm_enable_experimental_body),
                                            onCancel = { showConfirm = false },
                                            onConfirm = {
                                                showConfirm = false
                                                scope.launch(ExceptionHandler()) {
                                                    preferences =
                                                        viewModel.preferenceDataStore.updateData { prefs ->
                                                            pref.setter.invoke(prefs, true)
                                                        }
                                                }
                                            },
                                        )
                                    }
                                }

                                AppPreference.LiquidGlass -> {
                                    // Per account: the device file holds the default, the
                                    // account overlay holds this profile's choice.
                                    val value = effectivePreferences.interfacePreferences.liquidGlass
                                    SwitchPreference(
                                        title = stringResource(R.string.liquid_glass_mode),
                                        value = value,
                                        onClick = { viewModel.setLiquidGlass(!value) },
                                        summaryOn = stringResource(R.string.liquid_glass_mode_summary),
                                        summaryOff = stringResource(R.string.liquid_glass_mode_summary),
                                        modifier = focusModifier,
                                        interactionSource = interactionSource,
                                    )
                                }

                                AppPreference.SeerrEnabled -> {
                                    // Per account, like the glass switch: a request identity is a
                                    // person's, so one profile can keep Discover while another
                                    // does not see the door at all.
                                    val value = accountPreferences.seerrEnabled
                                    SwitchPreference(
                                        title = stringResource(R.string.seerr_enabled),
                                        value = value,
                                        onClick = { viewModel.setSeerrEnabled(!value) },
                                        summaryOn = stringResource(R.string.seerr_enabled_summary),
                                        summaryOff = stringResource(R.string.seerr_enabled_summary),
                                        modifier = focusModifier,
                                        interactionSource = interactionSource,
                                    )
                                }

                                AppPreference.TmdbApiKey -> {
                                    var showKeyDialog by remember { mutableStateOf(false) }
                                    ClickPreference(
                                        title = stringResource(R.string.tmdb_api_key),
                                        onClick = { showKeyDialog = true },
                                        summary = stringResource(R.string.tmdb_api_key_summary),
                                        interactionSource = interactionSource,
                                        modifier = focusModifier,
                                    )
                                    if (showKeyDialog) {
                                        StringInputDialog(
                                            input =
                                                StringInput(
                                                    title = stringResource(R.string.tmdb_api_key),
                                                    value = effectivePreferences.interfacePreferences.tmdbApiKey,
                                                    keyboardOptions =
                                                        KeyboardOptions(
                                                            autoCorrectEnabled = false,
                                                            imeAction = ImeAction.Done,
                                                        ),
                                                    onSubmit = {
                                                        viewModel.setTmdbApiKey(it)
                                                        showKeyDialog = false
                                                    },
                                                ),
                                            onSave = {
                                                viewModel.setTmdbApiKey(it)
                                                showKeyDialog = false
                                            },
                                            onDismissRequest = { showKeyDialog = false },
                                            elevation = 3.dp,
                                        )
                                    }
                                }

                                else -> {
                                    val value = pref.getter.invoke(effectivePreferences)
                                    ComposablePreference(
                                        preference = pref,
                                        value = value,
                                        onNavigate = viewModel.navigationManager::navigateTo,
                                        onValueChange = { newValue ->
                                            val validation = pref.validate(preferences, newValue)
                                            when (validation) {
                                                is PreferenceValidation.Invalid -> {
                                                    // TODO?
                                                    Toast
                                                        .makeText(
                                                            context,
                                                            validation.message,
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                }

                                                PreferenceValidation.Valid -> {
                                                    scope.launch(ExceptionHandler()) {
                                                        preferences =
                                                            viewModel.preferenceDataStore.updateData { prefs ->
                                                                pref.setter(prefs, newValue)
                                                            }
                                                    }
                                                }
                                            }
                                        },
                                        interactionSource = interactionSource,
                                        modifier = focusModifier,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showPinFlow && currentUser != null) {
            currentUser?.let { user ->
                SetPinFlow(
                    currentPin = user.pin,
                    onAddPin = {
                        viewModel.setPin(user, it)
                        showPinFlow = false
                    },
                    onRemovePin = {
                        viewModel.setPin(user, null)
                        showPinFlow = false
                    },
                    onDismissRequest = { showPinFlow = false },
                )
            }
        }
    }

    SeerrDialog(
        seerrDialogMode = seerrDialogMode,
        seerrConnection = seerrConnection,
        onDismissRequest = { seerrDialogMode = SeerrDialogMode.None },
        seerrVm = seerrVm,
    )

    if (showQuickConnectDialog) {
        val quickConnectStatus by viewModel.quickConnectStatus.collectAsState(LoadingState.Pending)
        val successMessage = stringResource(R.string.quick_connect_success)

        LaunchedEffect(quickConnectStatus) {
            when (val status = quickConnectStatus) {
                LoadingState.Success -> {
                    Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
                    showQuickConnectDialog = false
                }

                is LoadingState.Error -> {
                    val errorMessage = status.message ?: "Authorization failed"
                    Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                }

                else -> {}
            }
        }

        QuickConnectDialog(
            onSubmit = { code ->
                viewModel.authorizeQuickConnect(code)
            },
            onDismissRequest = {
                viewModel.resetQuickConnectStatus()
                showQuickConnectDialog = false
            },
        )
    }
    if (showVersionDialog) {
        LaunchedEffect(Unit) {
            viewModel.fetchReleaseNotes()
        }
        val release by viewModel.releaseNotes.collectAsState()
        ScrollableDialog(
            onDismissRequest = { showVersionDialog = false },
        ) {
            item {
                when (val r = release) {
                    is DataLoadingState.Error -> {
                        ErrorMessage(message = "Error", exception = r.exception)
                    }

                    DataLoadingState.Pending,
                    DataLoadingState.Loading,
                    -> {
                        LoadingPage()
                    }

                    is DataLoadingState.Success<Release> -> {
                        ReleaseNotes(r.data)
                    }
                }
            }
        }
    }
    if (showLocaleChoiceDialog) {
        LocaleChoiceDialog(
            onDismissRequest = { showLocaleChoiceDialog = false },
        )
    }
}

@Composable
fun PreferencesPage(
    initialPreferences: AppPreferences,
    preferenceScreenOption: PreferenceScreenOption,
    modifier: Modifier = Modifier,
) {
    val liquidGlass = LocalLiquidGlass.current
    Box(
        modifier = if (liquidGlass) modifier else modifier.background(MaterialTheme.colorScheme.background),
    ) {
        when (preferenceScreenOption) {
            PreferenceScreenOption.BASIC,
            PreferenceScreenOption.ADVANCED,
            PreferenceScreenOption.EXO_PLAYER,
            PreferenceScreenOption.MPV,
            PreferenceScreenOption.SCREENSAVER,
            PreferenceScreenOption.SKIP_SEGMENTS,
            PreferenceScreenOption.EXPERIMENTAL,
            PreferenceScreenOption.HDR_FORMATS,
            -> {
                val pageModifier =
                    Modifier
                        // Glass settings are the one page that reads as a sheet rather than a
                        // stage: wide enough for a title, a summary and a switch on one line,
                        // centred so the artwork balances on both sides instead of being pushed
                        // into a narrow strip on the left.
                        .fillMaxWidth(if (liquidGlass) 0.66f else 0.4f)
                        .fillMaxHeight()
                        .align(if (liquidGlass) Alignment.Center else Alignment.TopEnd)
                if (liquidGlass) {
                    // A single pane behind the whole list, so scrolling settings feel like one
                    // sheet of glass rather than a stack of unrelated rows.
                    Box(
                        modifier =
                            pageModifier
                                // Air at the foot as well as the top: a pane that ends flush
                                // with the screen edge loses its bottom corners and reads as a
                                // page that scrolled instead of as a sheet.
                                .padding(top = 10.dp, bottom = 22.dp)
                                .glassSurface(
                                    shape = RoundedCornerShape(GlassTokens.panelRadius),
                                    // A settings sheet is a text surface, not a hero: it takes a
                                    // light, even smoke so the list holds over any artwork, while
                                    // the edges still refract. The lens is never turned off.
                                    tint = Color.Black.copy(alpha = 0.30f),
                                    scrim = 0.55f,
                                ),
                    )
                }
                PreferencesContent(
                    initialPreferences,
                    preferenceScreenOption,
                    if (liquidGlass) {
                        pageModifier.padding(top = 10.dp, bottom = 22.dp, start = 4.dp, end = 4.dp)
                    } else {
                        pageModifier
                    },
                )
            }
        }
    }
}

data class CacheUsage(
    val imageMemoryUsed: Long,
    val imageMemoryMax: Long,
    val imageDiskUsed: Long,
)
