@file:OptIn(ExperimentalCoilApi::class)

package com.github.damontecres.wholphin.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import coil3.compose.useExistingImageAsPlaceholder
import coil3.request.ImageRequest
import coil3.request.transitionFactory
import com.github.damontecres.wholphin.preferences.BackdropStyle
import com.github.damontecres.wholphin.services.BackdropResult
import com.github.damontecres.wholphin.ui.CrossFadeFactory
import com.github.damontecres.wholphin.ui.theme.GlassAmbientBackground
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Shows the current backdrop images provided by [com.github.damontecres.wholphin.services.BackdropService]
 */
@Composable
fun Backdrop(
    drawerIsOpen: Boolean,
    backdropStyle: BackdropStyle,
    viewModel: ApplicationContentViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
    enableTopScrim: Boolean = true,
    useExistingImageAsPlaceholder: Boolean = false,
    crossfadeDuration: Duration = 800.milliseconds,
    fullBleed: Boolean = false,
) {
    val backdrop by viewModel.backdropService.backdropFlow.collectAsStateWithLifecycle()
    Backdrop(
        backdrop = backdrop,
        drawerIsOpen = drawerIsOpen,
        backdropStyle = backdropStyle,
        modifier = modifier,
        enableTopScrim = enableTopScrim,
        useExistingImageAsPlaceholder = useExistingImageAsPlaceholder,
        crossfadeDuration = crossfadeDuration,
        fullBleed = fullBleed,
    )
}

/**
 * Shows the current backdrop images provided by the [BackdropResult]
 */
@Composable
fun Backdrop(
    backdrop: BackdropResult,
    drawerIsOpen: Boolean,
    backdropStyle: BackdropStyle,
    modifier: Modifier = Modifier,
    enableTopScrim: Boolean = true,
    useExistingImageAsPlaceholder: Boolean = false,
    crossfadeDuration: Duration = 800.milliseconds,
    fullBleed: Boolean = false,
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val baseBackgroundColor = MaterialTheme.colorScheme.background
    if (backdrop.hasColors &&
        (backdropStyle == BackdropStyle.BACKDROP_DYNAMIC_COLOR || backdropStyle == BackdropStyle.UNRECOGNIZED)
    ) {
        val animPrimary by animateColorAsState(
            backdrop.primaryColor,
            animationSpec = tween(1250),
            label = "dynamic_backdrop_primary",
        )
        val animSecondary by animateColorAsState(
            backdrop.secondaryColor,
            animationSpec = tween(1250),
            label = "dynamic_backdrop_secondary",
        )
        val animTertiary by animateColorAsState(
            backdrop.tertiaryColor,
            animationSpec = tween(1250),
            label = "dynamic_backdrop_tertiary",
        )
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(color = baseBackgroundColor)
                        val start = if (isRtl) size.width else 0f
                        val end = if (isRtl) 0f else size.width
                        // Top Left (Vibrant/Muted)
                        drawRect(
                            brush =
                                Brush.radialGradient(
                                    colors = listOf(animSecondary, Color.Transparent),
                                    center = Offset(start, 0f),
                                    radius = size.width * 0.8f,
                                ),
                        )
                        // Bottom Right (DarkVibrant/DarkMuted)
                        drawRect(
                            brush =
                                Brush.radialGradient(
                                    colors = listOf(animPrimary, Color.Transparent),
                                    center = Offset(end, size.height),
                                    radius = size.width * 0.8f,
                                ),
                        )
                        // Bottom Left (Dark / Bridge)
                        drawRect(
                            brush =
                                Brush.radialGradient(
                                    colors =
                                        listOf(
                                            baseBackgroundColor,
                                            Color.Transparent,
                                        ),
                                    center = Offset(start, size.height),
                                    radius = size.width * 0.8f,
                                ),
                        )
                        // Top Right (Under Image - Vibrant/Bright)
                        drawRect(
                            brush =
                                Brush.radialGradient(
                                    colors = listOf(animTertiary, Color.Transparent),
                                    center = Offset(end, 0f),
                                    radius = size.width * 0.8f,
                                ),
                        )
                    },
        )
    }
    if (backdropStyle == BackdropStyle.BACKDROP_NONE) {
        return
    }
    if (backdrop.imageUrl == null) {
        // Nothing is focused, or the focused thing has no landscape art. A television with a
        // flat black field looks broken between pages, and glass has nothing to refract; the
        // theme's ambient field is quiet enough to sit under every page and still changes with
        // the palette the viewer chose.
        GlassAmbientBackground(modifier)
        return
    }
    if (backdropStyle != BackdropStyle.BACKDROP_NONE) {
        Box(
            modifier = modifier.fillMaxSize(),
        ) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalContext.current)
                        .data(backdrop.imageUrl)
                        .useExistingImageAsPlaceholder(useExistingImageAsPlaceholder)
                        .transitionFactory(CrossFadeFactory(crossfadeDuration))
                        .build(),
                contentDescription = null,
                contentScale = if (fullBleed) ContentScale.Crop else ContentScale.Fit,
                alignment = if (fullBleed) Alignment.Center else Alignment.TopEnd,
                modifier =
                    Modifier
                        .then(
                            if (fullBleed) {
                                Modifier.fillMaxSize()
                            } else {
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .fillMaxHeight(.7f)
                                    .fillMaxWidth(.7f)
                            },
                        ).graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            if (fullBleed) {
                                // Full-frame artwork: glass can only refract what is actually
                                // behind it, and a corner thumbnail leaves most panes sampling
                                // nothing but background. The scrims keep type readable without
                                // flattening the image into a grey wash.
                                // Light scrims only: the artwork is the thing the glass
                                // refracts, so every bit of it that gets covered by a black
                                // wash is glass the viewer cannot see.
                                drawContent()
                                drawRect(
                                    brush =
                                        Brush.horizontalGradient(
                                            colorStops =
                                                arrayOf(
                                                    0f to Color.Black.copy(alpha = 0.30f),
                                                    0.50f to Color.Black.copy(alpha = 0.10f),
                                                    1f to Color.Black.copy(alpha = 0.02f),
                                                ),
                                        ),
                                )
                                // Heavier at the bottom than the top: shelves and row titles sit
                                // there, and white type over sunlit artwork needs a floor. The
                                // middle stays clear so the frame still reads as the artwork.
                                drawRect(
                                    brush =
                                        Brush.verticalGradient(
                                            colorStops =
                                                arrayOf(
                                                    0f to Color.Black.copy(alpha = 0.16f),
                                                    0.40f to Color.Black.copy(alpha = 0.04f),
                                                    0.70f to Color.Black.copy(alpha = 0.16f),
                                                    1f to Color.Black.copy(alpha = 0.55f),
                                                ),
                                        ),
                                )
                                return@drawWithContent
                            }
                            val start = if (isRtl) size.width else 0f
                            drawContent()
                            if (drawerIsOpen) {
                                drawRect(
                                    brush = SolidColor(Color.Black),
                                    alpha = .75f,
                                )
                            }
                            // Subtle top scrim for system UI readability (clock, tabs)
                            if (enableTopScrim) {
                                drawRect(
                                    brush =
                                        Brush.verticalGradient(
                                            colorStops =
                                                arrayOf(
                                                    0f to Color.Black.copy(alpha = TOP_SCRIM_ALPHA),
                                                    TOP_SCRIM_END_FRACTION to Color.Transparent,
                                                ),
                                        ),
                                    blendMode = BlendMode.Multiply,
                                )
                            }
                            drawRect(
                                brush =
                                    Brush.horizontalGradient(
                                        colors = listOf(Color.Transparent, Color.Black),
                                        startX = start,
                                        endX = size.width * 0.6f,
                                    ),
                                blendMode = BlendMode.DstIn,
                            )
                            drawRect(
                                brush =
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Black, Color.Transparent),
                                        startY = 0f,
                                        endY = size.height,
                                    ),
                                blendMode = BlendMode.DstIn,
                            )
                        },
            )
        }
    }
}
