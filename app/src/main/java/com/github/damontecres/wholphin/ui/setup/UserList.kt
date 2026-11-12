package com.github.damontecres.wholphin.ui.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.ui.tryRequestFocus
import java.util.UUID

/**
 * Display a list of users plus option to add a new one or switch servers
 * Redesigned to match streaming service style with horizontal scrollable user icons
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

    val glass = LocalLiquidGlass.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (glass) 16.dp else 24.dp),
    ) {
        // Horizontal scrollable list of user icons - centered
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
                horizontalArrangement =
                    Arrangement.spacedBy(if (glass) GlassTokens.profileGap else 24.dp),
                // Focus grows a disc by a few dp; the row needs just enough edge room for that
                // and no more, or the first profile sits visibly inboard of the title.
                contentPadding = PaddingValues(horizontal = if (glass) 4.dp else 48.dp, vertical = 8.dp),
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
                        onLongClick = { showDeleteDialog = user },
                        modifier = if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier,
                    )
                }
                // Add User card - always rightmost
                item {
                    AddUserCard(
                        onClick = { onAddUser.invoke() },
                    )
                }
            }
        }

        // Switch servers button below user list - centered
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
        ) {
            Button(
                onClick = { onSwitchServer.invoke() },
                glass = LocalLiquidGlass.current,
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
                        style = if (LocalLiquidGlass.current) GlassType.button else MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
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

@Composable
private fun UserIconCard(
    user: JellyfinUserAndImage,
    isCurrentUser: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val glass = LocalLiquidGlass.current
    // Generate unique color for this user
    val userColor = rememberIdColor(user.user.id)

    // Track image loading errors
    var imageError by remember { mutableStateOf(false) }

    // Card dimensions - circular card. Glass uses the smaller tvOS profile disc so a row of
    // profiles fits inside the setup sheet instead of being clipped by its edge.
    val cardSize = if (glass) GlassTokens.profileSize else Cards.serverUserCircle

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (glass) GlassTokens.profileLabelGap else 24.dp),
    ) {
        // Circular card with colored background
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            interactionSource = interactionSource,
            modifier = Modifier.size(cardSize),
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor =
                        if (isCurrentUser) {
                            userColor.copy(alpha = if (glass) 0.46f else 0.7f)
                        } else {
                            userColor.copy(alpha = if (glass) 0.34f else 0.5f)
                        },
                    focusedContainerColor =
                        if (isCurrentUser) {
                            userColor.copy(alpha = if (glass) 0.66f else 0.9f)
                        } else {
                            userColor.copy(alpha = if (glass) 0.56f else 0.7f)
                        },
                ),
            border =
                ClickableSurfaceDefaults.border(
                    // The signed-in profile keeps a quiet ring at rest, the way a tvOS profile
                    // shows which viewer is active before anything is focused.
                    border =
                        if (glass && isCurrentUser) {
                            Border(
                                border = BorderStroke(2.dp, Color.White.copy(alpha = 0.82f)),
                                shape = CircleShape,
                            )
                        } else {
                            Border.None
                        },
                    focusedBorder =
                        Border(
                            border =
                                BorderStroke(
                                    width = if (glass) 2.5.dp else 3.dp,
                                    color = if (glass) Color.White else MaterialTheme.colorScheme.onSurface,
                                ),
                            shape = CircleShape,
                        ),
                ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = if (glass) 1.08f else 1.2f),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (user.imageUrl.isNotNullOrBlank() && !imageError) {
                    AsyncImage(
                        model = user.imageUrl,
                        contentDescription = user.user.name,
                        contentScale = ContentScale.Crop,
                        onError = { imageError = true },
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                    )
                } else {
                    // Show big bold first letter of username
                    val firstLetter =
                        remember(user) {
                            user.user.let {
                                (it.name ?: it.id.toString()).firstOrNull()?.uppercase()
                            } ?: "?"
                        }
                    Text(
                        text = firstLetter,
                        style =
                            if (glass) {
                                GlassType.pageTitle.copy(fontSize = 40.sp, lineHeight = 44.sp)
                            } else {
                                MaterialTheme.typography.displayLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                )
                            },
                        color = if (glass) Color.White else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // Username below the card
        Text(
            text = user.user.name ?: user.user.id.toString(),
            style = if (glass) GlassType.tab else MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (glass) Color.White else MaterialTheme.colorScheme.onSurface,
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

@Composable
fun UserIconCardImage(
    id: UUID,
    name: String?,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
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
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
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

/**
 * Add User card component - displays a + icon in a circle
 */
@Composable
private fun AddUserCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val glass = LocalLiquidGlass.current

    // Use a neutral gray color for the add user card
    val addUserColor = if (glass) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant

    // Card dimensions - circular card (same as user cards)
    val cardSize = if (glass) GlassTokens.profileSize else Cards.height2x3 * 0.75f

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (glass) GlassTokens.profileLabelGap else 24.dp),
    ) {
        // Circular card with colored background
        Surface(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = Modifier.size(cardSize),
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = if (glass) addUserColor else addUserColor.copy(alpha = 0.4f),
                    contentColor = if (glass) Color.White else MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = if (glass) Color.White else addUserColor.copy(alpha = 0.6f),
                    focusedContentColor = if (glass) Color.Black else MaterialTheme.colorScheme.onSurface,
                ),
            border =
                ClickableSurfaceDefaults.border(
                    focusedBorder =
                        Border(
                            border =
                                BorderStroke(
                                    width = if (glass) 2.dp else 3.dp,
                                    color = if (glass) Color.Black.copy(alpha = 0.35f) else MaterialTheme.colorScheme.onSurface,
                                ),
                            shape = CircleShape,
                        ),
                ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = if (glass) 1.10f else 1.2f),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_user),
                    tint = Color.Unspecified,
                    modifier = Modifier.size(cardSize * 0.4f), // Size of the + icon
                )
            }
        }

        // "Add User" text below the card
        Text(
            text = stringResource(R.string.add_user),
            style =
                if (glass) {
                    GlassType.tab
                } else {
                    MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                },
            color = if (glass) Color.White else MaterialTheme.colorScheme.onSurface,
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
