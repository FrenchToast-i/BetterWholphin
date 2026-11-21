package com.github.damontecres.wholphin.ui.kids

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.GlassTokens
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import kotlin.math.cos
import kotlin.math.sin

/**
 * A children's shelf in the shape of the PBS KIDS video app.
 *
 * The chrome is deliberately taken from PBS KIDS itself: the circular logo, the Headline and Sans
 * typefaces, the cyan field and the official character artwork for the PBS shows in the library.
 * What plays is never borrowed. The hero, the show wall and the episode rail are all read from the
 * viewer's Jellyfin library; a show without official PBS artwork keeps its own library image.
 *
 * The layout follows the app's own chrome: in Liquid Glass mode the top bar floats over a blue band,
 * so its white labels stay readable over the sunshine instead of disappearing into yellow.
 */
@Composable
fun KidsPage(
    modifier: Modifier = Modifier,
    viewModel: KidsViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.init() }
    val state by viewModel.state.collectAsState()
    val glass = LocalLiquidGlass.current
    val topInset = if (glass) GlassTokens.contentTop else 0.dp
    val navigation = viewModel.navigationManager

    Box(modifier.fillMaxSize()) {
        when (val loading = state.loading) {
            LoadingState.Pending,
            LoadingState.Loading,
            -> {
                KidsBackdrop(null)
                KidsStatusPanel(
                    topInset = topInset,
                    title = stringResource(R.string.loading),
                    body = stringResource(R.string.kids_loading_body),
                    loading = true,
                )
            }

            is LoadingState.Error -> {
                KidsBackdrop(null)
                KidsStatusPanel(
                    topInset = topInset,
                    title = stringResource(R.string.kids_error_title),
                    body = loading.localizedMessage.ifBlank { stringResource(R.string.kids_error_body) },
                    loading = false,
                    onRetry = viewModel::refresh,
                )
            }

            LoadingState.Success -> {
                if (state.isEmpty) {
                    KidsBackdrop(null)
                    KidsStatusPanel(
                        topInset = topInset,
                        title = stringResource(R.string.kids_empty_title),
                        body = stringResource(R.string.kids_empty_body),
                        loading = false,
                        onRetry = viewModel::refresh,
                    )
                } else {
                    KidsContent(
                        state = state,
                        onPlay = { navigation.navigateTo(Destination.Playback(it)) },
                        onOpen = { navigation.navigateTo(it.destination()) },
                    )
                }
            }
        }

        if (glass) {
            // The bar keeps its contrast when a show's backdrop scrolls under it. PBS blue is the
            // brand's own dark field, so the glass labels stay legible without leaving the palette.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(topInset)
                    .background(PbsKids.Blue),
            )
        }
    }
}

@Composable
private fun KidsContent(
    state: KidsState,
    onPlay: (BaseItem) -> Unit,
    onOpen: (BaseItem) -> Unit,
) {
    val glass = LocalLiquidGlass.current
    val topInset = if (glass) GlassTokens.contentTop else 0.dp
    val heroFocus = remember { FocusRequester() }
    val featured = state.featured
    var heroIndex by remember(featured) { mutableIntStateOf(0) }

    LaunchedEffect(featured) {
        if (featured.size <= 1) return@LaunchedEffect
        while (true) {
            delay(HERO_ROTATION_MS)
            heroIndex = (heroIndex + 1) % featured.size
        }
    }

    LaunchedEffect(featured.firstOrNull()?.id) {
        if (featured.isEmpty()) return@LaunchedEffect
        // The page opens on the hero, so one Select starts the thing being shown. The short
        // retry loop covers the frame between composition and the card being laid out.
        repeat(12) {
            if (heroFocus.tryRequestFocus()) return@LaunchedEffect
            delay(50)
        }
    }

    val showCells = remember(state.shows, state.liveTv) {
        buildList {
            state.liveTv?.let(::add)
            addAll(state.shows)
        }
    }
    val showRows = remember(showCells) { showCells.chunked(SHOW_COLUMNS) }
    val activeIndex = if (featured.isEmpty()) 0 else heroIndex % featured.size
    val active = featured.getOrNull(activeIndex)
    val activeBrand = remember(active?.id) { kidsBrandFor(active?.kidsShowTitle()) }

    Box(Modifier.fillMaxSize()) {
        KidsBackdrop(activeBrand)

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding =
                PaddingValues(
                    start = 32.dp,
                    end = 32.dp,
                    top = topInset + 4.dp,
                    bottom = 48.dp,
                ),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "masthead") {
                KidsMasthead()
            }

            if (active != null) {
                item(key = "hero") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        KidsHero(
                            item = active,
                            brand = activeBrand,
                            focusRequester = heroFocus,
                            onClick = {
                                if (active.playable) onPlay(active) else onOpen(active)
                            },
                        )
                        if (featured.size > 1) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 7.dp),
                            ) {
                                featured.indices.forEach { index ->
                                    Box(
                                        Modifier
                                            .size(6.dp)
                                            .background(
                                                if (index == activeIndex) Color.White else PbsKids.Ink.copy(alpha = 0.22f),
                                                CircleShape,
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showRows.isNotEmpty()) {
                item(key = "shows_title") {
                    KidsSectionTitle(
                        text = stringResource(R.string.kids_shows),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                showRows.forEachIndexed { rowIndex, row ->
                    item(key = "show_row_$rowIndex") {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            row.forEach { item ->
                                val isLive = state.liveTv?.id == item.id
                                KidsShowTile(
                                    item = item,
                                    isLive = isLive,
                                    brand = if (isLive) null else kidsBrandFor(item.kidsShowTitle()),
                                    onClick = { onOpen(item) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(SHOW_COLUMNS - row.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            if (state.watchNow.isNotEmpty()) {
                item(key = "watch_title") {
                    KidsSectionTitle(
                        text = stringResource(R.string.kids_watch_now),
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                item(key = "watch_row") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(end = 8.dp),
                    ) {
                        items(state.watchNow, key = { it.id }) { item ->
                            KidsVideoCard(item = item, onPlay = { onPlay(item) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KidsMasthead() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(bottom = 6.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.pbs_kids_logo),
            contentDescription = stringResource(R.string.kids),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(54.dp).clip(CircleShape),
        )
        Box(
            Modifier
                .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(percent = 50))
                .padding(horizontal = 18.dp, vertical = 7.dp),
        ) {
            Text(
                text = stringResource(R.string.kids_pick_a_show).uppercase(),
                color = PbsKids.Blue,
                fontFamily = PbsHeadline,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp,
                letterSpacing = 0.2.sp,
            )
        }
    }
}

@Composable
private fun KidsSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(PbsKids.Green, CircleShape),
        )
        Text(
            text = text.uppercase(),
            color = PbsKids.Blue,
            fontFamily = PbsHeadline,
            fontWeight = FontWeight.Bold,
            fontSize = 25.sp,
            letterSpacing = 0.4.sp,
        )
    }
}

@Composable
private fun KidsHero(
    item: BaseItem,
    brand: KidsShowBrand?,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val image = rememberKidsImage(item, fillWidth = 960)
    val showTitle = item.kidsShowTitle()
    val detail =
        if (item.type == BaseItemKind.EPISODE) {
            item.name
        } else {
            item.data.genres?.take(2)?.joinToString(" · ")
        }
    val footer = brand?.footer ?: PbsKids.Blue
    val onFooter = brand?.onFooter ?: Color.White

    Card(
        onClick = onClick,
        onLongClick = {},
        interactionSource = interaction,
        shape = CardDefaults.shape(shape),
        border =
            CardDefaults.border(
                border = Border(BorderStroke(4.dp, Color.White), shape = shape),
                focusedBorder = Border(BorderStroke(6.dp, Color.White), shape = shape),
            ),
        scale = CardDefaults.scale(focusedScale = 1.035f, pressedScale = 0.99f),
        colors = CardDefaults.colors(containerColor = Color.Transparent),
        modifier =
            Modifier
                .width(290.dp)
                .focusRequester(focusRequester),
    ) {
        Column {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            ) {
                KidsArtwork(
                    item = item,
                    image = image,
                    contentDescription = showTitle,
                    modifier = Modifier.fillMaxSize(),
                )
                if (focused) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .size(54.dp)
                                .background(PbsKids.Blue.copy(alpha = 0.95f), CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(footer)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                if (brand != null) {
                    Box(
                        Modifier
                            .background(Color.White, RoundedCornerShape(7.dp))
                            .padding(horizontal = 7.dp, vertical = 4.dp),
                    ) {
                        Image(
                            painter = painterResource(brand.logo),
                            contentDescription = showTitle,
                            contentScale = ContentScale.Fit,
                            modifier =
                                Modifier
                                    .height(26.dp)
                                    .width((26 * brand.logoAspect).dp),
                        )
                    }
                } else {
                    Text(
                        text = showTitle,
                        color = onFooter,
                        fontFamily = PbsHeadline,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!detail.isNullOrBlank()) {
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = detail,
                        color = onFooter.copy(alpha = 0.92f),
                        fontFamily = PbsSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun KidsShowTile(
    item: BaseItem,
    isLive: Boolean,
    brand: KidsShowBrand?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    val image = if (isLive || brand != null) null else rememberKidsImage(item, fillWidth = 400)
    Column(modifier = modifier) {
        Card(
            onClick = onClick,
            onLongClick = {},
            shape = CardDefaults.shape(shape),
            border =
                CardDefaults.border(
                    border = Border(BorderStroke(3.dp, Color.White), shape = shape),
                    focusedBorder = Border(BorderStroke(5.dp, Color.White), shape = shape),
                ),
            scale = CardDefaults.scale(focusedScale = 1.05f, pressedScale = 0.98f),
            colors = CardDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth().aspectRatio(3f / 2f),
        ) {
            when {
                isLive -> KidsLiveArt()
                brand != null -> KidsCharacterArt(brand = brand, contentDescription = item.name)
                else ->
                    KidsArtwork(
                        item = item,
                        image = image,
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                    )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = if (isLive) stringResource(R.string.live_tv) else item.name.orEmpty(),
            color = PbsKids.Ink,
            fontFamily = PbsSans,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            lineHeight = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        )
    }
}

/**
 * A show tile made from the show's official PBS KIDS character cut-out. The white circle gives the
 * cut-out the same sticker-like seat it has on the PBS KIDS site, and keeps dark characters visible
 * on the dark Odd Squad card.
 */
@Composable
private fun KidsCharacterArt(
    brand: KidsShowBrand,
    contentDescription: String?,
) {
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier.fillMaxSize().background(brand.color),
    ) {
        Box(
            Modifier
                .fillMaxHeight(0.82f)
                .aspectRatio(1f)
                .padding(bottom = 7.dp)
                .background(Color.White.copy(alpha = 0.94f), CircleShape),
        )
        Image(
            painter = painterResource(brand.character),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier =
                Modifier
                    .fillMaxHeight(0.78f)
                    .fillMaxWidth(0.76f)
                    .padding(bottom = 4.dp),
        )
    }
}

@Composable
private fun KidsLiveArt() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(PbsKids.Orange, PbsKids.OrangeDeep)),
                ),
    ) {
        Text(
            text = stringResource(R.string.fa_tv),
            color = Color.White,
            fontFamily = FontAwesome,
            fontSize = 24.sp,
        )
        Text(
            text = stringResource(R.string.live_tv).uppercase(),
            color = Color.White,
            fontFamily = PbsHeadline,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            letterSpacing = 0.3.sp,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(Modifier.size(7.dp).background(Color(0xFFE32636), CircleShape))
            Text(
                text = stringResource(R.string.kids_on_now),
                color = Color.White.copy(alpha = 0.96f),
                fontFamily = PbsSans,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
            )
        }
    }
}

@Composable
private fun KidsVideoCard(
    item: BaseItem,
    onPlay: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val image = rememberKidsImage(item, fillWidth = 520)
    val showTitle = item.kidsShowTitle()
    val subtitle = if (item.type == BaseItemKind.EPISODE) item.name else null

    Column(Modifier.width(224.dp)) {
        Card(
            onClick = onPlay,
            onLongClick = {},
            shape = CardDefaults.shape(shape),
            border =
                CardDefaults.border(
                    border = Border(BorderStroke(3.dp, Color.White.copy(alpha = 0.95f)), shape = shape),
                    focusedBorder = Border(BorderStroke(5.dp, Color.White), shape = shape),
                ),
            scale = CardDefaults.scale(focusedScale = 1.04f, pressedScale = 0.98f),
            colors = CardDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth().height(126.dp),
        ) {
            Box(Modifier.fillMaxSize()) {
                KidsArtwork(
                    item = item,
                    image = image,
                    contentDescription = showTitle,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Transparent, PbsKids.Ink.copy(alpha = 0.55f)),
                            ),
                        ),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = showTitle,
            color = PbsKids.Ink,
            fontFamily = PbsSans,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = PbsKids.Ink.copy(alpha = 0.72f),
                fontFamily = PbsSans,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun KidsArtwork(
    item: BaseItem,
    image: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    if (image != null) {
        AsyncImage(
            model = image,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.background(PbsKids.Blue),
        ) {
            Text(
                text = item.name?.trim()?.take(1)?.uppercase().orEmpty(),
                color = Color.White,
                fontFamily = PbsHeadline,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
            )
        }
    }
}

@Composable
private fun KidsStatusPanel(
    topInset: Dp,
    title: String,
    body: String?,
    loading: Boolean,
    onRetry: (() -> Unit)? = null,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .fillMaxSize()
                .padding(start = 32.dp, end = 32.dp, top = topInset + 24.dp, bottom = 24.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .widthIn(max = 460.dp)
                    .background(Color.White.copy(alpha = 0.97f), RoundedCornerShape(28.dp))
                    .padding(horizontal = 32.dp, vertical = 24.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.pbs_kids_logo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(62.dp).clip(CircleShape),
            )
            if (loading) {
                CircularProgressIndicator(
                    color = PbsKids.Blue,
                    modifier = Modifier.size(38.dp),
                )
            }
            Text(
                text = title,
                color = PbsKids.Blue,
                fontFamily = PbsHeadline,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
            )
            if (!body.isNullOrBlank()) {
                Text(
                    text = body,
                    color = PbsKids.Ink.copy(alpha = 0.78f),
                    fontFamily = PbsSans,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onRetry != null) {
                KidsButton(text = stringResource(R.string.retry), onClick = onRetry)
            }
        }
    }
}

@Composable
private fun KidsButton(
    text: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = PbsKids.Blue,
                contentColor = Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor = PbsKids.Blue,
                pressedContainerColor = PbsKids.Blue.copy(alpha = 0.86f),
                pressedContentColor = Color.White,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, PbsKids.Blue), shape = shape),
            ),
        modifier = Modifier.height(44.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxHeight().padding(horizontal = 26.dp),
        ) {
            Text(
                text = text,
                color = LocalContentColor.current,
                fontFamily = PbsHeadline,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun rememberKidsImage(
    item: BaseItem,
    fillWidth: Int,
): String? {
    val imageService = LocalImageUrlService.current
    return remember(item.id, fillWidth) {
        item.imageUrlOverride
            ?: imageService.getItemImageUrl(
                item = item,
                imageType = item.kidsImageType(),
                fillWidth = fillWidth,
                useSeriesForPrimary = true,
            )
    }
}

/**
 * The widest real image the item has. A hero is 16:9, so a thumb or backdrop beats a poster even
 * when both exist; the poster only becomes the card when there is nothing else to show.
 */
private fun BaseItem.kidsImageType(): ImageType =
    when {
        data.imageTags?.get(ImageType.THUMB) != null || data.parentThumbItemId != null -> ImageType.THUMB
        data.backdropImageTags?.isNotEmpty() == true || data.parentBackdropItemId != null -> ImageType.BACKDROP
        else -> ImageType.PRIMARY
    }

private fun BaseItem.kidsShowTitle(): String =
    if (type == BaseItemKind.EPISODE) {
        data.seriesName ?: name.orEmpty()
    } else {
        name.orEmpty()
    }

/**
 * The page's own sky: PBS KIDS cyan with a soft burst of light, and the featured show's official
 * backdrop fading into it. Drawn behind the content so the whole page changes colour with the hero
 * the same way a PBS KIDS show page does.
 */
@Composable
private fun KidsBackdrop(brand: KidsShowBrand?) {
    Box(Modifier.fillMaxSize().background(PbsKids.Cyan)) {
        Canvas(Modifier.fillMaxSize()) {
            val origin = Offset(size.width * 1.02f, -size.height * 0.18f)
            val length = size.width * 1.8f
            for (index in 0..14) {
                val angle = Math.toRadians(126.0 + index * 5.4)
                drawLine(
                    color = Color.White.copy(alpha = 0.075f),
                    start = origin,
                    end =
                        Offset(
                            origin.x + cos(angle).toFloat() * length,
                            origin.y + sin(angle).toFloat() * length,
                        ),
                    strokeWidth = 48.dp.toPx(),
                )
            }
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = size.width * 0.22f,
                center = Offset(size.width * 0.06f, size.height * 0.96f),
            )
        }
        Crossfade(targetState = brand) { active ->
            if (active != null) {
                Box(Modifier.fillMaxWidth().height(390.dp)) {
                    Image(
                        painter = painterResource(active.backdrop),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        active.color.copy(alpha = 0.72f),
                                        active.color.copy(alpha = 0.42f),
                                        PbsKids.Cyan,
                                    ),
                                ),
                            ),
                    )
                }
            }
        }
    }
}

private const val SHOW_COLUMNS = 6
private const val HERO_ROTATION_MS = 9_000L
