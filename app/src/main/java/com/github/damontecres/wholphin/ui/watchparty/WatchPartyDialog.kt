package com.github.damontecres.wholphin.ui.watchparty

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.WatchParty
import com.github.damontecres.wholphin.data.model.WatchPartyJoin
import com.github.damontecres.wholphin.data.model.WatchPartyResult
import com.github.damontecres.wholphin.data.model.WatchPartyRoom
import com.github.damontecres.wholphin.data.model.WatchPartyRoomsState
import com.github.damontecres.wholphin.data.model.WatchPartyState
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.Button
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.tryRequestFocus
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh

/**
 * The one place a watch party is opened, joined or left.
 *
 * Deliberately a dialog rather than a page: a party is a question about what is already on screen
 * ("watch this with someone"), so it hangs off the title or the player instead of taking over.
 * [title] names the default room when this client hosts; [onStartParty] tells the caller to start
 * its title now that the room exists, and [onJoined] hands a guest the host's title.
 */
@Composable
fun WatchPartyDialog(
    title: String?,
    onDismissRequest: () -> Unit,
    onStartParty: () -> Unit,
    onJoined: (WatchPartyJoin) -> Unit,
    viewModel: WatchPartyViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val party by viewModel.party.collectAsState()
    val rooms by viewModel.rooms.collectAsState()
    val outcome by viewModel.outcome.collectAsState()
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        viewModel.refresh()
        firstFocus.tryRequestFocus()
    }

    LaunchedEffect(outcome) {
        when (val result = outcome) {
            WatchPartyOutcome.Started -> {
                viewModel.consumeOutcome()
                onStartParty()
            }

            is WatchPartyOutcome.Joined -> {
                val target = result.target
                viewModel.consumeOutcome()
                if (target != null) {
                    onJoined(target)
                } else {
                    showToast(
                        context,
                        context.getString(
                            R.string.watch_party_joined_waiting,
                            party?.name ?: "",
                        ),
                    )
                }
            }

            null -> Unit
        }
    }

    BasicDialog(onDismissRequest = onDismissRequest) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier =
                Modifier
                    .width(620.dp)
                    .padding(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.watch_together),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.watch_party_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                )
            }

            val currentParty = party
            if (currentParty != null) {
                PartyPanel(
                    party = currentParty,
                    busy = viewModel.busy,
                    firstFocus = firstFocus,
                    onLeave = viewModel::leave,
                )
            } else {
                Button(
                    onClick = { viewModel.start(title) },
                    enabled = !viewModel.busy,
                    glassProminent = true,
                    contentHeight = 44.dp,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(firstFocus),
                ) {
                    Text(stringResource(R.string.watch_party_start))
                }

                viewModel.error?.let { error ->
                    Text(
                        text =
                            stringResource(
                                when (error) {
                                    WatchPartyResult.NotAllowed -> R.string.watch_party_not_allowed
                                    else -> R.string.watch_party_unavailable
                                },
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.watch_party_join),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Button(
                        onClick = viewModel::refresh,
                        contentPadding = PaddingValues(8.dp),
                        contentHeight = 32.dp,
                        glassFilled = true,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.watch_party_refresh),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                when (val state = rooms) {
                    WatchPartyRoomsState.Loading, WatchPartyRoomsState.Failed -> {
                        Text(
                            text =
                                if (state is WatchPartyRoomsState.Failed) {
                                    stringResource(R.string.watch_party_unavailable)
                                } else {
                                    stringResource(R.string.loading)
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalContentColor.current.copy(alpha = 0.7f),
                        )
                    }

                    is WatchPartyRoomsState.Available -> {
                        if (state.rooms.isEmpty()) {
                            Text(
                                text = stringResource(R.string.watch_party_none),
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalContentColor.current.copy(alpha = 0.7f),
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.heightIn(max = 260.dp),
                            ) {
                                items(state.rooms, key = { it.groupId }) { room ->
                                    RoomRow(
                                        room = room,
                                        enabled = !viewModel.busy,
                                        onClick = { viewModel.join(room.groupId) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = onDismissRequest,
                    glassFilled = true,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    contentHeight = 40.dp,
                ) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}

@Composable
private fun PartyPanel(
    party: WatchParty,
    busy: Boolean,
    firstFocus: FocusRequester,
    onLeave: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = party.name,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text =
                listOf(
                    stringResource(R.string.watch_party_members, party.members),
                    stringResource(
                        when (party.state) {
                            WatchPartyState.PLAYING -> R.string.watch_party_playing
                            WatchPartyState.WAITING -> R.string.watch_party_waiting
                            else -> R.string.watch_party_paused
                        },
                    ),
                ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalContentColor.current.copy(alpha = 0.7f),
        )
        Button(
            onClick = onLeave,
            enabled = !busy,
            glassFilled = true,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
            contentHeight = 40.dp,
            modifier = Modifier.focusRequester(firstFocus),
        ) {
            Text(stringResource(R.string.watch_party_leave))
        }
    }
}

@Composable
private fun RoomRow(
    room: WatchPartyRoom,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        glassFilled = true,
        contentHeight = 52.dp,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = room.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    listOf(
                        stringResource(R.string.watch_party_members, room.members),
                        stringResource(
                            when (room.state) {
                                WatchPartyState.PLAYING -> R.string.watch_party_playing
                                WatchPartyState.WAITING -> R.string.watch_party_waiting
                                else -> R.string.watch_party_paused
                            },
                        ),
                    ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}
