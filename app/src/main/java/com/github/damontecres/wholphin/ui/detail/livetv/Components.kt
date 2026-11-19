package com.github.damontecres.wholphin.ui.detail.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import java.time.LocalDateTime

/**
 * One programme block in the guide.
 *
 * The reference listings page is deliberately flat: a dark rectangle, one line of white title,
 * and a white fill for the focused programme. There is no card in it, no subtitle and no red
 * live rule; the block that is airing is simply a shade lighter. The still of a programme that
 * began before the visible window sits at the leading edge, as it does in the reference, so the
 * current row reads as a broadcast rather than as a column of filenames.
 */
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
    val title =
        buildString {
            val base = program.name ?: program.id.toString()
            append(base)
            // Some XMLTV providers already write the year into the title. Appending it again
            // gives the tell-tale "(2008) (2008)" of a guide that does not read its own data.
            program.productionYear
                ?.takeUnless { base.contains("($it)") }
                ?.let { append(" ($it)") }
        }
    val filler = program.isFake
    val glass = LocalLiquidGlass.current
    val category = if (colorCode) program.category?.color else null
    val onAir = !filler && !program.start.isAfter(now) && now.isBefore(program.end)
    val containerColor =
        when {
            filler -> Color.Transparent
            glass && category != null -> category.copy(alpha = if (onAir) 0.22f else 0.14f)
            glass && onAir -> GuideEpg.cellOnAir
            glass && program.start.isAfter(now) -> GuideEpg.cellFuture
            glass -> GuideEpg.cell
            else -> MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(1.dp)),
        scale = ClickableSurfaceDefaults.scale(1f, 1f, 1f),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = containerColor,
                contentColor = if (glass) GuideEpg.text else MaterialTheme.colorScheme.onSurface,
                // A gap does not flash white on focus; it lifts just enough to show the D-pad is
                // on a real slot without pretending there is a programme there.
                focusedContainerColor =
                    when {
                        filler && glass -> Color.White.copy(alpha = 0.05f)
                        glass -> GuideEpg.focus
                        else -> MaterialTheme.colorScheme.inverseSurface
                    },
                focusedContentColor = if (glass) GuideEpg.focusInk else MaterialTheme.colorScheme.inverseOnSurface,
            ),
        modifier =
            modifier
                // A gap is not a destination. It has its slot so the calendar stays aligned, but
                // skipping it means every press lands on something that can be watched.
                .focusProperties { canFocus = !filler }
                .padding(1.dp)
                // Height is explicit: the guide measures a programme against an unbounded
                // vertical constraint. A fixed height makes the block own its slot.
                .fillMaxWidth()
                .height(cellHeight),
    ) {
        if (filler) {
            Spacer(Modifier.fillMaxSize())
            return@Surface
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // A thin block cannot carry a still and its title; in that case the title wins.
            val showLeadingArt =
                startedBeforeGuide && !program.imageUrl.isNullOrBlank() && maxWidth >= 150.dp
            Row(Modifier.fillMaxSize()) {
                if (showLeadingArt) {
                    AsyncImage(
                        model = program.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.width(58.dp).fillMaxHeight(),
                    )
                }
                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 6.dp),
                ) {
                    Text(
                        text = title,
                        color = LocalContentColor.current,
                        fontSize = 14.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        RecordingMarker(
            isRecording = program.isRecording,
            isSeriesRecording = program.isSeriesRecording,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

/**
 * The channel identity block: number, then the tuner's logo.
 *
 * The reference column has no second line of prose. A logo is the recognisable part of a channel
 * at ten feet, and the number is the part that works when a tuner publishes no art. Keeping both
 * in one 44dp row gives the schedule back the width it needs.
 */
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
        scale = ClickableSurfaceDefaults.scale(1f, 1f, 1f),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (glass) GuideEpg.channel else MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                contentColor = if (glass) GuideEpg.text else LocalContentColor.current,
                focusedContainerColor = if (glass) GuideEpg.focus else MaterialTheme.colorScheme.inverseSurface,
                focusedContentColor = if (glass) GuideEpg.focusInk else LocalContentColor.current,
            ),
        modifier =
            modifier
                .background(if (glass) GuideEpg.channel else MaterialTheme.colorScheme.background)
                .then(
                    if (glass) {
                        Modifier
                            .bottomHairline(GuideEpg.divider.copy(alpha = 0.55f))
                            .endHairline(GuideEpg.divider)
                    } else {
                        Modifier
                    },
                ).fillMaxSize(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = channel.number ?: (channelIndex + 1).toString(),
                fontSize = 14.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = LocalContentColor.current.copy(alpha = 0.72f),
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.width(38.dp),
            )
            Spacer(Modifier.width(8.dp))
            var showImage by remember { mutableStateOf(true) }
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (showImage && channel.imageUrl != null) {
                    AsyncImage(
                        model = channel.imageUrl,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(vertical = 5.dp),
                        onError = { showImage = false },
                    )
                } else {
                    Text(
                        text = channel.name.orEmpty(),
                        fontSize = 12.sp,
                        lineHeight = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = LocalContentColor.current.copy(alpha = 0.88f),
                    )
                }
            }
            if (channel.favorite) {
                Text(
                    color = colorResource(android.R.color.holo_red_light),
                    text = stringResource(R.string.fa_heart),
                    fontSize = 9.sp,
                    fontFamily = FontAwesome,
                    modifier = Modifier.padding(start = 3.dp),
                )
            }
        }
    }
}
