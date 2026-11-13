package com.github.damontecres.wholphin.ui.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.Button
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.theme.GlassTokens
import com.github.damontecres.wholphin.ui.theme.GlassType
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.theme.glassSurface
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.ui.tryRequestFocus
import java.util.UUID

/**
 * Display a list of users plus option to add a new one or switch servers.
 *
 * Glass mode is a vertical sheet: profiles are rows beneath each other in a pane anchored to the
 * top-right of the setup screen, which is the layout a television viewer expects from a profile
 * picker and the one this fork asked for. The classic UI keeps the horizontal strip it has always
 * had, because that layout is also what the non-glass themes are shaped around.
 */
@Composable
fun UserList(
    users: List<JellyfinUserAndImage>,
    currentUser: JellyfinUser?,
    onSwitchUser: (JellyfinUser) -> Unit,
    onAddUser: () -> Unit,
    onRemoveUser: (JellyfinUser) -> Unit,
    onSwitchServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by remember { mutableStateOf<JellyfinUserAndImage?>(null) }

    if (LocalLiquidGlass.current) {
        GlassUserList(
            users = users,
            currentUser = currentUser,
            onSwitchUser = onSwitchUser,
            onAddUser = onAddUser,
            onLongClickUser = { showDeleteDialog = it },
            onSwitchServer = onSwitchServer,
            modifier = modifier,
        )
    } else {
        ClassicUserList(
            users = users,
            currentUser = currentUser,
            onSwitchUser = onSwitchUser,
            onAddUser = onAddUser,
            onLongClickUser = { showDeleteDialog = it },
            onSwitchServer = onSwitchServer,
            modifier = modifier,
        )
    }

    showDeleteDialog?.let { user ->
        DialogPopup(
            showDialog = true,
            title = user.user.name ?: user.user.id.toServerString(),
            dialogItems =
                buildList {
                    add(
                        DialogItem(
                            stringResource(R.string.switch_user),
                            R.string.fa_arrow_left_arrow_right,
                        ) {
                            onSwitchUser.invoke(user.user)
                        },
                    )
                    if (user.known) {
                        add(
                            DialogItem(
                                stringResource(R.string.delete),
                                Icons.Default.Delete,
                                Color.Red.copy(alpha = .8f),
                            ) {
                                onRemoveUser.invoke(user.user)
                            },
                        )
                    }
                },
            onDismissRequest = { showDeleteDialog = null },
            dismissOnClick = true,
            waitToLoad = true,
            properties = DialogProperties(),
        )
    }
}

/**
 * The glass profile sheet.
 *
 * One pane, rows inside it, no glass on the rows themselves: the material is the pane, and a
 * capsule per profile would make five pieces of chrome stacked in a column. Focus is a quiet
 * lift of white plus a hairline rim — the same signal as every other list in the app.
 */
@Composable
private fun GlassUserList(
    users: List<JellyfinUserAndImage>,
    currentUser: JellyfinUser?,
    onSwitchUser: (JellyfinUser) -> Unit,
    onAddUser: () -> Unit,
    onLongClickUser: (JellyfinUserAndImage) -> Unit,
    onSwitchServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val firstFocusRequester = remember { FocusRequester() }
    LaunchedEffect(users) {
        if (users.isNotEmpty()) firstFocusRequester.tryRequestFocus()
    }

    Column(
        modifier =
            modifier
                .width(392.dp)
                .glassSurface(
                    shape = GlassTokens.panelShape,
                    tint = GlassTokens.sheetTint,
                    scrim = GlassTokens.SHEET_SCRIM,
                    elevation = GlassTokens.dialogElevation,
                ).padding(vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.profiles).uppercase(),
            style = GlassType.sectionLabel,
            color = Color.White.copy(alpha = 0.52f),
            modifier = Modifier.padding(start = 24.dp, top = 4.dp, bottom = 8.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    // Four profiles and the Add row (306dp of content) fit a 540dp television
                    // with air at the foot; a fifth scrolls instead of pushing the server row
                    // off the sheet. Do not lower this to the exact content height: the list
                    // then clips the Add row by the few dp that `heightIn` rounds off.
                    .heightIn(max = 320.dp)
                    .focusRestorer(firstFocusRequester)
                    .focusRequester(focusRequester),
        ) {
            itemsIndexed(users, key = { _, user -> user.user.id }) { index, user ->
                GlassProfileRow(
                    user = user,
                    isCurrentUser = user.user.id == currentUser?.id,
                    onClick = { onSwitchUser.invoke(user.user) },
                    onLongClick = { onLongClickUser.invoke(user) },
                    modifier = if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier,
                )
            }
            item(key = "add_profile") {
                GlassAddProfileRow(onClick = onAddUser)
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .padding(horizontal = 20.dp)
                    .background(Color.White.copy(alpha = 0.10f)),
        )
        GlassServerRow(
            onClick = onSwitchServer,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun GlassProfileRow(
    user: JellyfinUserAndImage,
    isCurrentUser: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(15.dp)
    val userColor = rememberIdColor(user.user.id)
    val subtitle =
        when {
            isCurrentUser -> stringResource(R.string.profile_watching_now)
            user.user.hasPin -> stringResource(R.string.profile_pin_required)
            user.user.accessToken == null || user.user.requireLogin ->
                stringResource(R.string.profile_sign_in_required)
            else -> stringResource(R.string.profile_tap_to_switch)
        }

    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = Color.White.copy(alpha = 0.92f),
                focusedContainerColor = Color.White.copy(alpha = 0.15f),
                focusedContentColor = Color.White,
            ),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder =
                    Border(
                        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.55f)),
                        shape = shape,
                    ),
            ),
        // The sheet keeps its silhouette; focus is a wash and a rim, not a growing card.
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f, pressedScale = 0.985f),
        modifier = modifier.fillMaxWidth().height(60.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(userColor.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                UserIconCardImage(
                    id = user.user.id,
                    name = user.user.name,
                    imageUrl = user.imageUrl,
                    textColor = Color.White,
                    letterSize = 19.sp,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = user.user.name ?: user.user.id.toServerString(),
                    style = GlassType.settingsTitle.copy(fontSize = 18.sp, lineHeight = 22.sp),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = GlassType.settingsSummary,
                    color = Color.White.copy(alpha = if (isCurrentUser) 0.78f else 0.48f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isCurrentUser) {
                Box(
                    modifier =
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.92f)),
                )
            } else {
                Text(
                    text = "\u203A",
                    style = GlassType.chevron,
                    color = Color.White.copy(alpha = 0.32f),
                )
            }
        }
    }
}

@Composable
private fun GlassAddProfileRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(15.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = Color.White.copy(alpha = 0.72f),
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f, pressedScale = 0.985f),
        modifier = modifier.fillMaxWidth().height(56.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = stringResource(R.string.add_user),
                style = GlassType.settingsTitle.copy(fontSize = 17.sp),
            )
        }
    }
}

/** The way back to the server picker, quiet enough not to compete with the profiles. */
@Composable
private fun GlassServerRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(15.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = Color.White.copy(alpha = 0.55f),
                focusedContainerColor = Color.White.copy(alpha = 0.12f),
                focusedContentColor = Color.White,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f, pressedScale = 0.985f),
        modifier = modifier.fillMaxWidth().height(50.dp).padding(horizontal = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        ) {
            Text(
                text = stringResource(R.string.fa_arrow_left_arrow_right),
                fontFamily = FontAwesome,
                style = GlassType.meta,
                color = Color.Unspecified,
            )
            Text(
                text = stringResource(R.string.switch_servers),
                style = GlassType.settingsTitle.copy(fontSize = 15.sp),
            )
        }
    }
}

@Composable
private fun ClassicUserList(
    users: List<JellyfinUserAndImage>,
    currentUser: JellyfinUser?,
    onSwitchUser: (JellyfinUser) -> Unit,
    onAddUser: () -> Unit,
    onLongClickUser: (JellyfinUserAndImage) -> Unit,
    onSwitchServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val focusRequester = remember { FocusRequester() }
            val firstFocusRequester = remember { FocusRequester() }
            if (users.isNotEmpty()) {
                LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
                modifier =
                    Modifier
                        .wrapContentWidth()
                        .focusRestorer(firstFocusRequester)
                        .focusRequester(focusRequester),
            ) {
                itemsIndexed(users) { index, user ->
                    UserIconCard(
                        user = user,
                        isCurrentUser = user.user.id == currentUser?.id,
                        onClick = { onSwitchUser.invoke(user.user) },
                        onLongClick = { onLongClickUser.invoke(user) },
                        modifier = if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier,
                    )
                }
                item {
                    AddUserCard(onClick = { onAddUser.invoke() })
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
        ) {
            Button(
                onClick = { onSwitchServer.invoke() },
                glass = false,
                contentHeight = 44.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.fa_arrow_left_arrow_right),
                        fontFamily = FontAwesome,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        text = stringResource(R.string.switch_servers),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun UserIconCard(
    user: JellyfinUserAndImage,
    isCurrentUser: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val userColor = rememberIdColor(user.user.id)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            interactionSource = interactionSource,
            modifier = Modifier.size(Cards.serverUserCircle),
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = userColor.copy(alpha = if (isCurrentUser) 0.7f else 0.5f),
                    focusedContainerColor = userColor.copy(alpha = if (isCurrentUser) 0.9f else 0.7f),
                ),
            border =
                ClickableSurfaceDefaults.border(
                    focusedBorder =
                        Border(
                            border = BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface),
                            shape = CircleShape,
                        ),
                ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                UserIconCardImage(
                    id = user.user.id,
                    name = user.user.name,
                    imageUrl = user.imageUrl,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Text(
            text = user.user.name ?: user.user.id.toString(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .width(Cards.serverUserCircle)
                    .padding(horizontal = 4.dp),
        )
    }
}

@Composable
fun UserIconCardImage(
    id: UUID,
    name: String?,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    letterSize: androidx.compose.ui.unit.TextUnit = 14.sp,
) {
    var imageError by remember { mutableStateOf(false) }
    val userColor = rememberIdColor(id, alpha)
    Box(
        modifier =
            modifier.background(
                color = userColor,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl.isNotNullOrBlank() && !imageError) {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                onError = { imageError = true },
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
            )
        } else {
            val firstLetter =
                remember(id, name) {
                    (name ?: id.toString()).firstOrNull()?.uppercase() ?: "?"
                }
            Text(
                text = firstLetter,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = letterSize,
                fontWeight = FontWeight.Normal,
                color = textColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@PreviewTvSpec
@Composable
fun UserIconCardImagePreview() {
    WholphinTheme {
        UserIconCardImage(
            id = UUID.randomUUID(),
            name = "A smith",
            imageUrl = null,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun AddUserCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val addUserColor = MaterialTheme.colorScheme.surfaceVariant
    val cardSize = Cards.height2x3 * 0.75f

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Surface(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = Modifier.size(cardSize),
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = addUserColor.copy(alpha = 0.4f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = addUserColor.copy(alpha = 0.6f),
                    focusedContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            border =
                ClickableSurfaceDefaults.border(
                    focusedBorder =
                        Border(
                            border = BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface),
                            shape = CircleShape,
                        ),
                ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_user),
                    tint = Color.Unspecified,
                    modifier = Modifier.size(cardSize * 0.4f),
                )
            }
        }

        Text(
            text = stringResource(R.string.add_user),
            style =
                MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .width(cardSize)
                    .padding(horizontal = 4.dp),
        )
    }
}
