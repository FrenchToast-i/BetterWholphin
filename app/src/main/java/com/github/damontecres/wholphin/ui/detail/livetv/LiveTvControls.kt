package com.github.damontecres.wholphin.ui.detail.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.cards.glassCardBorder
import com.github.damontecres.wholphin.ui.cards.glassCardGlow
import com.github.damontecres.wholphin.ui.cards.glassCardScale
import com.github.damontecres.wholphin.ui.cards.glassCardShape
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.Button
import com.github.damontecres.wholphin.ui.components.CircularProgress
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogItemDivider
import com.github.damontecres.wholphin.ui.components.DialogItemEntry
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButton
import com.github.damontecres.wholphin.ui.components.TextButton
import com.github.damontecres.wholphin.ui.search.SearchFieldRow
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.theme.GlassTokens
import com.github.damontecres.wholphin.ui.theme.GlassType
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

private val headerTime: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val headerDayTime: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE h:mm a", Locale.getDefault())

/**
 * The identity band above the Live TV tabs.
 *
 * It answers the three questions a viewer has before choosing a tab: is anything on, when is the
 * next thing, and where was I? The old band also carried three inline filter chips, which grew into
 * a row of controls the remote had to cross before reaching the page. Filters now live in one sheet
 * with an active count, and the band keeps the two actions worth a stop: Watch last and Search.
 */
@Composable
fun LiveTvPageHeader(
    liveCount: Int,
    sportsLiveCount: Int,
    nextStart: LocalDateTime?,
    now: LocalDateTime,
    lastChannel: TvChannel?,
    activeFilterCount: Int,
    tabFocusRequester: FocusRequester,
    actionFocusRequester: FocusRequester,
    onWatchLast: () -> Unit,
    onSearch: () -> Unit,
    onNumberEntry: () -> Unit,
    onFilters: () -> Unit,
) {
    val glass = LocalLiquidGlass.current
    val quiet = if (glass) Color.White.copy(alpha = 0.62f) else MaterialTheme.colorScheme.onSurfaceVariant
    // The actions sit above the tab strip, but their geometry is to the right of it. Without an
    // explicit target, Down from Filters skips the tabs entirely and lands in the page's sort
    // controls, which leaves no obvious route back to Now / Guide / Channels. Every action goes
    // to the selected tab instead, and the tab strip's own Down continues into the page.
    val downToTab = Modifier.focusProperties { down = tabFocusRequester }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = pageStartPadding(), end = 24.dp, top = 2.dp),
    ) {
        Text(
            text = stringResource(R.string.live_tv),
            style = if (glass) GlassType.pageTitle else MaterialTheme.typography.headlineSmall,
            color = if (glass) Color.White else MaterialTheme.colorScheme.onSurface,
        )
        if (liveCount > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier =
                    Modifier
                        .background(LiveRed.copy(alpha = 0.16f), RoundedCornerShape(percent = 50))
                        .padding(horizontal = 11.dp, vertical = 5.dp),
            ) {
                Box(Modifier.size(7.dp).background(LiveRed, CircleShape))
                Text(
                    text = stringResource(R.string.live_tv_header_live, liveCount),
                    style = if (glass) GlassType.meta else MaterialTheme.typography.labelMedium,
                    color = if (glass) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (sportsLiveCount > 0) {
                Text(
                    text = stringResource(R.string.live_tv_header_sports, sportsLiveCount),
                    style = if (glass) GlassType.meta else MaterialTheme.typography.labelMedium,
                    color = quiet,
                )
            }
        } else {
            Text(
                text = stringResource(R.string.live_tv_header_quiet),
                style = if (glass) GlassType.meta else MaterialTheme.typography.bodyMedium,
                color = quiet,
            )
        }
        Spacer(Modifier.weight(1f))
        nextStart?.let {
            Text(
                text =
                    stringResource(
                        R.string.live_tv_header_next,
                        if (it.toLocalDate() == now.toLocalDate()) {
                            it.format(headerTime)
                        } else {
                            it.format(headerDayTime)
                        },
                    ),
                style = if (glass) GlassType.meta else MaterialTheme.typography.bodyMedium,
                color = quiet,
            )
        }
        lastChannel?.let {
            Button(
                onClick = onWatchLast,
                modifier = downToTab,
                glassFilled = true,
                shape =
                    androidx.tv.material3.ClickableSurfaceDefaults.shape(
                        RoundedCornerShape(GlassTokens.pillRadius),
                    ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                contentHeight = 40.dp,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = stringResource(R.string.live_tv_watch_last),
                    style = if (glass) GlassType.button else MaterialTheme.typography.labelLarge,
                )
            }
        }
        ExpandableFaButton(
            title = R.string.search,
            iconStringRes = R.string.fa_magnifying_glass,
            onClick = onSearch,
            size = 40.dp,
            expandLabel = false,
            modifier = downToTab,
        )
        ExpandableFaButton(
            title = R.string.live_tv_channel_number,
            iconStringRes = R.string.fa_hashtag,
            onClick = onNumberEntry,
            size = 40.dp,
            expandLabel = false,
            modifier = downToTab,
        )
        Button(
            onClick = onFilters,
            modifier =
                Modifier
                    .focusRequester(actionFocusRequester)
                    .focusProperties { down = tabFocusRequester },
            glassFilled = true,
            shape =
                androidx.tv.material3.ClickableSurfaceDefaults.shape(
                    RoundedCornerShape(GlassTokens.pillRadius),
                ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            contentHeight = 40.dp,
        ) {
            Text(
                text =
                    if (activeFilterCount > 0) {
                        stringResource(R.string.live_tv_filters_active, activeFilterCount)
                    } else {
                        stringResource(R.string.live_tv_filters)
                    },
                style = if (glass) GlassType.button else MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * One sheet for every lens. Toggling keeps the sheet open, because choosing "favourites + live" is
 * two decisions, not two trips through the header.
 */
@Composable
fun LiveTvFilterDialog(
    filters: LiveTvFilters,
    hasLive: Boolean,
    hasSports: Boolean,
    availableCategories: Set<ProgramCategory>,
    lastUpdated: String?,
    onFiltersChange: (LiveTvFilters) -> Unit,
    onRefreshGuide: () -> Unit,
    onDismiss: () -> Unit,
    onSearch: (() -> Unit)? = null,
    onOpenDvr: (() -> Unit)? = null,
) {
    val items =
        buildList<DialogItemEntry> {
            if (onSearch != null) {
                add(
                    DialogItem(
                        headlineContent = { Text(stringResource(R.string.live_tv_search_title)) },
                        leadingContent = { Text(stringResource(R.string.fa_magnifying_glass), fontFamily = com.github.damontecres.wholphin.ui.FontAwesome) },
                        onClick = onSearch,
                    ),
                )
            }
            if (onOpenDvr != null) {
                add(
                    DialogItem(
                        headlineContent = { Text(stringResource(R.string.tv_dvr_schedule)) },
                        leadingContent = { Text(stringResource(R.string.fa_tv), fontFamily = com.github.damontecres.wholphin.ui.FontAwesome) },
                        onClick = onOpenDvr,
                    ),
                )
            }
            if (onSearch != null || onOpenDvr != null) add(DialogItemDivider)
            add(
                DialogItem(
                    headlineContent = { Text(stringResource(R.string.favorites)) },
                    leadingContent = { Text(stringResource(R.string.fa_heart), fontFamily = com.github.damontecres.wholphin.ui.FontAwesome) },
                    selected = filters.favoritesOnly,
                    trailingContent = { SelectedCheck(filters.favoritesOnly) },
                    onClick = { onFiltersChange(filters.toggleFavorites()) },
                ),
            )
            if (hasLive) {
                add(
                    DialogItem(
                        headlineContent = { Text(stringResource(R.string.live_tv_filter_live)) },
                        leadingContent = { Text(stringResource(R.string.fa_tv), fontFamily = com.github.damontecres.wholphin.ui.FontAwesome) },
                        selected = filters.liveOnly,
                        trailingContent = { SelectedCheck(filters.liveOnly) },
                        onClick = { onFiltersChange(filters.toggleLive()) },
                    ),
                )
            }
            if (hasSports) {
                add(
                    DialogItem(
                        headlineContent = { Text(stringResource(R.string.live_tv_filter_sports)) },
                        leadingContent = { Text(stringResource(R.string.fa_football), fontFamily = com.github.damontecres.wholphin.ui.FontAwesome) },
                        selected = filters.sportsOnly,
                        trailingContent = { SelectedCheck(filters.sportsOnly) },
                        onClick = { onFiltersChange(filters.toggleSports()) },
                    ),
                )
            }
            add(
                DialogItem(
                    headlineContent = { Text(stringResource(R.string.live_tv_filter_has_guide)) },
                    leadingContent = { Text(stringResource(R.string.fa_list_ul), fontFamily = com.github.damontecres.wholphin.ui.FontAwesome) },
                    selected = filters.hasGuideOnly,
                    trailingContent = { SelectedCheck(filters.hasGuideOnly) },
                    onClick = { onFiltersChange(filters.toggleHasGuide()) },
                ),
            )
            if (availableCategories.isNotEmpty()) {
                add(DialogItemDivider)
                add(
                    DialogItem(
                        headlineContent = { Text(stringResource(R.string.live_tv_filter_all_categories)) },
                        selected = filters.category == null,
                        trailingContent = { SelectedCheck(filters.category == null) },
                        onClick = { onFiltersChange(filters.setCategory(null)) },
                    ),
                )
                availableCategories.sortedBy { it.ordinal }.forEach { category ->
                    add(
                        DialogItem(
                            headlineContent = { Text(categoryLabel(category)) },
                            selected = filters.category == category,
                            trailingContent = { SelectedCheck(filters.category == category) },
                            onClick = { onFiltersChange(filters.setCategory(category)) },
                        ),
                    )
                }
            }
            add(DialogItemDivider)
            add(
                DialogItem(
                    headlineContent = { Text(stringResource(R.string.live_tv_refresh_guide)) },
                    // The freshness timestamp is context for the refresh, not a visitable row.
                    // It used to be a disabled list item, which took a D-pad stop and a full row
                    // without being actionable.
                    supportingContent = lastUpdated?.let { updated ->
                        { Text(stringResource(R.string.live_tv_guide_updated, updated)) }
                    },
                    leadingContent = { Text("\u21bb") },
                    onClick = onRefreshGuide,
                ),
            )
            add(
                DialogItem(
                    headlineContent = { Text(stringResource(R.string.live_tv_clear_filters)) },
                    onClick = { onFiltersChange(filters.clear()) },
                ),
            )
            add(
                DialogItem(
                    headlineContent = { Text(stringResource(R.string.close)) },
                    onClick = onDismiss,
                ),
            )
        }
    DialogPopup(
        params = DialogParams(false, stringResource(R.string.live_tv_filters), items),
        dismissOnClick = false,
        onDismissRequest = onDismiss,
        listModifier = Modifier.height(360.dp),
    )
}

@Composable
private fun SelectedCheck(selected: Boolean) {
    if (selected) {
        Text(
            text = stringResource(R.string.fa_check),
            fontFamily = com.github.damontecres.wholphin.ui.FontAwesome,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun categoryLabel(category: ProgramCategory): String =
    when (category) {
        ProgramCategory.SPORTS -> stringResource(R.string.sports)
        ProgramCategory.NEWS -> stringResource(R.string.live_tv_category_news)
        ProgramCategory.KIDS -> stringResource(R.string.live_tv_category_kids)
        ProgramCategory.MOVIE -> stringResource(R.string.live_tv_category_movies)
        ProgramCategory.FAKE -> ""
    }

/**
 * A remote-first programme guide toolbar.
 *
 * The previous guide spent 132dp on a focused-programme header and had no time controls. This is a
 * single 72dp band: the programme being read, its channel and time, then Now / Tonight / Tomorrow
 * and the zoom steps. The whole board below is schedule.
 */
@Composable
fun GuideToolbar(
    program: TvProgram?,
    channel: TvChannel?,
    now: LocalDateTime,
    zoomLabel: String,
    tabFocusRequester: FocusRequester,
    onNow: () -> Unit,
    onTonight: () -> Unit,
    onTomorrow: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onViewOptions: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val glass = LocalLiquidGlass.current
    val context = LocalContext.current
    // Every toolbar control targets the selected tab on Up. The schedule underneath no longer
    // gets to send focus to whichever tab happens to sit above the button's x coordinate.
    val upToTab = Modifier.focusProperties { up = tabFocusRequester }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth().height(56.dp),
    ) {
        if (program?.imageUrl != null) {
            AsyncImage(
                model = program.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(88.dp).fillMaxHeight().clip(RoundedCornerShape(9.dp)),
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .width(88.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (glass) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Text(
                    text = channel?.number ?: stringResource(R.string.fa_tv),
                    fontFamily = if (channel?.number == null) com.github.damontecres.wholphin.ui.FontAwesome else null,
                    style = if (glass) GlassType.sectionTitle else MaterialTheme.typography.titleMedium,
                    color = if (glass) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = program?.name ?: stringResource(R.string.tv_guide),
                style = if (glass) GlassType.sectionTitle else MaterialTheme.typography.titleMedium,
                color = if (glass) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val details =
                program?.let {
                    listOfNotNull(
                        channel?.number?.let { number -> "$number  ${channel.name.orEmpty()}" },
                        formatGuideWindow(context, program, now),
                    ).joinToString("  ·  ")
                } ?: stringResource(R.string.live_tv_guide_pick)
            Text(
                text = details,
                style = if (glass) GlassType.meta else MaterialTheme.typography.bodySmall,
                color = if (glass) Color.White.copy(alpha = 0.66f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(stringRes = R.string.live_tv_now, onClick = onNow, modifier = upToTab)
            TextButton(stringRes = R.string.live_tv_tonight, onClick = onTonight, modifier = upToTab)
            TextButton(stringRes = R.string.live_tv_tomorrow, onClick = onTomorrow, modifier = upToTab)
            ExpandableFaButton(
                title = R.string.live_tv_zoom_out,
                iconStringRes = R.string.fa_magnifying_glass_minus,
                onClick = onZoomOut,
                size = 36.dp,
                expandLabel = false,
                modifier = upToTab,
            )
            ExpandableFaButton(
                title = R.string.live_tv_zoom_in,
                iconStringRes = R.string.fa_magnifying_glass_plus,
                onClick = onZoomIn,
                size = 36.dp,
                expandLabel = false,
                modifier = upToTab,
            )
            Text(
                text = zoomLabel,
                style = if (glass) GlassType.meta else MaterialTheme.typography.labelSmall,
                color = if (glass) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ExpandableFaButton(
                title = R.string.view_options,
                iconStringRes = R.string.fa_sliders,
                onClick = onViewOptions,
                size = 36.dp,
                expandLabel = false,
                modifier = upToTab,
            )
        }
    }
}

private fun formatGuideWindow(
    context: android.content.Context,
    program: TvProgram,
    now: LocalDateTime,
): String {
    val start = android.text.format.DateUtils.formatDateTime(context, program.start.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), android.text.format.DateUtils.FORMAT_SHOW_TIME)
    val end = android.text.format.DateUtils.formatDateTime(context, program.end.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), android.text.format.DateUtils.FORMAT_SHOW_TIME)
    val state =
        when {
            program.isLiveAt(now) ->
                context.getString(R.string.live_tv_on_air, program.minutesLeftAt(now))
            program.start.isAfter(now) ->
                context.getString(R.string.live_tv_starts_in, Duration.between(now, program.start).toMinutes().coerceAtLeast(0))
            else -> context.getString(R.string.live_tv_finished)
        }
    return "$start–$end  ·  $state"
}

/**
 * Channel search. It searches the two things a tuner actually contains: channel identities and
 * programme titles. Results are ordered live first, because a viewer searching during a broadcast is
 * usually trying to get to what is on now.
 */
@Composable
fun LiveTvSearchDialog(
    channels: List<TvChannel>,
    currentPrograms: Map<UUID, TvProgram>,
    programs: List<TvProgram>,
    now: LocalDateTime,
    onTunes: (TvChannel) -> Unit,
    onProgramActions: (TvProgram) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { fieldFocus.tryRequestFocus() }
    val channelsById = remember(channels) { channels.associateBy { it.id } }
    val hits =
        remember(query, channels, currentPrograms, programs, now) {
            buildList {
                if (query.isBlank()) {
                    currentPrograms.values
                        .sortedBy { channelsById[it.channelId]?.number.orEmpty() }
                        .take(8)
                        .forEach { add(SearchHit(channelsById[it.channelId], it, true)) }
                } else {
                    channels
                        .filter {
                            it.name?.contains(query, ignoreCase = true) == true ||
                                channelNumberMatches(it, query)
                        }.forEach { channel ->
                            add(SearchHit(channel, currentPrograms[channel.id], currentPrograms[channel.id] != null))
                        }
                    programs
                        .asSequence()
                        .filterNot { it.isFake }
                        .filter {
                            it.name?.contains(query, ignoreCase = true) == true ||
                                it.subtitle?.contains(query, ignoreCase = true) == true
                        }.sortedWith(compareByDescending<TvProgram> { it.isLiveAt(now) }.thenBy { it.start })
                        .take(40)
                        .forEach { program ->
                            add(SearchHit(channelsById[program.channelId], program, program.isLiveAt(now)))
                        }
                }
            }.distinctBy { hit -> "${hit.channel?.id}:${hit.program?.id}:${hit.live}" }
                .filter { it.channel != null }
        }
    val resultFocus = remember { FocusRequester() }

    BasicDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.width(720.dp).heightIn(max = 470.dp).padding(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.live_tv_search_title),
                    style = if (LocalLiquidGlass.current) GlassType.sectionTitle else MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                if (hits.isNotEmpty()) {
                    TextButton(
                        stringRes = R.string.live_tv_results,
                        onClick = { resultFocus.tryRequestFocus() },
                    )
                }
            }
            SearchFieldRow(
                query = query,
                onQueryChange = { query = it },
                onSearch = { resultFocus.tryRequestFocus() },
                voiceInputManager = null,
                focusRequester = fieldFocus,
                glass = LocalLiquidGlass.current,
                showVoiceSearch = false,
                downFocusRequester = resultFocus,
                placeholder = stringResource(R.string.live_tv_search_hint),
                fillWidth = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (hits.isEmpty()) {
                Text(
                    text = if (query.isBlank()) stringResource(R.string.live_tv_search_prompt) else stringResource(R.string.no_results),
                    style = MaterialTheme.typography.bodyLarge,
                    color = copyColor(0.72f),
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize().focusRestorer(resultFocus),
                ) {
                    itemsIndexed(hits, key = { index, hit -> "${index}-${hit.channel?.id}-${hit.program?.id}" }) { index, hit ->
                        val channel = hit.channel ?: return@itemsIndexed
                        SearchResultRow(
                            hit = hit,
                            channel = channel,
                            now = now,
                            onClick = {
                                if (hit.program != null && hit.program.isLiveAt(now)) {
                                    onTunes(channel)
                                } else if (hit.program != null) {
                                    onProgramActions(hit.program)
                                } else {
                                    onTunes(channel)
                                }
                            },
                            modifier = if (index == 0) Modifier.focusRequester(resultFocus) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

private data class SearchHit(
    val channel: TvChannel?,
    val program: TvProgram?,
    val live: Boolean,
)

@Composable
private fun SearchResultRow(
    hit: SearchHit,
    channel: TvChannel,
    now: LocalDateTime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = {
            Text(
                text =
                    if (hit.program != null) {
                        hit.program.name.orEmpty()
                    } else {
                        channel.name.orEmpty()
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text =
                    listOfNotNull(
                        channel.number?.let { "#$it" },
                        channel.name.takeIf { hit.program != null },
                        hit.program?.let { if (it.isLiveAt(now)) stringResource(R.string.live_tv_live_now) else formatTimeForSearch(it.start) },
                    ).joinToString("  ·  "),
                maxLines = 1,
            )
        },
        leadingContent = {
            if (hit.program?.isLiveAt(now) == true) {
                Box(Modifier.size(8.dp).background(LiveRed, CircleShape))
            } else {
                Text(
                    text = stringResource(R.string.fa_tv),
                    fontFamily = com.github.damontecres.wholphin.ui.FontAwesome,
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

private fun formatTimeForSearch(time: LocalDateTime): String =
    time.format(DateTimeFormatter.ofPattern("EEE h:mm a", Locale.getDefault()))

/**
 * Numeric channel entry.
 *
 * Remotes still have digits, and a tuner with hundreds of channels cannot be crossed one D-pad
 * press at a time. The keypad is drawn large enough to read across a room, and the remote's own
 * number keys feed it without leaving the overlay.
 */
@Composable
fun ChannelNumberDialog(
    channels: List<TvChannel>,
    currentPrograms: Map<UUID, TvProgram>,
    initialDigits: String,
    onTune: (TvChannel) -> Unit,
    onDismiss: () -> Unit,
) {
    var digits by rememberSaveable { mutableStateOf(initialDigits.filter { it.isDigit() }) }
    val match = remember(digits, channels) { findChannelByNumber(channels, digits) }
    val matches = remember(digits, channels) {
        if (digits.isBlank()) emptyList() else channels.filter { channelNumberMatches(it, digits) }.take(4)
    }
    val firstDigitFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstDigitFocus.tryRequestFocus() }

    fun append(value: String) {
        if (digits.length < 6) digits += value
    }

    fun erase() {
        digits = digits.dropLast(1)
    }

    BasicDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            modifier =
                Modifier
                    .padding(24.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                        val value =
                            when (event.key) {
                                Key.Zero, Key.NumPad0 -> "0"
                                Key.One, Key.NumPad1 -> "1"
                                Key.Two, Key.NumPad2 -> "2"
                                Key.Three, Key.NumPad3 -> "3"
                                Key.Four, Key.NumPad4 -> "4"
                                Key.Five, Key.NumPad5 -> "5"
                                Key.Six, Key.NumPad6 -> "6"
                                Key.Seven, Key.NumPad7 -> "7"
                                Key.Eight, Key.NumPad8 -> "8"
                                Key.Nine, Key.NumPad9 -> "9"
                                else -> null
                            }
                        when {
                            value != null -> {
                                append(value)
                                true
                            }
                            event.key == Key.Backspace -> {
                                erase()
                                true
                            }
                            else -> false
                        }
                    },
        ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    // The old column used a weighted spacer against an unbounded dialog
                    // height. It stretched the whole sheet to 1080px, clipped its rounded
                    // corners, and pushed the digits and the Watch action to opposite edges
                    // of the television. A fixed height matches the 3x3 keypad, so the sheet
                    // hugs its content and stays inside the screen.
                    modifier = Modifier.width(230.dp).height(336.dp),
                ) {
                    Text(
                        text = stringResource(R.string.live_tv_channel_number),
                        style = if (LocalLiquidGlass.current) GlassType.sectionLabel else MaterialTheme.typography.labelMedium,
                        color = copyColor(0.65f),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = digits.ifBlank { "—" },
                        style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (digits.isBlank()) copyColor(0.3f) else copyColor(1f),
                    )
                    if (match != null) {
                        Text(
                            text = listOfNotNull(match.number, match.name).joinToString("  "),
                            style = MaterialTheme.typography.titleMedium,
                            color = copyColor(0.85f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        currentPrograms[match.id]?.name?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = copyColor(0.6f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else if (digits.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.live_tv_no_channel_number),
                            style = MaterialTheme.typography.bodyMedium,
                            color = copyColor(0.65f),
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.live_tv_channel_number_prompt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = copyColor(0.55f),
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { match?.let(onTune) },
                        enabled = match != null,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 10.dp),
                        contentHeight = 42.dp,
                    ) {
                        Text(stringResource(R.string.live_tv_watch_channel))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val numberRows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
                    numberRows.forEachIndexed { rowIndex, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEachIndexed { columnIndex, label ->
                                KeypadButton(
                                    label = label,
                                    onClick = { append(label) },
                                    modifier =
                                        if (rowIndex == 0 && columnIndex == 0) {
                                            Modifier.focusRequester(firstDigitFocus)
                                        } else {
                                            Modifier
                                        },
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KeypadButton(label = "⌫", onClick = { erase() })
                        KeypadButton(label = "0", onClick = { append("0") })
                        KeypadButton(label = "OK", onClick = { match?.let(onTune) }, enabled = match != null)
                    }
                    if (matches.size > 1) {
                        Text(
                            text = stringResource(R.string.live_tv_number_matches, matches.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = copyColor(0.6f),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
        }
    }
}

@Composable
private fun KeypadButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(78.dp),
        contentPadding = PaddingValues(0.dp),
        contentHeight = 78.dp,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** The details sheet for a channel itself, rather than a programme. */
@Composable
fun ChannelInfoDialog(
    channel: TvChannel,
    current: TvProgram?,
    next: TvProgram?,
    isFavorite: Boolean,
    onWatch: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenGuide: () -> Unit,
    onDismiss: () -> Unit,
) {
    val actions =
        buildList<DialogItemEntry> {
            add(
                DialogItem(
                    headlineContent = { Text(stringResource(R.string.watch_live)) },
                    leadingContent = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    onClick = onWatch,
                ),
            )
            add(
                DialogItem(
                    headlineContent = {
                        Text(stringResource(if (isFavorite) R.string.remove_favorite else R.string.add_favorite))
                    },
                    selected = isFavorite,
                    onClick = onToggleFavorite,
                ),
            )
            add(
                DialogItem(
                    headlineContent = { Text(stringResource(R.string.live_tv_open_in_guide)) },
                    onClick = onOpenGuide,
                ),
            )
            if (current != null) {
                add(DialogItemDivider)
                add(
                    DialogItem(
                        headlineContent = { Text(stringResource(R.string.live_tv_on_now_label, current.name.orEmpty())) },
                        supportingContent = { Text(current.quickDetails) },
                        enabled = false,
                        onClick = {},
                    ),
                )
            }
            if (next != null) {
                add(
                    DialogItem(
                        headlineContent = { Text(stringResource(R.string.live_tv_next_label, next.name.orEmpty())) },
                        supportingContent = { Text(next.quickDetails) },
                        enabled = false,
                        onClick = {},
                    ),
                )
            }
            add(
                DialogItem(
                    headlineContent = { Text(stringResource(R.string.close)) },
                    onClick = onDismiss,
                ),
            )
        }
    DialogPopup(
        params =
            DialogParams(
                false,
                listOfNotNull(channel.number, channel.name).joinToString("  ·  "),
                actions,
            ),
        dismissOnClick = false,
        onDismissRequest = onDismiss,
    )
}
