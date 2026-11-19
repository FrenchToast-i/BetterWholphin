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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val epgTime: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

/**
 * The listing header from the reference EPG: the focused programme's title, year, rating,
 * description and still on the left, the GUIDE hint and the active filters in the middle, and the
 * programme image on the right.
 *
 * It is deliberately plain black. The guide below is a listings page, not a glass control, and
 * this header is its masthead; a pane here would put a bright sheet above a bright grid. The one
 * accent is the same blue the filter control uses.
 */
@Composable
fun GuideInfoHeader(
    program: TvProgram?,
    channel: TvChannel?,
    filters: LiveTvFilters,
    now: LocalDateTime,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier,
    filterFocusRequester: FocusRequester? = null,
) {
    val programCategory =
        program?.category
            ?.let { categoryLabel(it) }
            ?.takeUnless { it.isBlank() }
    // The reference masthead's eyebrow is the programme's genre ("Comedy"), while the category
    // stays at the end of the metadata line ("Movies"). A tuner that only supplies flags has no
    // genre, so fall back to the category and suppress the now-duplicate metadata entry below.
    val category =
        program?.genres
            ?.firstOrNull { it.isNotBlank() && !it.equals("sports", ignoreCase = true) }
            ?.replaceFirstChar { it.titlecase(Locale.getDefault()) }
            ?: programCategory
            ?: stringResource(R.string.live_tv)
    val title =
        program?.name?.takeIf { it.isNotBlank() }
            ?: channel?.name?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.tv_guide)
    val meta =
        buildList {
            program?.let {
                add("${it.start.format(epgTime)} - ${it.end.format(epgTime)}")
                if (it.isLiveAt(now)) add("${(it.progressAt(now) * 100).roundToInt()}%")
                it.productionYear?.let { year -> add(year.toString()) }
                it.officialRating?.takeIf { rating -> rating.isNotBlank() }?.let(::add)
                if (it.isRepeat) add("\u21bb")
            }
            programCategory
                ?.takeUnless { it.equals(category, ignoreCase = true) }
                ?.let(::add)
        }.joinToString("  |  ")
    val activeFilters =
        buildList {
            if (filters.favoritesOnly) add(stringResource(R.string.favorites))
            if (filters.liveOnly) add(stringResource(R.string.live_tv_filter_live))
            if (filters.sportsOnly) add(stringResource(R.string.sports))
            if (filters.hasGuideOnly) add(stringResource(R.string.live_tv_filter_has_guide))
            filters.category?.let { add(categoryLabel(it)) }
        }.joinToString(", ").ifBlank { stringResource(R.string.live_tv_filter_none) }
    val overview =
        program?.overview?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.live_tv_guide_prompt)

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
        modifier =
            modifier
                .fillMaxWidth()
                .height(104.dp)
                .background(GuideEpg.background)
                .padding(start = 12.dp, end = 12.dp, top = 1.dp, bottom = 3.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(1.dp),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            Text(
                text = category,
                color = GuideEpg.text.copy(alpha = 0.64f),
                fontSize = 10.sp,
                lineHeight = 11.5.sp,
                letterSpacing = 0.2.sp,
                maxLines = 1,
            )
            Text(
                text = title,
                color = GuideEpg.text,
                fontSize = 21.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = meta,
                color = GuideEpg.text.copy(alpha = 0.78f),
                fontSize = 10.5.sp,
                lineHeight = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = overview,
                color = GuideEpg.text.copy(alpha = 0.82f),
                fontSize = 11.sp,
                lineHeight = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.width(170.dp).padding(top = 3.dp),
        ) {
            Surface(
                onClick = onOpenFilters,
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = Color(0xFF242424),
                        contentColor = GuideEpg.text,
                        focusedContainerColor = GuideEpg.focus,
                        focusedContentColor = GuideEpg.focusInk,
                    ),
                scale = ClickableSurfaceDefaults.scale(1f, 1f, 0.96f),
                modifier =
                    Modifier
                        .size(40.dp)
                        .then(
                            if (filterFocusRequester != null) {
                                Modifier.focusRequester(filterFocusRequester)
                            } else {
                                Modifier
                            },
                        ),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.live_tv_guide_key),
                        color = androidx.tv.material3.LocalContentColor.current,
                        fontSize = 7.5.sp,
                        lineHeight = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp,
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text =
                        buildAnnotatedString {
                            append(stringResource(R.string.live_tv_guide_press_prefix))
                            append(' ')
                            withStyle(SpanStyle(color = GuideEpg.accent)) {
                                append(stringResource(R.string.live_tv_guide_key_quoted))
                            }
                        },
                    color = GuideEpg.text,
                    fontSize = 10.5.sp,
                    lineHeight = 12.sp,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.live_tv_guide_press_suffix),
                    color = GuideEpg.text,
                    fontSize = 10.5.sp,
                    lineHeight = 12.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = stringResource(R.string.live_tv_active_filters),
                    color = GuideEpg.text.copy(alpha = 0.78f),
                    fontSize = 9.5.sp,
                    lineHeight = 11.sp,
                    maxLines = 1,
                )
                Text(
                    text = activeFilters,
                    color = GuideEpg.accent,
                    fontSize = 10.sp,
                    lineHeight = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        ProgrammePreview(
            imageUrl = program?.imageUrl ?: channel?.imageUrl,
            label = title,
            modifier = Modifier.width(168.dp).height(94.dp),
        )
    }
}

@Composable
private fun ProgrammePreview(
    imageUrl: String?,
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(2.dp))
                .background(GuideEpg.preview),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl.isNullOrBlank()) {
            Text(
                text = label,
                color = GuideEpg.text.copy(alpha = 0.55f),
                fontSize = 10.sp,
                lineHeight = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
