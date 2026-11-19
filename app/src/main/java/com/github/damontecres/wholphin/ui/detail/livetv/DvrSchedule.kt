package com.github.damontecres.wholphin.ui.detail.livetv

import android.text.format.DateUtils
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.cards.glassCardBorder
import com.github.damontecres.wholphin.ui.cards.glassCardGlow
import com.github.damontecres.wholphin.ui.cards.glassCardScale
import com.github.damontecres.wholphin.ui.cards.glassCardShape
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.TextButton
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.seasonEpisode
import com.github.damontecres.wholphin.ui.theme.GlassType
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.SeriesTimerInfoDto
import timber.log.Timber
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class DvrScheduleViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        val navigationManager: NavigationManager,
        private val imageUrlService: ImageUrlService,
    ) : ViewModel() {
        private fun BaseItem.withImage(): BaseItem =
            copy(
                imageUrlOverride =
                    imageUrlService.getItemImageUrl(
                        itemId = id,
                        imageType = ImageType.PRIMARY,
                        tag = data.imageTags?.get(ImageType.PRIMARY),
                    ),
            )

        private val _state = MutableStateFlow(DvrScheduleState())
        val state: StateFlow<DvrScheduleState> = _state

        init {
            init()
        }

        fun init() {
            viewModelScope.launchIO {
                try {
                    val active =
                        api.liveTvApi
                            .getRecordings(
                                isInProgress = true,
                                fields = SlimItemFields,
                                limit = 100,
                            ).content.items
                            .map { BaseItem.from(it, api, true).withImage() }
                    val scheduled =
                        api.liveTvApi
                            .getTimers(
                                isActive = false,
                                isScheduled = true,
                            ).content.items
                            .mapNotNull { timer ->
                                timer.programInfo?.let { program ->
                                    val item = BaseItem.from(program, api, true).withImage()
                                    // TimerInfoDto's programme info is a plain item: without these
                                    // ids the row's dialog cannot tell that it is already
                                    // scheduled and offers a Record action that does nothing.
                                    item.copy(
                                        data =
                                            item.data.copy(
                                                timerId = timer.id,
                                                seriesTimerId = timer.seriesTimerId,
                                            ),
                                    )
                                }
                            }.sortedBy { it.data.startDate }
                            .groupBy { it.data.startDate?.toLocalDate() ?: LocalDate.now() }
                    val series =
                        try {
                            api.liveTvApi.getSeriesTimers().content.items
                        } catch (ex: Exception) {
                            Timber.w(ex, "Could not load series timers")
                            emptyList()
                        }
                    val folders =
                        try {
                            api.liveTvApi.getRecordingFolders().content.items
                                .map { BaseItem.from(it, api, true) }
                        } catch (ex: Exception) {
                            Timber.w(ex, "Could not load recording folders")
                            emptyList()
                        }

                    _state.update {
                        it.copy(
                            loading = LoadingState.Success,
                            active = active,
                            scheduled = scheduled,
                            seriesTimers = series,
                            folders = folders,
                        )
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching DVR schedule")
                    _state.update { it.copy(loading = LoadingState.Error(ex)) }
                }
            }
        }

        fun cancelRecording(
            timerId: String,
            series: Boolean,
        ) {
            viewModelScope.launchIO(ExceptionHandler(autoToast = true)) {
                if (series) {
                    api.liveTvApi.cancelSeriesTimer(timerId)
                } else {
                    api.liveTvApi.cancelTimer(timerId)
                }
                init()
            }
        }

        fun openFolder(folder: BaseItem) {
            navigationManager.navigateTo(Destination.Recordings(folder.id))
        }
    }

data class DvrScheduleState(
    val loading: LoadingState = LoadingState.Loading,
    val active: List<BaseItem> = emptyList(),
    val scheduled: Map<LocalDate, List<BaseItem>> = emptyMap(),
    val seriesTimers: List<SeriesTimerInfoDto> = emptyList(),
    val folders: List<BaseItem> = emptyList(),
)

/**
 * The DVR cockpit.
 *
 * A recording list is not a schedule: the viewer needs to know what is writing now, how far through
 * it is, what is queued next, and where the files live. Those are four different questions, so they
 * are four different bands on one page instead of a single undifferentiated list.
 */
@Composable
fun DvrSchedule(
    requestFocusAfterLoading: Boolean,
    tabFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onOpenGuide: () -> Unit = {},
    viewModel: DvrScheduleViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) {
        viewModel.init()
    }
    val state by viewModel.state.collectAsState()
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
            var showDialog by remember { mutableStateOf<BaseItem?>(null) }
            val focusRequester = remember { FocusRequester() }
            if (requestFocusAfterLoading) {
                LaunchedEffect(Unit) {
                    // The first thing worth acting on is always inside this page: the first
                    // scheduled row, the first folder, or the empty state's Open guide button.
                    // A previous version parked focus on the DVR tab, so the D-pad could not
                    // enter the page it had just opened.
                    focusRequester.tryRequestFocus()
                }
            }
            DvrScheduleContent(
                activeRecordings = state.active,
                scheduledRecordings = state.scheduled,
                seriesTimers = state.seriesTimers,
                folders = state.folders,
                onClickItem = { showDialog = it },
                onOpenGuide = onOpenGuide,
                onOpenFolder = viewModel::openFolder,
                tabFocusRequester = tabFocusRequester,
                focusRequester = focusRequester,
                modifier = modifier,
            )
            showDialog?.let { item ->
                val context = LocalContext.current
                val channelName = item.data.channelName
                ProgramDialog(
                    state = DataLoadingState.Success(item),
                    canRecord = item.data.timerId != null || item.data.seriesTimerId != null,
                    channelName = channelName,
                    onDismissRequest = {
                        showDialog = null
                    },
                    onWatch = { program ->
                        val channelId = program.data.channelId
                        if (channelId != null) {
                            viewModel.navigationManager.navigateTo(
                                Destination.Playback(
                                    itemId = channelId,
                                    positionMs = 0L,
                                ),
                            )
                        } else {
                            android.widget.Toast
                                .makeText(context, "Program has no channel ID", android.widget.Toast.LENGTH_LONG)
                                .show()
                        }
                    },
                    onRecord = { _, _ -> },
                    onCancelRecord = { program, series ->
                        showDialog = null
                        val timerId =
                            if (series) program.data.seriesTimerId else program.data.timerId
                        if (timerId != null) {
                            viewModel.cancelRecording(timerId, series)
                        }
                    },
                    onOpenGuide = {
                        showDialog = null
                        onOpenGuide()
                    },
                )
            }
        }
    }
}

@Composable
fun DvrScheduleContent(
    activeRecordings: List<BaseItem>,
    scheduledRecordings: Map<LocalDate, List<BaseItem>>,
    seriesTimers: List<SeriesTimerInfoDto>,
    folders: List<BaseItem>,
    onClickItem: (BaseItem) -> Unit,
    onOpenGuide: () -> Unit,
    onOpenFolder: (BaseItem) -> Unit,
    tabFocusRequester: FocusRequester,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val now = rememberTickingNow()
    val scheduledCount = scheduledRecordings.values.sumOf { it.size }
    val conflicts =
        remember(scheduledRecordings, activeRecordings) {
            conflictedProgramIds(
                (scheduledRecordings.values.flatten() + activeRecordings).mapNotNull { item ->
                    val start = item.data.startDate ?: return@mapNotNull null
                    val end = item.data.endDate ?: return@mapNotNull null
                    item.id to (start to end)
                },
            )
        }
    val firstScheduledId =
        scheduledRecordings
            .toSortedMap()
            .values
            .flatten()
            .firstOrNull()
            ?.id

    LazyColumn(
        contentPadding = PaddingValues(start = pageStartPadding(), end = 24.dp, top = 2.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item(key = "summary") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
            ) {
                Text(
                    text = stringResource(R.string.live_tv_dvr),
                    style = if (LocalLiquidGlass.current) GlassType.pageTitle else MaterialTheme.typography.headlineSmall,
                    color = copyColor(1f),
                )
                Text(
                    text = stringResource(R.string.live_tv_dvr_summary, activeRecordings.size, scheduledCount),
                    style = if (LocalLiquidGlass.current) GlassType.meta else MaterialTheme.typography.bodyMedium,
                    color = copyColor(0.62f),
                )
                if (conflicts.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier =
                            Modifier
                                .background(LiveRed.copy(alpha = 0.16f), RoundedCornerShape(percent = 50))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.fa_xmark),
                            fontFamily = FontAwesome,
                            fontSize = 11.sp,
                            color = LiveRed,
                        )
                        Text(
                            text = stringResource(R.string.live_tv_dvr_conflicts, conflicts.size),
                            style = if (LocalLiquidGlass.current) GlassType.meta else MaterialTheme.typography.labelSmall,
                            color = copyColor(0.9f),
                        )
                    }
                }
                if (seriesTimers.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.live_tv_dvr_series_rules, seriesTimers.size),
                        style = if (LocalLiquidGlass.current) GlassType.meta else MaterialTheme.typography.bodySmall,
                        color = copyColor(0.5f),
                    )
                }
                Spacer(Modifier.weight(1f))
                // The empty state already carries a clear call to action; repeating it in the
                // header gave the D-pad two identical destinations and the page two primaries.
                if (activeRecordings.isNotEmpty() || scheduledCount > 0 || folders.isNotEmpty()) {
                    TextButton(
                        stringRes = R.string.live_tv_open_guide,
                        onClick = onOpenGuide,
                    )
                }
            }
        }

        if (activeRecordings.isEmpty() && scheduledCount == 0) {
            item(key = "empty") {
                DvrEmpty(
                    onOpenGuide = onOpenGuide,
                    tabFocusRequester = tabFocusRequester,
                    focusRequester = focusRequester,
                )
            }
        }

        if (activeRecordings.isNotEmpty()) {
            item(key = "active_header") {
                DvrSectionTitle(
                    title = stringResource(R.string.live_tv_dvr_recording_now),
                    subtitle = stringResource(R.string.live_tv_dvr_recording_now_count, activeRecordings.size),
                )
            }
            item(key = "active_row") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    itemsIndexed(activeRecordings, key = { _, item -> item.id }) { index, item ->
                        ActiveRecordingCard(
                            item = item,
                            now = now,
                            onClick = { onClickItem(item) },
                            onLongClick = { onClickItem(item) },
                            modifier =
                                Modifier
                                    .then(
                                        if (index == 0) {
                                            Modifier.focusRequester(focusRequester)
                                        } else {
                                            Modifier
                                        },
                                    ).focusProperties { up = tabFocusRequester },
                        )
                    }
                }
            }
        }

        scheduledRecordings.keys.sorted().forEach { date ->
            val programs = scheduledRecordings[date].orEmpty()
            if (programs.isNotEmpty()) {
                item(key = "date_$date") {
                    DvrSectionTitle(
                        title = formatDvrDate(context, date, now.toLocalDate()),
                        subtitle = stringResource(R.string.live_tv_dvr_scheduled_count, programs.size),
                    )
                }
                items(programs, key = { it.id }) { item ->
                    ScheduledRecordingRow(
                        item = item,
                        now = now,
                        conflict = item.id in conflicts,
                        onClick = { onClickItem(item) },
                        modifier =
                            Modifier
                                .then(
                                    if (activeRecordings.isEmpty() && item.id == firstScheduledId) {
                                        Modifier.focusRequester(focusRequester)
                                    } else {
                                        Modifier
                                    },
                                ).focusProperties { up = tabFocusRequester },
                    )
                }
            }
        }

        if (folders.isNotEmpty()) {
            item(key = "folders_header") {
                DvrSectionTitle(
                    title = stringResource(R.string.live_tv_dvr_folders),
                    subtitle = stringResource(R.string.live_tv_dvr_folders_subtitle),
                )
            }
            item(key = "folders") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(folders, key = { _, folder -> folder.id }) { index, folder ->
                        FolderCard(
                            folder = folder,
                            onClick = { onOpenFolder(folder) },
                            modifier =
                                Modifier
                                    .then(
                                        if (activeRecordings.isEmpty() && scheduledCount == 0 && index == 0) {
                                            Modifier.focusRequester(focusRequester)
                                        } else {
                                            Modifier
                                        },
                                    ).focusProperties { up = tabFocusRequester },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DvrEmpty(
    onOpenGuide: () -> Unit,
    tabFocusRequester: FocusRequester,
    focusRequester: FocusRequester,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            Modifier
                .fillMaxWidth(0.66f)
                .padding(vertical = 32.dp),
    ) {
        Text(
            text = stringResource(R.string.live_tv_dvr_empty_title),
            style = if (LocalLiquidGlass.current) GlassType.sectionTitle else MaterialTheme.typography.headlineSmall,
            color = copyColor(1f),
        )
        Text(
            text = stringResource(R.string.live_tv_dvr_empty_message),
            style = MaterialTheme.typography.bodyLarge,
            color = copyColor(0.72f),
        )
        TextButton(
            stringRes = R.string.live_tv_open_guide,
            onClick = onOpenGuide,
            modifier =
                Modifier
                    .focusRequester(focusRequester)
                    .focusProperties { up = tabFocusRequester },
        )
    }
}

@Composable
private fun DvrSectionTitle(
    title: String,
    subtitle: String,
) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    ) {
        Text(
            text = title,
            style = if (LocalLiquidGlass.current) GlassType.rowTitle else MaterialTheme.typography.titleMedium,
            color = copyColor(1f),
        )
        Text(
            text = subtitle,
            style = if (LocalLiquidGlass.current) GlassType.meta else MaterialTheme.typography.bodySmall,
            color = copyColor(0.5f),
        )
    }
}

@Composable
private fun ActiveRecordingCard(
    item: BaseItem,
    now: LocalDateTime,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val start = item.data.startDate ?: now
    val end = item.data.endDate ?: start
    val total = Duration.between(start, end).toMinutes().coerceAtLeast(1L)
    val elapsed = Duration.between(start, now).toMinutes().coerceIn(0L, total)
    val progress = elapsed.toFloat() / total.toFloat()
    val remaining = Duration.between(now, end).toMinutes().coerceAtLeast(0L)
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.width(370.dp).height(176.dp),
        shape = glassCardShape(),
        border = glassCardBorder(),
        glow = glassCardGlow(),
        scale = glassCardScale(),
        colors = CardDefaults.colors(containerColor = Color.Transparent),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(Color.Black.copy(alpha = 0.38f), Color.Black.copy(alpha = 0.78f)),
                    ),
                ),
        ) {
            item.imageUrlOverride?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.58f,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.size(8.dp).background(LiveRed, CircleShape))
                    Text(
                        text = stringResource(R.string.live_tv_dvr_recording),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                    item.data.channelName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = item.title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(2.dp)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(LiveRed, RoundedCornerShape(2.dp)),
                    )
                }
                Text(
                    text = stringResource(R.string.live_tv_dvr_min_left, remaining),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun ScheduledRecordingRow(
    item: BaseItem,
    now: LocalDateTime,
    conflict: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val glass = LocalLiquidGlass.current
    val start = item.data.startDate ?: now
    val end = item.data.endDate ?: start
    val time =
        DateUtils.formatDateRange(
            context,
            start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            DateUtils.FORMAT_SHOW_TIME,
        )
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(1f, 1f, 0.99f),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (glass) Color.White.copy(alpha = 0.055f) else MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                contentColor = copyColor(1f),
                focusedContainerColor = if (glass) Color.White else MaterialTheme.colorScheme.inverseSurface,
                focusedContentColor = if (glass) Color.Black else MaterialTheme.colorScheme.inverseOnSurface,
            ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(78.dp).padding(horizontal = 14.dp),
        ) {
            val parts = time.split("–", "-", limit = 2)
            Column(modifier = Modifier.width(88.dp)) {
                Text(
                    text = parts.firstOrNull().orEmpty().trim(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = parts.getOrNull(1).orEmpty().trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.55f),
                    maxLines = 1,
                )
            }
            Box(
                modifier =
                    Modifier
                        .size(width = 70.dp, height = 42.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = if (glass) 0.2f else 0.07f)),
                contentAlignment = Alignment.Center,
            ) {
                if (item.imageUrlOverride != null) {
                    AsyncImage(
                        model = item.imageUrlOverride,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.fa_tv),
                        fontFamily = FontAwesome,
                        color = LocalContentColor.current.copy(alpha = 0.65f),
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = item.title.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (item.data.isSeries == true) {
                        Text(
                            text = stringResource(R.string.live_tv_repeat),
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalContentColor.current.copy(alpha = 0.5f),
                        )
                    }
                    if (conflict) {
                        Text(
                            text = stringResource(R.string.fa_xmark),
                            fontFamily = FontAwesome,
                            color = LiveRed,
                            fontSize = 11.sp,
                        )
                    }
                }
                Text(
                    text = listOfNotNull(item.data.channelName, item.data.seasonEpisode).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (conflict) {
                Text(
                    text = stringResource(R.string.live_tv_dvr_conflict),
                    style = MaterialTheme.typography.labelSmall,
                    color = LiveRed,
                )
            }
        }
    }
}

@Composable
private fun FolderCard(
    folder: BaseItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.width(220.dp).height(82.dp),
        shape = glassCardShape(),
        border = glassCardBorder(),
        glow = glassCardGlow(),
        scale = glassCardScale(),
        colors = CardDefaults.colors(containerColor = Color.Transparent),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
                    .padding(12.dp),
        ) {
            Text(
                text = stringResource(R.string.fa_hard_drive),
                fontFamily = FontAwesome,
                color = Color.White.copy(alpha = 0.7f),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = folder.name.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                folder.data.childCount?.let {
                    Text(
                        text = stringResource(R.string.live_tv_dvr_folder_count, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                }
            }
        }
    }
}

private fun formatDvrDate(
    context: android.content.Context,
    date: LocalDate,
    today: LocalDate,
): String =
    when (date) {
        today -> context.getString(R.string.live_tv_dvr_today)
        today.plusDays(1) -> context.getString(R.string.live_tv_dvr_tomorrow)
        else ->
            DateUtils.formatDateTime(
                context,
                date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE,
            )
    }
