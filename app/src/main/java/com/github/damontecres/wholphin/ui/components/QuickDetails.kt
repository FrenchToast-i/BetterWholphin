package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.QuickDetailsData
import com.github.damontecres.wholphin.preferences.DisplayToggle
import com.github.damontecres.wholphin.ui.dot
import com.github.damontecres.wholphin.ui.formatTime
import com.github.damontecres.wholphin.ui.util.LocalClock
import com.github.damontecres.wholphin.ui.util.LocalInterfaceCustomization
import org.jellyfin.sdk.model.DateTime
import kotlin.time.Duration

@Composable
fun QuickDetails(
    details: QuickDetailsData?,
    timeRemaining: Duration?,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall,
    endsAt: DateTime? = null,
    /**
     * Whether the line may claim when the title finishes.
     *
     * Tonight passes false: its card carries a deadline rule that computes the finish time from
     * its own window, and a line derived a second later printed a second "Ends at" a minute apart
     * from the rule right below it.
     */
    showEndsAt: Boolean = true,
    /**
     * Draw the whole line as one ellipsized text.
     *
     * The default is a Row of separate texts, which is right when the header is as wide as the
     * page. In a fixed-width pane (the glass hero) four unweighted texts each measure to their own
     * width and the last one is simply clipped by the pane's edge: "Ends at 11:20" loses its
     * minutes because the string inside the same sentence was too long.
     */
    oneLine: Boolean = false,
) {
    val enabled = LocalInterfaceCustomization.current.enabledDisplayToggles
    val inlineContentMap = rememberQuickDetailsContentMap(textStyle)
    if (oneLine) {
        Text(
            text = rememberQuickDetailsLine(details, timeRemaining, endsAt, enabled, showEndsAt),
            color = MaterialTheme.colorScheme.onSurface,
            style = textStyle,
            inlineContent = inlineContentMap,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
        return
    }
    Row(modifier = modifier) {
        if (details != null) {
            QuickDetailsText(details.basic, Modifier, textStyle, inlineContentMap)
            if (DisplayToggle.OFFICIAL_RATING in enabled) {
                QuickDetailsText(details.officialRating, Modifier, textStyle, inlineContentMap)
            }
            if (DisplayToggle.COMMUNITY_RATING in enabled) {
                QuickDetailsText(details.communityRating, Modifier, textStyle, inlineContentMap)
            }
            if (DisplayToggle.CRITIC_RATING in enabled) {
                QuickDetailsText(details.criticRating, Modifier, textStyle, inlineContentMap)
            }
        }
        if (timeRemaining != null) {
            TimeRemaining(timeRemaining, textStyle = textStyle)
        } else if (endsAt != null && showEndsAt) {
            EndsAt(endsAt, textStyle = textStyle)
        }
    }
}

/**
 * Every detail as one string, in the order the Row would draw them.
 *
 * Concatenating the parts (rather than re-deriving them) keeps the rating icons: each part is an
 * [AnnotatedString] carrying its own inline content, and `plus` carries the placeholders and
 * their ranges across.
 */
@Composable
private fun rememberQuickDetailsLine(
    details: QuickDetailsData?,
    timeRemaining: Duration?,
    endsAt: DateTime?,
    enabled: Collection<DisplayToggle>,
    showEndsAt: Boolean = true,
): AnnotatedString {
    val resources = LocalResources.current
    val context = LocalContext.current
    val now by LocalClock.current.now
    val end =
        when {
            !showEndsAt -> null
            timeRemaining != null -> now.plusSeconds(timeRemaining.inWholeSeconds)
            else -> endsAt
        }
    return remember(details, end, enabled, resources, context) {
        var line = details?.basic ?: AnnotatedString("")
        if (details != null) {
            if (DisplayToggle.OFFICIAL_RATING in enabled) details.officialRating?.let { line += it }
            if (DisplayToggle.COMMUNITY_RATING in enabled) details.communityRating?.let { line += it }
            if (DisplayToggle.CRITIC_RATING in enabled) details.criticRating?.let { line += it }
        }
        if (end != null) {
            line +=
                buildAnnotatedString {
                    dot()
                    append(resources.getString(R.string.ends_at, formatTime(context, end)))
                }
        }
        line
    }
}

@NonRestartableComposable
@Composable
fun QuickDetailsText(
    str: AnnotatedString?,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall,
    inlineContentMap: Map<String, InlineTextContent> = rememberQuickDetailsContentMap(textStyle),
) = str?.let {
    Text(
        text = str,
        color = MaterialTheme.colorScheme.onSurface,
        style = textStyle,
        inlineContent = inlineContentMap,
        maxLines = 1,
        modifier = modifier,
    )
}

@Composable
fun rememberQuickDetailsContentMap(textStyle: TextStyle = MaterialTheme.typography.titleSmall) =
    remember(textStyle) {
        mapOf(
            "star" to
                InlineTextContent(
                    Placeholder(
                        textStyle.fontSize,
                        textStyle.fontSize,
                        PlaceholderVerticalAlign.TextCenter,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        tint = FilledStarColor,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
            "rotten" to
                InlineTextContent(
                    Placeholder(
                        textStyle.fontSize,
                        textStyle.fontSize,
                        PlaceholderVerticalAlign.TextCenter,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_rotten_tomatoes_rotten),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        tint = Color.Unspecified,
                    )
                },
            "fresh" to
                InlineTextContent(
                    Placeholder(
                        textStyle.fontSize,
                        textStyle.fontSize,
                        PlaceholderVerticalAlign.TextCenter,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_rotten_tomatoes_fresh),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        tint = Color.Unspecified,
                    )
                },
        )
    }

@Composable
fun TimeRemaining(
    remaining: Duration,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall,
) {
    val now by LocalClock.current.now
    EndsAt(
        endsAt = now.plusSeconds(remaining.inWholeSeconds),
        modifier = modifier,
        textStyle = textStyle,
    )
}

@Composable
fun EndsAt(
    endsAt: DateTime,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall,
) {
    val resources = LocalResources.current
    val context = LocalContext.current
    val remainingStr =
        remember(endsAt, resources, context) {
            val endTimeStr = formatTime(context, endsAt)
            buildAnnotatedString {
                dot()
                append(resources.getString(R.string.ends_at, endTimeStr))
            }
        }
    Text(
        text = remainingStr,
        style = textStyle,
        maxLines = 1,
        modifier = modifier,
    )
}
