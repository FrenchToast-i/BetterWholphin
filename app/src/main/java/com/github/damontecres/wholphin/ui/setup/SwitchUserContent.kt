package com.github.damontecres.wholphin.ui.setup

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.services.SetupDestination
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.CircularProgress
import com.github.damontecres.wholphin.ui.components.EditTextBox
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.TextButton
import com.github.damontecres.wholphin.ui.dimAndBlur
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.serverSupportedMessage
import com.github.damontecres.wholphin.ui.theme.GlassType
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.coroutines.launch

@Composable
fun SwitchUserContent(
    server: JellyfinServer,
    modifier: Modifier = Modifier,
    viewModel: SwitchUserViewModel =
        hiltViewModel<SwitchUserViewModel, SwitchUserViewModel.Factory>(
            creationCallback = { it.create(server) },
        ),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        viewModel.init()
    }

    val state by viewModel.state.collectAsState()

    val currentUser by viewModel.serverRepository.currentUserFlow.collectAsState(null)
    var showAddUser by remember { mutableStateOf(false) }
    var addUser by remember(server) { mutableStateOf<JellyfinUser?>(null) }
    var username by remember(addUser) { mutableStateOf(addUser?.name ?: "") }

    fun showAddUserDialog(user: JellyfinUser?) {
        addUser = user
        showAddUser = true
    }

    fun hideAddUserDialog() {
        addUser = null
        showAddUser = false
    }

    fun trySwitchUser(user: JellyfinUser) {
        val result = viewModel.trySwitchUser(user)
        scope.launch {
            when (val r = result.await()) {
                is SwitchUserResult.Error -> {
                    Toast.makeText(context, r.errorMessage, Toast.LENGTH_LONG).show()
                    if (r.showLogin) {
                        showAddUserDialog(user)
                    }
                }

                SwitchUserResult.Success -> {
                    // no-op, view model will navigate
                }
            }
        }
    }

    LaunchedEffect(state.switchUserState) {
        if (!showAddUser) {
            when (val s = state.switchUserState) {
                is LoadingState.Error -> {
                    val msg = s.message ?: s.exception?.localizedMessage
                    Toast.makeText(context, "Error: $msg", Toast.LENGTH_LONG).show()
                }

                else -> {}
            }
        }
    }
    var switchUserWithPin by remember { mutableStateOf<JellyfinUser?>(null) }

    when (val st = state.loading) {
        is LoadingState.Error -> {
            ErrorMessage(st, modifier)
        }

        LoadingState.Loading,
        LoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        LoadingState.Success -> {
            Box(
                modifier = modifier.dimAndBlur(showAddUser || switchUserWithPin != null),
            ) {
                // One layout, in every theme. This screen used to keep the classic centred list
                // behind a `LocalLiquidGlass` branch, and because that local is only known once
                // preferences load, a cold start drew the old "Select User" column for a frame or
                // two before the new one replaced it. A login screen that flickers through its
                // previous design is a bug even when the design that lands is right. The left
                // column is the question, the right pane is the answer — profiles as rows, not a
                // horizontal strip the household has to scroll through.
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier =
                            Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 72.dp)
                                .width(470.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.who_is_watching),
                            style = GlassType.hero,
                            color = Color.White,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(6.dp)
                                        .background(Color.White.copy(alpha = 0.55f), CircleShape),
                            )
                            Text(
                                text = server.name ?: server.url,
                                style = GlassType.heroSub,
                                color = Color.White.copy(alpha = 0.66f),
                            )
                        }
                        Text(
                            text = stringResource(R.string.profile_switch_hint),
                            style = GlassType.settingsSummary.copy(lineHeight = 20.sp),
                            color = Color.White.copy(alpha = 0.46f),
                            modifier = Modifier.padding(top = 8.dp).width(390.dp),
                        )
                    }
                    UserList(
                        users = state.users,
                        currentUser = currentUser,
                        onSwitchUser = { user ->
                            if (user.accessToken == null || user.requireLogin) {
                                showAddUserDialog(user)
                            } else if (user.hasPin) {
                                switchUserWithPin = user
                            } else {
                                trySwitchUser(user)
                            }
                        },
                        onAddUser = {
                            showAddUserDialog(null)
                        },
                        onRemoveUser = { user ->
                            viewModel.removeUser(user)
                        },
                        onSwitchServer = {
                            viewModel.setupNavigationManager.navigateTo(
                                SetupDestination.ServerList,
                            )
                        },
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 84.dp, end = 60.dp),
                )
                val resources = LocalResources.current
                val message =
                    remember(resources, state.serverVersionSupported) {
                        resources.serverSupportedMessage(
                            state.serverVersionSupported,
                            state.serverVersion,
                        )
                    }
                SwitchErrorMessage(message)
            }
        }
    }

    if (showAddUser) {
        var useQuickConnect by remember { mutableStateOf(state.quickConnectEnabled) }
        LaunchedEffect(Unit) {
            viewModel.clearSwitchUserState()
            viewModel.resetAttempts()
            if (useQuickConnect) {
                viewModel.initiateQuickConnect(server, addUser)
            }
        }
        BasicDialog(
            onDismissRequest = {
                viewModel.cancelQuickConnect()
                hideAddUserDialog()
            },
            properties =
                DialogProperties(
                    usePlatformDefaultWidth = false,
                ),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .focusGroup()
                        .padding(16.dp)
                        .fillMaxWidth(.4f),
            ) {
                if (useQuickConnect) {
                    if (state.quickConnectStatus == null && state.switchUserState !is LoadingState.Error) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier =
                                Modifier
                                    .height(32.dp)
                                    .align(Alignment.CenterHorizontally),
                        ) {
                            CircularProgress(Modifier.size(20.dp))
                            Text(
                                text = "Waiting for Quick Connect code...",
                                style = MaterialTheme.typography.titleMedium,
                                color = LocalContentColor.current,
                                textAlign = TextAlign.Center,
                                modifier = Modifier,
                            )
                        }
                    } else if (state.quickConnectStatus != null) {
                        Text(
                            text = "Use Quick Connect on your device to authenticate to ${server.name ?: server.url}",
                            style = MaterialTheme.typography.titleMedium,
                            color = LocalContentColor.current,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = state.quickConnectStatus?.code ?: "Failed to get code",
                            style = MaterialTheme.typography.displayMedium,
                            color = LocalContentColor.current,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                    UserStateError(state.switchUserState)
                    TextButton(
                        stringRes = R.string.username_or_password,
                        onClick = {
                            viewModel.cancelQuickConnect()
                            viewModel.clearSwitchUserState()
                            useQuickConnect = false
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                } else {
                    var password by remember { mutableStateOf("") }
                    val onSubmit = {
                        viewModel.login(
                            server,
                            addUser,
                            username,
                            password,
                        )
                    }
                    val focusRequester = remember { FocusRequester() }
                    val passwordFocusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) {
                        if (username.isBlank()) {
                            focusRequester.tryRequestFocus()
                        } else {
                            passwordFocusRequester.tryRequestFocus()
                        }
                    }
                    // The first line of the dialog is its title, at the sheet's scale. It used
                    // to open with one long sentence of instructions in the body size, which
                    // is what made the login sheet read as a leftover from the old setup flow.
                    Text(
                        text = stringResource(R.string.login),
                        style =
                            if (LocalLiquidGlass.current) {
                                GlassType.sectionTitle
                            } else {
                                MaterialTheme.typography.headlineSmall
                            },
                        color = LocalContentColor.current,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Sign in to ${server.name ?: server.url}",
                        style = MaterialTheme.typography.titleMedium,
                        color = LocalContentColor.current.copy(alpha = 0.72f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    UserStateError(state.switchUserState)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(
                            text = stringResource(R.string.username),
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        EditTextBox(
                            value = username,
                            onValueChange = { username = it },
                            keyboardOptions =
                                KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None,
                                    autoCorrectEnabled = false,
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.Next,
                                ),
                            keyboardActions =
                                KeyboardActions(
                                    onNext = {
                                        passwordFocusRequester.tryRequestFocus()
                                    },
                                ),
                            //                                onKeyboardAction = {
//                                    passwordFocusRequester.tryRequestFocus()
//                                },
                            isInputValid = { state.switchUserState !is LoadingState.Error },
                            modifier = Modifier.focusRequester(focusRequester),
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(
                            text = stringResource(R.string.password),
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        LaunchedEffect(password) {
                            viewModel.clearSwitchUserState()
                        }
                        EditTextBox(
                            value = password,
                            onValueChange = { password = it },
                            keyboardOptions =
                                KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None,
                                    autoCorrectEnabled = false,
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Go,
                                ),
                            keyboardActions =
                                KeyboardActions(
                                    onGo = { onSubmit.invoke() },
                                ),
                            isInputValid = { state.switchUserState !is LoadingState.Error },
                            modifier = Modifier.focusRequester(passwordFocusRequester),
                        )
                    }
                    TextButton(
                        stringRes = R.string.login,
                        onClick = { onSubmit.invoke() },
                        enabled = username.isNotNullOrBlank(),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                if (state.loginAttempts > 2) {
                    Text(
                        text = "Trouble logging in?",
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    TextButton(
                        stringRes = R.string.show_debug_info,
                        onClick = {
                            viewModel.navigationManager.navigateTo(Destination.Debug)
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
    }
    switchUserWithPin?.let { user ->
        PinEntryDialog(
            onDismissRequest = { switchUserWithPin = null },
            onClickServerAuth = {
                showAddUserDialog(user)
                switchUserWithPin = null
            },
            onTextChange = {
                if (it == user.pin) {
                    trySwitchUser(user)
                }
            },
        )
    }
}

@Composable
private fun UserStateError(
    userState: LoadingState,
    modifier: Modifier = Modifier,
) {
    when (val s = userState) {
        is LoadingState.Error -> {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = modifier,
            ) {
                s.message?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (s.exception != null) {
                    s.exception.localizedMessage?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    s.exception.cause?.localizedMessage?.let {
                        Text(
                            text = "Cause: $it",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        else -> {}
    }
}
