package com.github.damontecres.wholphin.ui.detail.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.formatSeasonEpisode
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import java.time.LocalDateTime

@Composable
fun Program(
    guideStart: LocalDateTime,
    program: TvProgram,
    now: LocalDateTime,
    colorCode: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    cellHeight: Dp = GuideCellHeight,
) {
    val startedBeforeGuide = program.start.isBefore(guideStart)
    val shape =
        remember(startedBeforeGuide) {
            val cornerSize = 8.dp
            if (startedBeforeGuide) {
                RoundedCornerShape(
                    topEnd = cornerSize,
                    bottomEnd = cornerSize,
                    topStart = 0.dp,
                    bottomStart = 0.dp,
                )
            } else {
                RoundedCornerShape(cornerSize)
            }
        }
    val title = program.name ?: program.id.toString()
    val glass = LocalLiquidGlass.current
    // A filler cell is a gap in the schedule, not a programme: the tuner has one listing per
    // fixture, so a 48-hour grid is mostly gap. Drawing "No data" in every one of those turned a
    // guide of twelve games into a wall of identical words. The cell keeps its slot — the grid is
    // a calendar — but draws as quiet space, so the games are what the eye lands on.
    val filler = program.isFake
    // A guide cell is a tile on the guide's slate rather than a pane of glass: the board is dark,
    // so a cell is a slight lift of white light on it and a category colour is a muted wash rather
    // than a saturated card. The focus signal is the app-wide white fill with dark glyphs.
    val category = if (colorCode) program.category?.color else null
    val onAir = !filler && !program.start.isAfter(now) && now.isBefore(program.end)
    val containerColor =
        when {
            filler && glass -> GuideSlate.filler
            filler -> MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp).copy(alpha = 0.4f)
            // A category is a hint on a slate, not a fill. At a third opacity the tuner's red
            // washed the whole schedule maroon and the board stopped reading as a timetable;
            // a low wash leaves the title white and the category recognisable, and the live red
            // rule is still the only saturated mark on the page.
            glass && category != null -> category.copy(alpha = if (onAir) 0.22f else 0.14f)
            glass -> if (onAir) GuideSlate.cellOnAir else GuideSlate.cell
            category != null -> category
            else -> MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(1f, 1f, .95f),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = containerColor,
                contentColor = if (glass) Color.White.copy(alpha = 0.94f) else MaterialTheme.colorScheme.onSurface,
                // A gap does not flash white on focus: an empty white slab reads as a cell that
                // failed to load. It lifts just enough to show the D-pad is on a real slot.
                focusedContainerColor =
                    when {
                        filler && glass -> Color.White.copy(alpha = 0.06f)
                        filler -> MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp).copy(alpha = 0.6f)
                        glass -> GuideSlate.focusFill
                        else -> MaterialTheme.colorScheme.inverseSurface
                    },
                focusedContentColor = if (glass) Color.Black else MaterialTheme.colorScheme.inverseOnSurface,
            ),
        modifier =
            modifier
                // A gap is not a destination. It has its slot so the calendar stays aligned, but
                // a D-pad that stops on empty hours only teaches the viewer to press past them;
                // skipping gaps means every press lands on something that can be watched.
                .focusProperties { canFocus = !filler }
                // A gap still rules its row. Empty hours are most of a sparse guide, and twelve
                // identical empty cells read as a page that failed to draw; a hairline turns them
                // into a timetable that happens to have nothing on. It is drawn before the cell's
                // 2dp inset so the segments join up across hours.
                .then(
                    if (filler && glass) {
                        Modifier.bottomHairline(Color.White.copy(alpha = 0.05f))
                    } else {
                        Modifier
                    },
                ).padding(2.dp)
                // Height is explicit: the guide measures a programme against an unbounded
                // vertical constraint, so `fillMaxSize` collapses to the text and every cell
                // floats as a short card in the top of its row. A fixed height makes the tile
                // own its slot, which is what puts the red live rule down the whole cell.
                .fillMaxWidth()
                .height(cellHeight),
    ) {
        if (filler) {
            // Nothing to say, and nothing to press: the cell exists to keep the columns aligned.
            Spacer(Modifier.fillMaxSize())
            return@Surface
        }
        Row(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (startedBeforeGuide) {
                Text(
                    text = stringResource(R.string.fa_caret_left),
                    fontFamily = FontAwesome,
                    color = LocalContentColor.current,
                    fontSize = 16.sp,
                    modifier =
                        Modifier
                            .align(Alignment.CenterVertically)
                            .padding(start = 2.dp),
                )
            }
            Column(
                // Centred rather than top-aligned: a programme with no subtitle is one line in a
                // 60dp tile, and pinned to the top it reads as a cell that lost its second line.
                verticalArrangement = Arrangement.Center,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            ) {
                Text(
                    text = title,
                    color = LocalContentColor.current,
                    fontSize = 15.sp,
                    fontWeight = if (onAir) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier,
                )
                val seasonEpisodeLabel =
                    formatSeasonEpisode(
                        program.seasonEpisode?.season,
                        program.seasonEpisode?.episode,
                    )
                val subtitle =
                    remember(program, seasonEpisodeLabel) {
                        listOfNotNull(
                            seasonEpisodeLabel,
                            program.subtitle,
                        ).joinToString(" - ").ifBlank { null }
                    }
                subtitle?.let {
                    Text(
                        text = it,
                        // The subtitle is a second rank of information, so it carries less ink
                        // than the title. At equal colour the two lines read as one run-on
                        // sentence at ten feet.
                        color = LocalContentColor.current.copy(alpha = 0.66f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier,
                    )
                }
            }
        }
        // "On now" is a red rule down the cell's leading edge, the same mark the sports page
        // uses. It lives inside the tile rather than under it: a rule below the card reads as a
        // selection indicator, and a guide already uses white fill for that.
        if (onAir && !filler) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(LiveRed.copy(alpha = 0.92f)),
            )
        }
        RecordingMarker(
            isRecording = program.isRecording,
            isSeriesRecording = program.isSeriesRecording,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
fun Channel(
    channel: TvChannel,
    channelIndex: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LocalLiquidGlass.current
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(0.dp)),
        scale = ClickableSurfaceDefaults.scale(1f, 1f, .97f),
        colors =
            ClickableSurfaceDefaults.colors(
                // The channel column is a *column*, not a stack of cards: it is the same slate as
                // the schedule, closed off by a divider. Giving every channel its own tinted tile
                // turned the column into a striped barcode next to the programme cells.
                containerColor =
                    if (glass) {
                        GuideSlate.column
                    } else {
                        MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                    },
                contentColor = if (glass) Color.White.copy(alpha = 0.92f) else LocalContentColor.current,
                focusedContainerColor = if (glass) Color.White else MaterialTheme.colorScheme.inverseSurface,
                focusedContentColor = if (glass) Color.Black else LocalContentColor.current,
            ),
        modifier =
            modifier
                // The column sits on the guide's slate, so it must not paint the page's opaque
                // backdrop behind itself; the separators belong to the board, not to black.
                .background(if (glass) Color.Transparent else MaterialTheme.colorScheme.background)
                .then(if (glass) Modifier.bottomHairline().endHairline() else Modifier)
                .fillMaxSize(),
    ) {
        Box(
            modifier =
                Modifier
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                    .fillMaxSize(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    channel.number?.let {
                        Text(
                            text = it,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LocalContentColor.current.copy(alpha = 0.55f),
                            maxLines = 1,
                        )
                    }
                    var showImage by remember { mutableStateOf(true) }
                    // A small logo beside the number rather than a large one above the name: the
                    // name is what a viewer reads to pick a channel, and 120dp of column cannot
                    // afford both at a legible size.
                    if (showImage && channel.imageUrl != null) {
                        AsyncImage(
                            model = channel.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier =
                                Modifier
                                    .height(14.dp)
                                    .widthIn(max = 34.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                            onError = { showImage = false },
                        )
                    }
                    if (channel.favorite) {
                        Text(
                            color = colorResource(android.R.color.holo_red_light),
                            text = stringResource(R.string.fa_heart),
                            fontSize = 10.sp,
                            fontFamily = FontAwesome,
                        )
                    }
                }
                channel.name?.let {
                    Text(
                        text = it,
                        // Two lines: a sports tuner's channel name *is* the fixture, and one
                        // line sliced it to "Washington Comm…", which names nothing.
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
