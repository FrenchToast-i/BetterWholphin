package com.github.damontecres.wholphin.ui.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.components.CircularProgress
import com.github.damontecres.wholphin.ui.theme.GlassTokens
import com.github.damontecres.wholphin.ui.theme.GlassType
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import org.jellyfin.sdk.model.api.PublicSystemInfo
import java.util.UUID

sealed interface ServerConnectionStatus {
    data class Success(
        val systemInfo: PublicSystemInfo,
    ) : ServerConnectionStatus

    object Pending : ServerConnectionStatus

    data class Error(
        val message: String?,
    ) : ServerConnectionStatus
}

/**
 * Generate a consistent color for a UUID
 */
@Composable
fun rememberIdColor(
    id: UUID?,
    alpha: Float = 1f,
    nullColor: Color = MaterialTheme.colorScheme.surfaceVariant,
): Color =
    remember(id, alpha) {
        if (id == null) {
            return@remember nullColor
        }
        // Generate a color based on the server ID hash, fallback to URL hash
        val hash = id.hashCode()
        val hue = (hash % 360).toFloat()
        val saturation = 0.6f + ((hash / 360) % 40).toFloat() / 100f // 0.6-1.0
        val brightness = 0.4f + ((hash / 14400) % 30).toFloat() / 100f // 0.4-0.7 (darker colors)

        // Convert HSV to RGB
        val c = brightness * saturation
        val x = c * (1 - kotlin.math.abs((hue / 60f) % 2f - 1))
        val m = brightness - c

        val (r, g, b) =
            when {
                hue < 60 -> Triple(c, x, 0f)
                hue < 120 -> Triple(x, c, 0f)
                hue < 180 -> Triple(0f, c, x)
                hue < 240 -> Triple(0f, x, c)
                hue < 300 -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }

        Color(
            red = (r + m).coerceIn(0f, 1f),
            green = (g + m).coerceIn(0f, 1f),
            blue = (b + m).coerceIn(0f, 1f),
            alpha = alpha,
        )
    }

/**
 * Server icon card component - displays a circular card with server name/letter
 */
@Composable
fun ServerIconCard(
    server: JellyfinServer,
    serverVersionSupported: ServerVersionSupported,
    connectionStatus: ServerConnectionStatus,
    isCurrentServer: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    allowDelete: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val glass = LocalLiquidGlass.current

    // Generate unique color for this server
    val serverColor = rememberIdColor(server.id)

    // Card dimensions - circular card
    val cardSize = if (glass) GlassTokens.profileSize else Cards.serverUserCircle

    val displayText =
        remember(server) {
            (server.name ?: server.url.replace(Regex("^https?://"), ""))
                .firstOrNull()
                ?.uppercase()
                ?: "?"
        }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (glass) GlassTokens.profileLabelGap else 24.dp),
    ) {
        // Circular card with colored background
        Surface(
            onClick = onClick,
            onLongClick = if (allowDelete) onLongClick else null,
            interactionSource = interactionSource,
            modifier = Modifier.size(cardSize),
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor =
                        if (isCurrentServer) {
                            serverColor.copy(alpha = if (glass) 0.46f else 0.7f)
                        } else {
                            serverColor.copy(alpha = if (glass) 0.34f else 0.5f)
                        },
                    focusedContainerColor =
                        if (isCurrentServer) {
                            serverColor.copy(alpha = if (glass) 0.66f else 0.9f)
                        } else {
                            serverColor.copy(alpha = if (glass) 0.56f else 0.7f)
                        },
                ),
            border =
                ClickableSurfaceDefaults.border(
                    // A profile picture on glass is a tinted disc, not a pane; the focus ring is
                    // the one place a drawn outline is the right signal.
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
            scale = ClickableSurfaceDefaults.scale(focusedScale = if (glass) 1.10f else 1.2f),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                // Show connection status indicator or server name/letter
                when {
                    connectionStatus is ServerConnectionStatus.Error || serverVersionSupported != ServerVersionSupported.SUPPORTED -> {
                        // Show warning icon with server letter below
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = (connectionStatus as? ServerConnectionStatus.Error)?.message,
                                tint =
                                    if (serverVersionSupported == ServerVersionSupported.NOT_SUPPORTED_IN_FUTURE) {
                                        AppColors.GoldenYellow
                                    } else {
                                        MaterialTheme.colorScheme.errorContainer
                                    },
                                modifier = Modifier.size(cardSize * 0.3f),
                            )
                            Text(
                                text = displayText,
                                style =
                                    MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                    ),
                                color = if (glass) Color.White else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    connectionStatus is ServerConnectionStatus.Success -> {
                        // Show server name/letter

                        Text(
                            text = displayText,
                            style =
                                MaterialTheme.typography.displayLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                            color = if (glass) Color.White else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                    }

                    connectionStatus == ServerConnectionStatus.Pending -> {
                        CircularProgress(
                            modifier = Modifier.size(cardSize * 0.4f),
                        )
                    }
                }
            }
        }

        // Server name below the card
        Text(
            text = server.name?.ifBlank { null } ?: server.url,
            style = if (glass) GlassType.tab else MaterialTheme.typography.bodyLarge,
            color = if (glass) Color.White else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
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

/**
 * Add Server card component - displays a + icon in a circle
 */
@Composable
fun AddServerCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val glass = LocalLiquidGlass.current

    // Use a neutral gray color for the add server card
    val addServerColor = if (glass) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant

    // Card dimensions - circular card (same as server cards)
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
            modifier =
                Modifier
                    .size(cardSize)
                    .testTag("add_server"),
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = if (glass) addServerColor else addServerColor.copy(alpha = 0.4f),
                    contentColor = if (glass) Color.White else MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = if (glass) Color.White else addServerColor.copy(alpha = 0.6f),
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
                    contentDescription = stringResource(R.string.add_server),
                    tint = Color.Unspecified,
                    modifier = Modifier.size(cardSize * 0.4f), // Size of the + icon
                )
            }
        }

        // "Add Server" text below the card
        Text(
            text = stringResource(R.string.add_server),
            style =
                if (glass) {
                    GlassType.tab
                } else {
                    MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
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
